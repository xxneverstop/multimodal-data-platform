"""
MOTION_PHYSICS_METRICS_V2 Pipeline
基于 SMPL BodyModel 前向计算的完整物理质量指标

指标说明：
  - jitter:            关节三阶导数（抖动），反映动作平滑度
  - avg_penetrate_mm:  平均穿透深度（mm），足部陷入地面的程度
  - avg_float_mm:      平均浮空高度（mm），足部离地悬空的程度
  - avg_skate_mm:       平均滑动距离（mm），足部在地面的滑移量
  - skate_ratio:        滑动帧占比（%）
  - joint_pop_ratio:    关节跳变率（%），整体关节角度突变比例
  - arms_pop_ratio / legs_pop_ratio / wrists_pop_ratio / ankles_pop_ratio: 分部位跳变率
  - wrist_twist_ratio:  手腕扭曲率（%）
  - phys_err_mm:        综合物理误差 = penetrate + float + skate（mm）

输入: SMPL_NPZ (poses, betas, trans, gender, mocap_framerate)
输出: PHYSICS_REPORT_V2 (每文件一个 JSON 报告)

依赖: torch, numpy, SMPL-H BodyModel 文件
"""
import os
import json
import numpy as np
from typing import List, Dict, Tuple, Optional

from .base import BasePipeline

# ── torch 可选导入 ──
try:
    import torch
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False

# ── 物理指标参数默认值 ──
DEFAULT_PHYS_PARAMS = {
    "up_axis": 1,
    "floor_first_n_frames": 5,
    "left_foot_joint_ids": [7, 10],
    "right_foot_joint_ids": [8, 11],
    "k_nearest": 200,
    "frame_for_knn": 0,
    "sole_keep_percentile": 40.0,
    "contact_thresh": 0.03,
    "below_tol": 0.01,
    "vertical_vel_thresh": 0.01,
    "ang_pop_thresh": 20.0,
    "wrist_twist_threshold": 120.0,
    "chunk_size": 256,
}


# ==============================================================================
# 旋转数学工具函数（torch 版本，移植自 motion_vis phys_metrics.py）
# ==============================================================================

def _normalize_tensor(x: "torch.Tensor", dim: int = -1, return_norm: bool = False):
    """向量归一化"""
    norm = x.norm(dim=dim, keepdim=True).clip(min=1e-8)
    normalized_x = x / norm
    return normalized_x if not return_norm else (normalized_x, norm)


def _vector_cross_matrix(x: "torch.Tensor") -> "torch.Tensor":
    """反对称矩阵 [v]×"""
    x = x.view(-1, 3)
    zeros = torch.zeros(x.shape[0], device=x.device)
    return torch.stack(
        (zeros, -x[:, 2], x[:, 1], x[:, 2], zeros, -x[:, 0], -x[:, 1], x[:, 0], zeros),
        dim=1,
    ).view(-1, 3, 3)


def _axis_angle_to_rotation_matrix(a: "torch.Tensor") -> "torch.Tensor":
    """轴角 → 旋转矩阵（batch）"""
    axis, angle = _normalize_tensor(a.reshape(-1, 3), return_norm=True)
    axis[torch.isnan(axis)] = 0
    i_cube = torch.eye(3, device=a.device).expand(angle.shape[0], 3, 3)
    c = angle.cos().view(-1, 1, 1)
    s = angle.sin().view(-1, 1, 1)
    r = c * i_cube + (1 - c) * torch.bmm(axis.view(-1, 3, 1), axis.view(-1, 1, 3)) + s * _vector_cross_matrix(axis)
    return r


def _rotation_matrix_2_angle(rot_mat: "torch.Tensor") -> "torch.Tensor":
    """旋转矩阵 → 旋转角度"""
    batch_size = rot_mat.size(0)
    rot_mat = rot_mat.view(batch_size, 3, 3)
    cos_theta = 0.5 * (rot_mat[:, 0, 0] + rot_mat[:, 1, 1] + rot_mat[:, 2, 2] - 1)
    theta = torch.acos(torch.clamp(cos_theta, -1.0, 1.0))
    return theta.unsqueeze(1)


