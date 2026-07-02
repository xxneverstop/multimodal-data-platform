"""
STEREO_IMU_ALIGN Pipeline
读取帧时间戳 CSV，对齐左右帧和 IMU 的时间窗口，IMU 线性插值到帧时间戳。
输出对齐后的帧序列 + IMU + 对齐报告。
"""
import os
import csv
import json
import shutil
from typing import List, Dict, Optional
from datetime import datetime, timezone, timedelta

from .base import BasePipeline

CST = timezone(timedelta(hours=8))


class StereoImuAlignPipeline(BasePipeline):
    """双目+IMU 时间对齐"""

    pipeline_id = "STEREO_IMU_ALIGN"
    display_name = "双目+IMU 时间对齐"
    description = "读取帧时间戳CSV，对齐左右帧和IMU的时间窗口，IMU线性插值到帧时间戳"
    version = "1.0.0"
    input_asset_types = [
        "LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE",
        "RAW_IMU_CSV", "FRAME_TIMESTAMPS_CSV"
    ]
    output_asset_types = [
        "LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE",
        "IMU_ALIGNED_CSV", "ALIGNMENT_REPORT"
    ]
    runtime_dependencies = []

    def execute(
        self, input_dir: str, output_dir: str, input_files: List[Dict]
    ) -> List[Dict]:
        # ---- 1. 定位 frame_timestamps.csv ----
        ts_file = self._find_file(input_files, "FRAME_TIMESTAMPS_CSV")
        if not ts_file:
            raise RuntimeError("未找到 frame_timestamps.csv (FRAME_TIMESTAMPS_CSV)")

        ts_path = os.path.join(input_dir, ts_file["sourceKey"], ts_file["originalFilename"])
        if not os.path.exists(ts_path):
            raise RuntimeError(f"frame_timestamps.csv 不存在: {ts_path}")

        # ---- 2. 解析 frame_timestamps.csv ----
        frames = []  # [{index, timestamp_ns, left_frame, right_frame}]
        with open(ts_path, "r", newline="") as fh:
            reader = csv.DictReader(fh)
            for row in reader:
                frames.append({
                    "index": int(row["frame_index"]),
                    "timestamp_ns": int(row["timestamp_ns"]),
                    "left_frame": row["left_frame"],
                    "right_frame": row["right_frame"],
                })

        if not frames:
            raise RuntimeError("frame_timestamps.csv 为空")

        total_frames = len(frames)
        ts_start_ns = frames[0]["timestamp_ns"]
        ts_end_ns = frames[-1]["timestamp_ns"]
        print(f"[STEREO_IMU_ALIGN] 帧数: {total_frames}, "
              f"时间范围: {ts_start_ns / 1e9:.3f}s ~ {ts_end_ns / 1e9:.3f}s "
              f"({(ts_end_ns - ts_start_ns) / 1e9:.3f}s)")

        # ---- 3. 读取 IMU CSV ----
        imu_file = self._find_file(input_files, "RAW_IMU_CSV")
        if not imu_file:
            raise RuntimeError("未找到 IMU CSV (RAW_IMU_CSV)")

        imu_path = os.path.join(input_dir, imu_file["sourceKey"], imu_file["originalFilename"])
        if not os.path.exists(imu_path):
            raise RuntimeError(f"IMU CSV 不存在: {imu_path}")

        imu_records, imu_header = self._parse_imu_csv(imu_path)
        if not imu_records:
            raise RuntimeError("IMU CSV 无数据行")

        imu_start_ms = imu_records[0]["timestamp_ms"]
        imu_end_ms = imu_records[-1]["timestamp_ms"]
        print(f"[STEREO_IMU_ALIGN] IMU 行数: {len(imu_records)}, "
              f"范围: {imu_start_ms:.3f}ms ~ {imu_end_ms:.3f}ms")

        # ---- 4. 计算共同时间窗口 ----
        imu_start_ns = int(imu_start_ms * 1_000_000)
        imu_end_ns = int(imu_end_ms * 1_000_000)
        window_start_ns = max(ts_start_ns, imu_start_ns)
        window_end_ns = min(ts_end_ns, imu_end_ns)

        aligned_frames = [
            f for f in frames
            if window_start_ns <= f["timestamp_ns"] <= window_end_ns
        ]

        dropped_start = sum(1 for f in frames if f["timestamp_ns"] < window_start_ns)
        dropped_end = sum(1 for f in frames if f["timestamp_ns"] > window_end_ns)

        print(f"[STEREO_IMU_ALIGN] 窗口: {window_start_ns / 1e9:.3f}s ~ "
              f"{window_end_ns / 1e9:.3f}s")
        print(f"[STEREO_IMU_ALIGN] 对齐后帧数: {len(aligned_frames)} "
              f"(裁掉前{dropped_start}+后{dropped_end})")

        if len(aligned_frames) == 0:
            raise RuntimeError("对齐后无帧，时间窗口为空")

        # ---- 5. 复制对齐帧到 aligned/ 子目录 ----
        outputs = []

        for side, side_label in [("left", "left_frames"), ("right", "right_frames")]:
            aligned_dir = os.path.join(output_dir, side_label, "aligned")
            os.makedirs(aligned_dir, exist_ok=True)

            field = f"{side}_frame"
            asset_type = "LEFT_IMAGE_SEQUENCE" if side == "left" else "RIGHT_IMAGE_SEQUENCE"

            for new_idx, f in enumerate(aligned_frames):
                src_rel = f[field]  # e.g. sources/left_frames/000042.jpg
                src_filename = os.path.basename(src_rel)
                src_path = os.path.join(input_dir, side_label, src_filename)

                new_name = f"{new_idx + 1:06d}.jpg"
                dst_path = os.path.join(aligned_dir, new_name)

                if os.path.exists(src_path):
                    shutil.copy2(src_path, dst_path)
                else:
                    print(f"[STEREO_IMU_ALIGN] [WARN] 源文件不存在: {src_path}")

                outputs.append({
                    "sourceKey": side_label,
                    "fileName": f"aligned/{new_name}",
                    "localPath": dst_path,
                    "assetType": asset_type,
                    "contentType": "image/jpeg",
                })

            print(f"[STEREO_IMU_ALIGN] {side_label}: {len(aligned_frames)} 帧 -> aligned/")

        # ---- 6. IMU 线性插值到帧时间戳 ----
        aligned_imu_path = os.path.join(output_dir, "imu_aligned.csv")
        self._interpolate_imu(imu_records, imu_header, aligned_frames, aligned_imu_path)
        outputs.append({
            "sourceKey": "imu",
            "fileName": "imu_aligned.csv",
            "localPath": aligned_imu_path,
            "assetType": "IMU_ALIGNED_CSV",
            "contentType": "text/csv",
        })
        print(f"[STEREO_IMU_ALIGN] IMU 插值: {len(aligned_frames)} 行")

        # ---- 7. 生成对齐报告 ----
        report_path = os.path.join(output_dir, "alignment_report.json")
        report = {
            "generatedAt": datetime.now(CST).isoformat(),
            "pipelineId": self.pipeline_id,
            "input": {
                "totalFrames": total_frames,
                "imuRows": len(imu_records),
                "frameTimeRangeNs": [ts_start_ns, ts_end_ns],
                "imuTimeRangeNs": [imu_start_ns, imu_end_ns],
            },
            "window": {
                "startNs": window_start_ns,
                "endNs": window_end_ns,
                "durationNs": window_end_ns - window_start_ns,
            },
            "output": {
                "alignedFrames": len(aligned_frames),
                "droppedLeading": dropped_start,
                "droppedTrailing": dropped_end,
                "imuAlignedRows": len(aligned_frames),
            },
        }
        with open(report_path, "w", encoding="utf-8") as fh:
            json.dump(report, fh, indent=2, ensure_ascii=False)

        outputs.append({
            "sourceKey": "alignment",
            "fileName": "alignment_report.json",
            "localPath": report_path,
            "assetType": "ALIGNMENT_REPORT",
            "contentType": "application/json",
        })

        print(f"[STEREO_IMU_ALIGN] [DONE] {len(outputs)} 个产物")
        return outputs

    # ---- helper methods ----

    @staticmethod
    def _find_file(input_files: List[Dict], asset_type: str) -> Optional[Dict]:
        """在 input_files 中按 assetType 查找文件"""
        for f in input_files:
            if f.get("assetType") == asset_type:
                return f
        return None

    @staticmethod
    def _parse_imu_csv(path: str) -> tuple:
        """解析 IMU CSV，返回 (records, header)"""
        with open(path, "r", newline="") as fh:
            reader = csv.DictReader(fh)
            header = reader.fieldnames
            records = []
            for row in reader:
                ts = float(row.get("timestamp", row.get("timestamp_ms", 0)))
                records.append({"timestamp_ms": ts, "row": row})
        return records, header

    @staticmethod
    def _interpolate_imu(imu_records, header, aligned_frames, output_path):
        """IMU 线性插值到每个帧时间戳，写入 CSV"""
        imu_ts = [r["timestamp_ms"] for r in imu_records]
        numeric_cols = [
            col for col in header
            if col.lower() not in ("timestamp", "timestamp_ms")
        ]
        imu_data = {
            col: [float(r["row"].get(col, 0)) for r in imu_records]
            for col in numeric_cols
        }

        with open(output_path, "w", newline="") as fh:
            out_cols = ["frame_index", "timestamp_ns", "timestamp_ms"] + numeric_cols
            writer = csv.DictWriter(fh, fieldnames=out_cols)
            writer.writeheader()

            for f in aligned_frames:
                t_ns = f["timestamp_ns"]
                t_ms = t_ns / 1_000_000.0
                row_out = {
                    "frame_index": f["index"],
                    "timestamp_ns": t_ns,
                    "timestamp_ms": f"{t_ms:.3f}",
                }
                for col in numeric_cols:
                    row_out[col] = f"{_lerp(imu_ts, imu_data[col], t_ms):.6f}"
                writer.writerow(row_out)


def _lerp(xs, ys, x):
    """线性插值"""
    if x <= xs[0]:
        return ys[0]
    if x >= xs[-1]:
        return ys[-1]
    for i in range(len(xs) - 1):
        if xs[i] <= x <= xs[i + 1]:
            t = (x - xs[i]) / (xs[i + 1] - xs[i])
            return ys[i] + t * (ys[i + 1] - ys[i])
    return ys[-1]
