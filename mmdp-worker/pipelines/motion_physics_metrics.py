"""
MOTION_PHYSICS_METRICS Pipeline
对 SMPL NPZ 动作数据计算基础物理质量指标（纯 numpy，无 torch 依赖）

指标说明：
  - jitter:        帧间关节角度变化率的均值，反映动作平滑度
  - ground_penetration_m: trans Y 轴最低点低于地面的深度（米），>0 表示穿透
  - floating_ratio: trans Y 轴持续悬空超过 5cm 的帧占比
  - trans_range_xyz: 全局位移在 X/Y/Z 方向的范围（米）
  - frames:        总帧数
  - fps:           帧率
"""
import os
import json
import numpy as np
from typing import List, Dict

from .base import BasePipeline


class MotionPhysicsMetricsPipeline(BasePipeline):
    """SMPL 动作 → 物理指标报告"""

    pipeline_id = "MOTION_PHYSICS_METRICS"
    display_name = "动作物理指标计算"
    description = "对 SMPL NPZ 动作文件计算抖动(jitter)、地面穿透(penetration)、浮空(floating)、位移范围等物理质量指标，产出 JSON 报告"
    version = "1.0.0"
    input_asset_types = ["SMPL_NPZ"]
    output_asset_types = ["PHYSICS_REPORT"]
    runtime_dependencies = ["numpy"]

    # 地面穿透检测阈值（Y 轴低于此值视为穿透）
    GROUND_Y = 0.0
    # 浮空检测阈值（Y 轴高于此值视为浮空）
    FLOATING_Y = 0.05
    # 滑步检测阈值：相邻帧脚部移动小于此值视为滑步（可用于后续扩展）
    SKATING_THRESHOLD_M = 0.002

    def execute(
        self,
        input_dir: str,
        output_dir: str,
        input_files: List[Dict],
    ) -> List[Dict]:
        """
        对每个 SMPL_NPZ 输入文件计算物理指标，生成 {filename}_phys.json 报告
        """
        outputs = []

        for f in input_files:
            asset_type = f.get("assetType", "")
            if asset_type != "SMPL_NPZ":
                continue

            source_key = f.get("sourceKey", "")
            filename = f.get("originalFilename", "")
            local_path = os.path.join(input_dir, source_key, filename)

            if not os.path.exists(local_path):
                print(f"[MOTION_PHYSICS_METRICS] [WARN] 文件不存在: {local_path}")
                continue

            print(f"[MOTION_PHYSICS_METRICS] 处理: {filename}")
            report = self._compute_metrics(local_path, filename)

            # 写入报告文件
            safe_name = os.path.splitext(filename)[0]
            report_name = f"{safe_name}_phys.json"
            report_path = os.path.join(output_dir, report_name)

            with open(report_path, "w", encoding="utf-8") as fp:
                json.dump(report, fp, ensure_ascii=False, indent=2)

            file_size = os.path.getsize(report_path)
            print(f"[MOTION_PHYSICS_METRICS] 生成: {report_name} ({file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": report_name,
                "localPath": report_path,
                "assetType": "PHYSICS_REPORT",
                "contentType": "application/json",
            })

        if not outputs:
            raise RuntimeError("未找到 SMPL_NPZ 类型的输入文件")

        return outputs

    def _compute_metrics(self, npz_path: str, filename: str) -> dict:
        """计算单个 NPZ 文件的物理指标"""
        data = np.load(npz_path, allow_pickle=True)

        poses = data.get("poses")       # (N, 156) for SMPL-H (52 joints × 3)
        trans = data.get("trans")       # (N, 3)
        fps = float(data.get("mocap_framerate", 0))
        gender = str(data.get("gender", ""))

        frames = poses.shape[0] if poses is not None else 0

        report = {
            "file": filename,
            "gender": gender,
            "frames": frames,
            "fps": round(fps, 2),
            "duration_s": round(frames / fps, 2) if fps > 0 else 0,
        }

        # 1. 帧间抖动 — 相邻帧 pose 变化量的均值
        if poses is not None and frames > 1:
            pose_diff = np.abs(np.diff(poses, axis=0))
            report["jitter"] = round(float(np.mean(pose_diff)), 6)
            report["jitter_max"] = round(float(np.max(pose_diff)), 6)
        else:
            report["jitter"] = None
            report["jitter_max"] = None

        # 2. 地面穿透 — trans[:, 1] 低于 GROUND_Y 的程度
        if trans is not None:
            trans_y = trans[:, 1]
            penetration = trans_y[trans_y < self.GROUND_Y]
            report["ground_penetration_m"] = round(
                float(-penetration.min()), 4
            ) if len(penetration) > 0 else 0.0
            report["ground_penetration_frames"] = int(len(penetration))
            report["ground_penetration_ratio"] = round(
                float(len(penetration) / frames), 4
            ) if frames > 0 else 0.0

            # 3. 浮空 — trans[:, 1] 持续高于 FLOATING_Y 的帧占比
            floating_mask = trans_y > self.FLOATING_Y
            report["floating_ratio"] = round(
                float(np.mean(floating_mask)), 4
            )
            report["floating_max_m"] = round(
                float(trans_y.max()), 4
            )

            # 4. 位移范围
            report["trans_range_x_m"] = round(float(trans[:, 0].max() - trans[:, 0].min()), 4)
            report["trans_range_y_m"] = round(float(trans[:, 1].max() - trans[:, 1].min()), 4)
            report["trans_range_z_m"] = round(float(trans[:, 2].max() - trans[:, 2].min()), 4)

            # 5. 平均速度（全局位移 m/frame）
            if frames > 1:
                trans_vel = np.linalg.norm(np.diff(trans, axis=0), axis=1)
                report["avg_speed_m_per_frame"] = round(float(np.mean(trans_vel)), 6)
            else:
                report["avg_speed_m_per_frame"] = None
        else:
            report["ground_penetration_m"] = None
            report["floating_ratio"] = None

        data.close()
        return report