def _angle_between(rot1: "torch.Tensor", rot2: "torch.Tensor") -> "torch.Tensor":
    """两个旋转矩阵之间的角度差"""
    rot1 = rot1.reshape(-1, 3, 3)
    rot2 = rot2.reshape(-1, 3, 3)
    offsets = rot1.transpose(1, 2).bmm(rot2)
    return _rotation_matrix_2_angle(offsets)


# ==============================================================================
# Pipeline 类
# ==============================================================================

class MotionPhysicsMetricsV2Pipeline(BasePipeline):
    """SMPL 动作 → 完整物理指标报告（torch + BodyModel）"""

    pipeline_id = "MOTION_PHYSICS_METRICS_V2"
    display_name = "动作物理指标计算V2"
    description = "基于SMPL BodyModel前向计算的完整物理质量指标：穿透/浮空/滑动/关节跳变/手腕扭曲/抖动，需要torch和SMPL-H模型文件"
    version = "2.0.0"
    input_asset_types = ["SMPL_NPZ"]
    output_asset_types = ["PHYSICS_REPORT_V2"]
    runtime_dependencies = ["torch", "numpy"]

    # ── 实例级 BodyModel 缓存 ──

    def __init__(self):
        super().__init__()
        self._bm_cache: Dict[str, "torch.nn.Module"] = {}

    # ── execute ──

    def execute(
        self,
        input_dir: str,
        output_dir: str,
        input_files: List[Dict],
    ) -> List[Dict]:
        if not HAS_TORCH:
            raise RuntimeError(
                "MOTION_PHYSICS_METRICS_V2 需要 torch，"
                "请在 Worker 环境中安装: pip install torch"
            )

        outputs = []

        for f in input_files:
            asset_type = f.get("assetType", "")
            if asset_type != "SMPL_NPZ":
                continue

            source_key = f.get("sourceKey", "")
            filename = f.get("originalFilename", "")
            local_path = os.path.join(input_dir, source_key, filename)

            if not os.path.exists(local_path):
                print(f"[MOTION_PHYSICS_METRICS_V2] [WARN] 文件不存在: {local_path}")
                continue

            print(f"[MOTION_PHYSICS_METRICS_V2] 处理: {filename}")
            report = self._compute_metrics_v2(local_path, filename)

            # 写入报告
            safe_name = os.path.splitext(filename)[0]
            report_name = f"{safe_name}_phys_v2.json"
            report_path = os.path.join(output_dir, report_name)

            with open(report_path, "w", encoding="utf-8") as fp:
                json.dump(report, fp, ensure_ascii=False, indent=2)

            file_size = os.path.getsize(report_path)
            print(f"[MOTION_PHYSICS_METRICS_V2] 生成: {report_name} ({file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": report_name,
                "localPath": report_path,
                "assetType": "PHYSICS_REPORT_V2",
                "contentType": "application/json",
            })

        if not outputs:
            raise RuntimeError("未找到 SMPL_NPZ 类型的输入文件")

        return outputs

    # ── 核心计算入口 ──

    def _compute_metrics_v2(self, npz_path: str, filename: str) -> dict:
        """计算单个 NPZ 文件的完整物理指标"""
        # 1. 加载动画数据
        motion = self._load_motion_data(npz_path)
        poses = motion["poses"]       # (T, J, 3)
        betas = motion["betas"]       # (num_betas,)
        trans = motion["trans"]       # (T, 3)
        smpl_type = motion["smpl_type"]
        gender = motion["gender"]
        fps = motion["fps"]

        T = poses.shape[0]
        num_betas = len(betas)

        # 2. 获取 SMPL 模型路径
        smpl_model_path = self._resolve_smpl_model_path(gender)
        if not os.path.exists(smpl_model_path):
            return {
                "file": filename,
                "error": f"SMPL 模型文件不存在: {smpl_model_path}。"
                         f"请设置 MMDP_SMPL_MODEL_DIR 环境变量指向包含 neutral/male/female 子目录的模型目录",
            }

        # 3. BodyModel 前向计算 → verts, joints
        try:
            joint_rot, verts, joints, fps_eff = self._compute_verts_joints(
                poses, betas, trans, smpl_type, gender, num_betas, fps,
            )
        except Exception as e:
            return {"file": filename, "error": f"BodyModel forward 失败: {e}"}

        # 4. 计算物理指标
        metrics = self._compute_metrics_from_verts_joints(
            joint_rot=joint_rot,
            verts=verts,
            joints=joints,
            fps=fps_eff,
            params=DEFAULT_PHYS_PARAMS,
        )

        # 5. 附加元数据
        metrics["file"] = filename
        metrics["gender"] = gender
        metrics["smpl_type"] = smpl_type
        metrics["frames"] = T
        metrics["fps"] = round(fps_eff, 2)
        metrics["duration_s"] = round(T / fps_eff, 2) if fps_eff > 0 else 0

        return metrics

    # ── SMPL 模型路径解析 ──

    def _resolve_smpl_model_path(self, gender: str) -> str:
        """根据 gender 构造 SMPL-H 模型 .npz 路径"""
        from config import MMDP_SMPL_MODEL_DIR

        if gender not in ("male", "female", "neutral"):
            gender = "neutral"

        return os.path.join(MMDP_SMPL_MODEL_DIR, gender, "model.npz")

    # ── 数据加载（移植自 phys_metrics.py load_motion_data） ──

    @staticmethod
    def _load_motion_data(file_path: str) -> Dict:
        """
        加载 NPZ 动画文件，提取 poses, betas, trans, fps, gender, smpl_type。
        返回 poses 统一为 (T, J, 3) 格式。
        """
        file_path = str(file_path)
        raw = dict(np.load(file_path, allow_pickle=True))

        # gender
        gender = raw.get("gender", "neutral")
        if isinstance(gender, np.ndarray):
            gender = str(gender.item())
        else:
            gender = str(gender)
        if gender not in ["male", "female", "neutral"]:
            gender = "neutral"

        # fps
        fps = float(raw.get("mocap_framerate", raw.get("mocap_frame_rate", 30)))

        # betas
        betas_raw = raw.get("betas", np.zeros(16))
        betas_raw = np.array(betas_raw, dtype=np.float32)
        num_betas = min(16, int(betas_raw.shape[0]))
        betas = betas_raw.flatten()[:num_betas]
        betas = _handle_nan_inf(betas, "betas")

        # poses: 规整化为 (T, J, 3)
        poses = raw.get("poses")
        if poses is None:
            raise ValueError(f"文件 {file_path} 缺少 poses 字段")
        poses = np.array(poses, dtype=np.float32)
        poses = _handle_nan_inf(poses, "poses")

        if poses.ndim == 3:
            pass  # 已是 (T, J, 3)
        elif poses.ndim == 2:
            T_, D = poses.shape
            if D == 165:
                poses = poses.reshape(T_, 55, 3)
            elif D == 156:
                poses = poses.reshape(T_, 52, 3)
            elif D in [66, 72]:
                poses = poses.reshape(T_, D // 3, 3)
            else:
                raise ValueError(f"poses 维度异常: {poses.shape}")
        else:
            raise ValueError(f"poses 形状异常: {poses.shape}")

        T_, J_, _ = poses.shape
        if J_ == 55:
            smpl_type = "smplx"
        elif J_ == 52:
            smpl_type = "smplh"
        elif J_ <= 24:
            smpl_type = "smpl"
        else:
            smpl_type = "smplh"

        # trans
        trans = raw.get("trans", np.zeros((T_, 3), dtype=np.float32))
        trans = np.array(trans, dtype=np.float32)
        trans = _handle_nan_inf(trans, "trans")
        if trans.ndim == 1:
            trans = np.tile(trans, (T_, 1))
        elif trans.shape[0] != T_:
            print(f"[MOTION_PHYSICS_METRICS_V2] [WARN] trans 长度 {trans.shape[0]} 与 poses {T_} 不一致，重置为零")
            trans = np.zeros((T_, 3), dtype=np.float32)

        return {
            "poses": poses,
            "betas": betas,
            "trans": trans,
            "fps": fps,
            "gender": gender,
            "smpl_type": smpl_type,
        }

    # ── poses → SMPLH 组件拆分（移植自 phys_metrics.py） ──

    @staticmethod
    def _poses_to_smplh_components(poses: np.ndarray, smpl_type: str) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        将 (T, J, 3) 的 poses 拆分为 root_orient, pose_body, pose_hand，
        以适配 SMPLH BodyModel（始终填充 52 关节）。
        """
        T_ = poses.shape[0]
        root_orient = poses[:, 0, :]  # (T, 3)

        if smpl_type == "smplh":
            pose_body = poses[:, 1:22, :].reshape(T_, 63)
            pose_hand = poses[:, 22:52, :].reshape(T_, 90)
        elif smpl_type == "smplx":
            pose_body = poses[:, 1:22, :].reshape(T_, 63)
            pose_hand = poses[:, 25:55, :].reshape(T_, 90)
        else:
            J_ = poses.shape[1]
            pose_body = poses[:, 1:min(22, J_), :].reshape(T_, -1)
            if pose_body.shape[1] < 63:
                pose_body = np.concatenate(
                    [pose_body, np.zeros((T_, 63 - pose_body.shape[1]), dtype=np.float32)], axis=1
                )
            pose_hand = np.zeros((T_, 90), dtype=np.float32)

        return root_orient, pose_body, pose_hand

    # ── BodyModel 前向计算（移植自 phys_metrics.py compute_verts_joints） ──

    def _compute_verts_joints(
        self,
        poses: np.ndarray,
        betas: np.ndarray,
        trans: np.ndarray,
        smpl_type: str,
        gender: str,
        num_betas: int,
        fps: float,
    ) -> Tuple[np.ndarray, np.ndarray, np.ndarray, float]:
        """
        通过 BodyModel.forward() 计算顶点和关节位置。
        返回: (joint_rot, verts, joints, fps_eff)
        """
        from .body_model.body_model import BodyModel

        T_ = poses.shape[0]
        device = self._get_device()
        chunk_size = DEFAULT_PHYS_PARAMS["chunk_size"]

        root_orient, pose_body, pose_hand = self._poses_to_smplh_components(poses, smpl_type)

        # joint_rot 用于后续关节跳变/手腕扭曲计算
        if poses.shape[1] >= 22:
            joint_rot = poses[:, 1:22, :].copy()
        else:
            joint_rot = np.zeros((T_, 21, 3), dtype=np.float32)

        # 获取或缓存 BodyModel
        smpl_model_path = self._resolve_smpl_model_path(gender)
        cache_key = f"{smpl_model_path}_{num_betas}_{device}"
        if cache_key not in self._bm_cache:
            print(f"[MOTION_PHYSICS_METRICS_V2] 加载 BodyModel: {smpl_model_path}")
            bm = BodyModel(bm_fname=smpl_model_path, num_betas=num_betas).to(device)
            bm.eval()
            self._bm_cache[cache_key] = bm
        bm = self._bm_cache[cache_key]

        root_t = torch.from_numpy(root_orient).float().to(device)
        body_t = torch.from_numpy(pose_body).float().to(device)
        hand_t = torch.from_numpy(pose_hand).float().to(device)
        betas_t = torch.from_numpy(betas).float().to(device).unsqueeze(0).repeat(T_, 1)
        trans_t = torch.from_numpy(trans).float().to(device)

        verts_list = []
        joints_list = []

        with torch.no_grad():
            for start in range(0, T_, chunk_size):
                end = min(start + chunk_size, T_)
                cs = end - start
                chunk_betas = betas_t[0:1].repeat(cs, 1)
                body = bm(
                    root_orient=root_t[start:end],
                    pose_body=body_t[start:end],
                    pose_hand=hand_t[start:end],
                    betas=chunk_betas,
                    trans=trans_t[start:end],
                )
                verts_list.append(body.v.cpu().numpy().astype(np.float32))
                joints_list.append(body.Jtr.cpu().numpy().astype(np.float32))

        verts = np.concatenate(verts_list, axis=0)
        joints = np.concatenate(joints_list, axis=0)

        # 下采样到约 30fps
        down = max(1, int(fps // 30))
        if down > 1:
            joint_rot = joint_rot[::down]
            verts = verts[::down]
            joints = joints[::down]
        fps_out = fps / down

        return joint_rot, verts, joints, fps_out

    def _get_device(self) -> str:
        """获取 torch 计算设备"""
        from config import MMDP_PHYSICS_DEVICE
        device = MMDP_PHYSICS_DEVICE
        if device == "cuda" and not torch.cuda.is_available():
            print("[MOTION_PHYSICS_METRICS_V2] [WARN] CUDA 不可用，回退到 CPU")
            device = "cpu"
        return device

    # ── 物理指标计算（移植自 phys_metrics.py Part C） ──

    @staticmethod
    def _compute_metrics_from_verts_joints(
        joint_rot: np.ndarray,
        verts: np.ndarray,
        joints: np.ndarray,
        fps: float,
        params: Dict,
    ) -> Dict[str, float]:
        """从 verts/joints 计算所有物理指标"""
        up_axis = params["up_axis"]
        floor_first_n_frames = params["floor_first_n_frames"]
        left_foot_joint_ids = params["left_foot_joint_ids"]
        right_foot_joint_ids = params["right_foot_joint_ids"]
        k_nearest = params["k_nearest"]
        frame_for_knn = params["frame_for_knn"]
        sole_keep_percentile = params["sole_keep_percentile"]
        contact_thresh = params["contact_thresh"]
        below_tol = params["below_tol"]
        vertical_vel_thresh = params["vertical_vel_thresh"]
        ang_pop_thresh = params["ang_pop_thresh"]
        wrist_twist_threshold = params["wrist_twist_threshold"]

        T_ = verts.shape[0]

        # --- 地面高度 + 脚底顶点 ---
        verts_ref = verts[frame_for_knn]
        joints_ref = joints[frame_for_knn]

        left_foot_vids = _knn_vids_from_joints(verts_ref, joints_ref, left_foot_joint_ids, k_nearest)
        right_foot_vids = _knn_vids_from_joints(verts_ref, joints_ref, right_foot_joint_ids, k_nearest)
        left_sole_vids = _filter_sole_vids_by_height(verts_ref, left_foot_vids, up_axis, sole_keep_percentile)
        right_sole_vids = _filter_sole_vids_by_height(verts_ref, right_foot_vids, up_axis, sole_keep_percentile)

        floor_height = _compute_floor_height(verts, up_axis, min(floor_first_n_frames, T_))

        # --- 左右脚物理指标 ---
        L_pen, L_flo, L_ska, _, _, L_skate_r = _stance_and_metrics_one_foot(
            verts, left_sole_vids, up_axis, floor_height, contact_thresh, below_tol, vertical_vel_thresh
        )
        R_pen, R_flo, R_ska, _, _, R_skate_r = _stance_and_metrics_one_foot(
            verts, right_sole_vids, up_axis, floor_height, contact_thresh, below_tol, vertical_vel_thresh
        )

        pen_avg = 0.5 * (L_pen + R_pen)
        flo_avg = 0.5 * (L_flo + R_flo)
        ska_avg = 0.5 * (L_ska + R_ska)
        skate_ratio_avg = 0.5 * (L_skate_r + R_skate_r)
        phys_err = pen_avg + flo_avg + ska_avg

        # --- Jitter（需要 torch） ---
        joints_t = torch.from_numpy(joints).float()
        body_joints = joints_t[:, :22, :]
        if T_ >= 4:
            jitter = float(
                ((body_joints[3:] - 3 * body_joints[2:-1] + 3 * body_joints[1:-2] - body_joints[:-3]) * fps)
                .norm(dim=2).mean().item()
            )
        else:
            jitter = 0.0

        # --- Joint Pop Ratio ---
        joint_rot_t = torch.from_numpy(joint_rot).float()
        T_rot, J_rot = joint_rot_t.shape[0], joint_rot_t.shape[1]

        arms_joint_ids = [15, 16, 17, 18]
        legs_joint_ids = [0, 1, 3, 4]
        wrists_joint_ids = [19, 20]
        ankles_joint_ids = [6, 7]

        if T_rot >= 2:
            rot_mat = _axis_angle_to_rotation_matrix(joint_rot_t.reshape(-1, 3)).reshape(T_rot, J_rot, 3, 3)
            ang_diff_deg = _angle_between(
                rot_mat[1:].reshape(-1, 3, 3),
                rot_mat[:-1].reshape(-1, 3, 3)
            ).reshape(T_rot - 1, J_rot, 1) * 180.0 / np.pi

            joint_pop_flag = ang_diff_deg > ang_pop_thresh
            total_frames = T_rot - 1

            def _pop_rate(ids):
                if not ids:
                    return 0.0
                return float(joint_pop_flag[:, ids].sum() / (total_frames * len(ids)) * 100)

            overall_pop_ratio = float(joint_pop_flag.sum() / (total_frames * J_rot) * 100)
            arms_pop_ratio = _pop_rate(arms_joint_ids)
            legs_pop_ratio = _pop_rate(legs_joint_ids)
            wrists_pop_ratio = _pop_rate(wrists_joint_ids)
            ankles_pop_ratio = _pop_rate(ankles_joint_ids)
        else:
            overall_pop_ratio = arms_pop_ratio = legs_pop_ratio = 0.0
            wrists_pop_ratio = ankles_pop_ratio = 0.0

        # --- Wrist Twist Ratio ---
        if T_rot >= 1 and len(wrists_joint_ids) > 0:
            wrist_angles = torch.norm(joint_rot_t[:, wrists_joint_ids], dim=2) * 180.0 / np.pi
            wrist_twist_flag = wrist_angles > wrist_twist_threshold
            wrist_twist_ratio = float(wrist_twist_flag.any(dim=1).float().mean() * 100)
        else:
            wrist_twist_ratio = 0.0

        return {
            "jitter": round(jitter, 6),
            "avg_penetrate_mm": round(pen_avg, 2),
            "avg_float_mm": round(flo_avg, 2),
            "avg_skate_mm": round(ska_avg, 2),
            "frame_avg_skate_mm": round(ska_avg * skate_ratio_avg / 100.0, 2),
            "skate_ratio": round(skate_ratio_avg, 2),
            "joint_pop_ratio": round(overall_pop_ratio, 4),
            "arms_pop_ratio": round(arms_pop_ratio, 4),
            "legs_pop_ratio": round(legs_pop_ratio, 4),
            "wrists_pop_ratio": round(wrists_pop_ratio, 4),
            "ankles_pop_ratio": round(ankles_pop_ratio, 4),
            "wrist_twist_ratio": round(wrist_twist_ratio, 2),
            "phys_err_mm": round(phys_err, 2),
        }


# ==============================================================================
# 辅助函数（模块级，移植自 phys_metrics.py）
# ==============================================================================

def _handle_nan_inf(arr: np.ndarray, name: str = "array") -> np.ndarray:
    """替换 NaN/Inf 为 0"""
    if np.any(~np.isfinite(arr)):
        count = np.sum(~np.isfinite(arr))
        print(f"[MOTION_PHYSICS_METRICS_V2] [WARN] {name} 包含 {count} 个 NaN/Inf，已替换为 0")
        arr = np.nan_to_num(arr, nan=0.0, posinf=0.0, neginf=0.0)
    return arr


def _knn_vids_from_joints(
    verts_t: np.ndarray,
    joints_t: np.ndarray,
    joint_ids: List[int],
    k_nearest: int = 200,
) -> np.ndarray:
    """KNN 选取脚底顶点"""
    if len(joint_ids) == 0:
        return np.array([], dtype=np.int64)
    vids = []
    for jid in joint_ids:
        c = joints_t[jid]
        d2 = np.sum((verts_t - c[None, :]) ** 2, axis=1)
        nn = np.argpartition(d2, kth=min(k_nearest, len(d2) - 1))[:k_nearest]
        vids.append(nn)
    return np.unique(np.concatenate(vids, axis=0)).astype(np.int64)


def _filter_sole_vids_by_height(
    verts_t: np.ndarray,
    foot_vids: np.ndarray,
    up_axis: int = 1,
    keep_percentile: float = 40.0,
    min_keep: int = 50,
) -> np.ndarray:
    """按高度百分位筛选脚底顶点"""
    if foot_vids.size == 0:
        return foot_vids
    h = verts_t[foot_vids, up_axis]
    thr = np.percentile(h, keep_percentile)
    sole = foot_vids[h <= thr]
    if sole.size < min_keep:
        idx = np.argsort(h)[:min(min_keep, foot_vids.size)]
        sole = foot_vids[idx]
    return np.unique(sole).astype(np.int64)


def _compute_floor_height(verts: np.ndarray, up_axis: int, first_n_frames: int) -> float:
    """基于前 N 帧所有顶点最低 10 个点的平均值估计地面高度"""
    T_, V_, _ = verts.shape
    N = int(min(max(first_n_frames, 1), T_))
    per_frame = []
    for t in range(N):
        heights = verts[t, :, up_axis]
        n_lowest = min(10, V_)
        lowest_idx = np.argpartition(heights, n_lowest)[:n_lowest]
        per_frame.append(float(np.mean(heights[lowest_idx])))
    return float(np.mean(per_frame))


def _stance_and_metrics_one_foot(
    verts: np.ndarray,
    sole_vids: np.ndarray,
    up_axis: int,
    floor_height: float,
    contact_thresh: float,
    below_tol: float,
    vertical_vel_thresh: float,
) -> Tuple[float, float, float, float, float, float]:
    """单脚物理指标计算：穿透/浮空/滑动"""
    T_ = verts.shape[0]
    if sole_vids.size == 0:
        return 0.0, 0.0, 0.0, 0.0, 0.0, 0.0

    low = floor_height - below_tol
    high = floor_height + contact_thresh
    horiz_axes = [ax for ax in range(3) if ax != up_axis]

    h_min = np.min(verts[:, sole_vids, up_axis], axis=1)

    v = np.zeros(T_, dtype=np.float32)
    v[:-1] = h_min[1:] - h_min[:-1]
    v[-1] = v[-2] if T_ > 1 else 0.0

    in_contact_window = (h_min >= low) & (h_min <= high)
    in_penetration = h_min < low
    v_ok = np.abs(v) < vertical_vel_thresh

    contact_stance = in_contact_window & v_ok
    pen_stance = in_penetration & v_ok
    any_stance = (in_contact_window | in_penetration) & v_ok

    pen_depth = np.maximum(low - h_min, 0.0)
    flo_gap = np.maximum(h_min - floor_height, 0.0)

    pen_mm = float(np.mean(pen_depth[pen_stance]) * 1000.0) if np.any(pen_stance) else 0.0
    flo_mm = float(np.mean(flo_gap[contact_stance]) * 1000.0) if np.any(contact_stance) else 0.0

    skate_d = []
    for t in range(T_ - 1):
        if not any_stance[t]:
            continue
        ht = verts[t, sole_vids, up_axis]
        mask_t = (ht >= low) & (ht <= high)
        pts_t = verts[t, sole_vids][mask_t][:, horiz_axes] if np.any(mask_t) else verts[t, sole_vids][:, horiz_axes]

        ht2 = verts[t + 1, sole_vids, up_axis]
        mask_t2 = (ht2 >= low) & (ht2 <= high)
        pts_t2 = verts[t + 1, sole_vids][mask_t2][:, horiz_axes] if np.any(mask_t2) else verts[t + 1, sole_vids][:, horiz_axes]

        c1 = np.mean(pts_t, axis=0)
        c2 = np.mean(pts_t2, axis=0)
        skate_d.append(float(np.linalg.norm(c2 - c1)))

    ska_mm = float(np.mean(skate_d) * 1000.0) if len(skate_d) > 0 else 0.0

    skate_threshold_m = 0.01
    skate_stance = np.zeros(T_ - 1, dtype=bool)
    for i, d in enumerate(skate_d):
        skate_stance[i] = d > skate_threshold_m
    skate_ratio = float(np.mean(skate_stance) * 100.0) if len(skate_stance) > 0 else 0.0

    contact_ratio = float(np.mean(contact_stance) * 100.0)
    pen_ratio = float(np.mean(pen_stance) * 100.0)

    return pen_mm, flo_mm, ska_mm, contact_ratio, pen_ratio, skate_ratio
