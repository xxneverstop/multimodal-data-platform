"""
BUILD_MOTION_VIEWER_DATA Pipeline
将 SMPL NPZ 动作文件转换为前端 Three.js 3D 查看器所需的 JSON 格式

输出格式兼容 motion_vis 的 /api/motion/<filename> 返回结构，
确保前端可以复用 motion_vis 的 SMPL 渲染管线（SkinnedMesh + 骨骼动画）。

输入: SMPL_NPZ (poses, trans, betas, gender, mocap_framerate)
输出: MOTION_VIEWER_JSON (每文件一个 viewer JSON)
"""
import os
import json
import numpy as np
from typing import List, Dict

from .base import BasePipeline


class BuildMotionViewerDataPipeline(BasePipeline):
    """SMPL NPZ → 3D 查看器 JSON 数据"""

    pipeline_id = "BUILD_MOTION_VIEWER_DATA"
    display_name = "生成3D查看数据"
    description = "将 SMPL NPZ 动作数据转换为前端 Three.js 查看器所需的帧数据 JSON，兼容 motion_vis 渲染管线"
    version = "1.0.0"
    input_asset_types = ["SMPL_NPZ"]
    output_asset_types = ["MOTION_VIEWER_JSON"]
    runtime_dependencies = ["numpy"]

    def execute(
        self,
        input_dir: str,
        output_dir: str,
        input_files: List[Dict],
    ) -> List[Dict]:
        outputs = []

        for f in input_files:
            if f.get("assetType") != "SMPL_NPZ":
                continue

            source_key = f.get("sourceKey", "")
            filename = f.get("originalFilename", "")
            local_path = os.path.join(input_dir, source_key, filename)

            if not os.path.exists(local_path):
                print(f"[BUILD_MOTION_VIEWER_DATA] [WARN] 文件不存在: {local_path}")
                continue

            print(f"[BUILD_MOTION_VIEWER_DATA] 处理: {filename}")
            viewer_json = self._build_viewer_json(local_path, filename)

            # 写入 JSON 文件
            safe_name = os.path.splitext(filename)[0]
            json_name = f"{safe_name}_viewer.json"
            json_path = os.path.join(output_dir, json_name)

            with open(json_path, "w", encoding="utf-8") as fp:
                json.dump(viewer_json, fp, ensure_ascii=False)

            file_size = os.path.getsize(json_path)
            print(f"[BUILD_MOTION_VIEWER_DATA] 生成: {json_name} "
                  f"({viewer_json['frame_count']} 帧, {file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": json_name,
                "localPath": json_path,
                "assetType": "MOTION_VIEWER_JSON",
                "contentType": "application/json",
            })

        if not outputs:
            raise RuntimeError("未找到 SMPL_NPZ 类型的输入文件")

        return outputs

    def _build_viewer_json(self, npz_path: str, filename: str) -> dict:
        """解析 NPZ 并构建 viewer JSON"""
        data = np.load(npz_path, allow_pickle=True)
        poses = data["poses"]            # (N, D) — D=72(smpl) / 156(smplh) / 165(smplx)
        trans = data["trans"]            # (N, 3)
        betas = data.get("betas", np.zeros(16))  # (16,) or (N,16)
        gender = str(data.get("gender", "neutral"))
        fps = float(data.get("mocap_framerate", 30.0))

        frames_n = poses.shape[0]
        pose_dim = poses.shape[1]

        # 检测 SMPL 类型
        smpl_type = self._detect_smpl_type(pose_dim)

        # 处理 betas: 可能是 (16,) 或 (N,16)
        if betas.ndim == 2:
            shapes = betas[0].tolist()
        else:
            shapes = betas.tolist()
        if len(shapes) < 16:
            shapes = shapes + [0.0] * (16 - len(shapes))
        shapes = shapes[:16]

        # 逐帧组装
        frames = []
        for i in range(frames_n):
            frame_poses = poses[i].tolist()
            frame_trans = trans[i].tolist()

            # Rh = 前3个 pose 值（全局根旋转，axis-angle）
            rh = frame_poses[:3]
            # Th = trans（全局根平移）
            th = frame_trans

            frame_data = {
                "id": i,
                "gender": gender,
                "smpl_type": smpl_type,
                "Rh": [rh],
                "Th": [th],
                "poses": [frame_poses],
                "shapes": shapes,
                "mocap_framerate": fps,
            }
            # 每帧包装在双层列表中（兼容 motion_vis 的数据结构）
            frames.append([frame_data])

        data.close()

        return {
            "filename": filename,
            "frames": frames,
            "frame_count": frames_n,
            "framerate": fps,
            "smpl_type": smpl_type,
            "gender": gender,
        }

    @staticmethod
    def _detect_smpl_type(pose_dim: int) -> str:
        """根据 poses 维度检测 SMPL 类型"""
        if pose_dim == 72:
            return "smpl"
        elif pose_dim == 75:
            return "smpl"       # 72 + 3 extra
        elif pose_dim == 156:
            return "smplh"
        elif pose_dim == 165:
            return "smplx"
        else:
            # 回退：按关节数估算
            n_joints = pose_dim // 3
            if n_joints <= 24:
                return "smpl"
            elif n_joints <= 52:
                return "smplh"
            else:
                return "smplx"
