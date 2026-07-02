"""
BUILD_PLAYBACK_BUNDLE Pipeline
校验 Step 1+2 的产物，生成质检报告 (QC_SUMMARY)。
检查项: 左右视频帧数一致性、IMU 时间戳覆盖完整性、丢帧检测。
依赖: ffprobe（命令行可用）
"""
import os
import json
import csv
import io
import subprocess
from typing import List, Dict, Optional
from datetime import datetime, timezone, timedelta

from .base import BasePipeline

CST = timezone(timedelta(hours=8))


class BuildPlaybackBundlePipeline(BasePipeline):
    """播放包+质检"""

    pipeline_id = "BUILD_PLAYBACK_BUNDLE"
    display_name = "播放包+质检"
    description = "校验左右视频帧数一致性、IMU覆盖完整性、丢帧检测，生成QC质检报告"
    version = "1.0.0"
    input_asset_types = ["RGB_VIDEO_MP4", "IMU_ALIGNED_CSV", "ALIGNMENT_REPORT"]
    output_asset_types = ["QC_SUMMARY"]
    runtime_dependencies = ["ffprobe"]

    def execute(
        self, input_dir: str, output_dir: str, input_files: List[Dict]
    ) -> List[Dict]:
        # ---- 1. 找到左右 MP4 文件 ----
        mp4_files = [f for f in input_files
                     if f.get("assetType") == "RGB_VIDEO_MP4"]
        if len(mp4_files) < 2:
            raise RuntimeError(f"需要至少 2 个 MP4 文件，实际: {len(mp4_files)}")

        video_info = {}
        for mp4 in mp4_files:
            source_key = mp4.get("sourceKey", "unknown")
            local_path = os.path.join(
                input_dir, source_key, mp4.get("originalFilename", "")
            )
            if not os.path.exists(local_path):
                # 尝试直接在 input_dir 下查找
                alt = os.path.join(input_dir, mp4.get("originalFilename", ""))
                if os.path.exists(alt):
                    local_path = alt
                else:
                    raise RuntimeError(f"MP4 文件不存在: {local_path}")

            info = self._probe_video(local_path)
            info["sourceKey"] = source_key
            info["sizeBytes"] = os.path.getsize(local_path)
            video_info[source_key] = info

        # ---- 2. 读取 IMU_ALIGNED_CSV ----
        imu_file = self._find_file(input_files, "IMU_ALIGNED_CSV")
        imu_rows = 0
        imu_time_range_ns = [0, 0]
        if imu_file:
            imu_path = os.path.join(
                input_dir, imu_file["sourceKey"], imu_file["originalFilename"]
            )
            if not os.path.exists(imu_path):
                imu_path = os.path.join(input_dir, imu_file["originalFilename"])
            if os.path.exists(imu_path):
                with open(imu_path, "r") as fh:
                    lines = fh.readlines()
                imu_rows = max(0, len(lines) - 1)
                if imu_rows > 0:
                    reader = csv.DictReader(io.StringIO("".join(lines)))
                    rows = list(reader)
                    imu_time_range_ns = [
                        int(float(rows[0]["timestamp_ns"])),
                        int(float(rows[-1]["timestamp_ns"])),
                    ]

        # ---- 3. 读取 ALIGNMENT_REPORT ----
        report_file = self._find_file(input_files, "ALIGNMENT_REPORT")
        report = {}
        if report_file:
            report_path = os.path.join(
                input_dir, report_file["sourceKey"], report_file["originalFilename"]
            )
            if not os.path.exists(report_path):
                report_path = os.path.join(input_dir, report_file["originalFilename"])
            if os.path.exists(report_path):
                with open(report_path, "r", encoding="utf-8") as fh:
                    report = json.load(fh)

        # ---- 4. QC 检查 ----
        checks = []

        # 4a. 左右视频帧数一致性
        frame_counts = [v.get("nbFrames", 0) for v in video_info.values()]
        sk_list = list(video_info.keys())
        if len(set(frame_counts)) == 1:
            checks.append({
                "check": "stereo_frame_count_match",
                "passed": True,
                "detail": f"左右帧数一致: {frame_counts[0]}",
            })
        else:
            detail_parts = [f"{sk}={fc}" for sk, fc in zip(sk_list, frame_counts)]
            checks.append({
                "check": "stereo_frame_count_match",
                "passed": False,
                "detail": f"左右帧数不一致: {', '.join(detail_parts)}",
            })

        # 4b. IMU 时间戳覆盖视频时长
        video_durations = [v.get("durationS", 0) for v in video_info.values()]
        max_video_dur = max(video_durations) if video_durations else 0
        imu_dur_ns = imu_time_range_ns[1] - imu_time_range_ns[0]
        imu_dur_s = imu_dur_ns / 1e9 if imu_dur_ns > 0 else 0

        if imu_rows == 0:
            checks.append({
                "check": "imu_temporal_coverage",
                "passed": False,
                "detail": "未找到 IMU_ALIGNED_CSV 数据",
            })
        elif imu_dur_s >= max_video_dur * 0.95:
            checks.append({
                "check": "imu_temporal_coverage",
                "passed": True,
                "detail": f"IMU {imu_dur_s:.2f}s >= 视频 {max_video_dur:.2f}s * 0.95",
            })
        else:
            checks.append({
                "check": "imu_temporal_coverage",
                "passed": False,
                "detail": f"IMU 覆盖不足: {imu_dur_s:.2f}s vs 视频 {max_video_dur:.2f}s",
            })

        # 4c. IMU 行数与视频帧数一致性
        expected_imu_rows = max(frame_counts) if frame_counts else 0
        if expected_imu_rows > 0:
            row_diff = abs(imu_rows - expected_imu_rows)
            if row_diff <= 1:
                checks.append({
                    "check": "imu_frame_row_match",
                    "passed": True,
                    "detail": f"IMU {imu_rows} 行 ≈ 视频 {expected_imu_rows} 帧",
                })
            else:
                checks.append({
                    "check": "imu_frame_row_match",
                    "passed": False,
                    "detail": f"IMU {imu_rows} 行 vs 视频 {expected_imu_rows} 帧 (差 {row_diff})",
                })

        # 4d. 丢帧检测
        for sk, info in video_info.items():
            fps_avg = info.get("fpsAvg", 0)
            fps_expected = 20.0
            if fps_avg > 0 and abs(fps_avg - fps_expected) / fps_expected > 0.05:
                checks.append({
                    "check": f"fps_deviation_{sk}",
                    "passed": False,
                    "detail": f"{sk}: 实际 {fps_avg:.1f}fps vs 预期 {fps_expected}fps",
                })

        # ---- 5. 综合评分 ----
        passed = sum(1 for c in checks if c["passed"])
        total = len(checks)
        score = round(passed / total * 100, 1) if total > 0 else 0
        status = "PASSED" if score == 100 else ("WARNING" if score >= 50 else "FAILED")

        qc_summary = {
            "generatedAt": datetime.now(CST).isoformat(),
            "pipelineId": self.pipeline_id,
            "status": status,
            "score": score,
            "passedChecks": passed,
            "totalChecks": total,
            "checks": checks,
            "videoInfo": {
                sk: {
                    "nbFrames": v["nbFrames"],
                    "durationS": v["durationS"],
                    "fpsAvg": v["fpsAvg"],
                    "sizeMB": round(v["sizeBytes"] / 1024 / 1024, 2),
                }
                for sk, v in video_info.items()
            },
            "imuInfo": {
                "rows": imu_rows,
                "timeRangeNs": imu_time_range_ns,
            },
        }

        qc_path = os.path.join(output_dir, "qc_summary.json")
        with open(qc_path, "w", encoding="utf-8") as fh:
            json.dump(qc_summary, fh, indent=2, ensure_ascii=False)

        print(f"[BUILD_PLAYBACK_BUNDLE] QC: {status} ({score}%), "
              f"{passed}/{total} 通过")
        return [{
            "sourceKey": "qc",
            "fileName": "qc_summary.json",
            "localPath": qc_path,
            "assetType": "QC_SUMMARY",
            "contentType": "application/json",
        }]

    # ---- helpers ----

    @staticmethod
    def _find_file(input_files: List[Dict], asset_type: str) -> Optional[Dict]:
        for f in input_files:
            if f.get("assetType") == asset_type:
                return f
        return None

    @staticmethod
    def _probe_video(path: str) -> Dict:
        """用 ffprobe 获取视频元数据"""
        cmd = [
            "ffprobe", "-v", "quiet",
            "-print_format", "json",
            "-show_format", "-show_streams",
            path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        info = {"nbFrames": 0, "durationS": 0.0, "fpsAvg": 0.0}

        if result.returncode == 0:
            try:
                data = json.loads(result.stdout)
                for stream in data.get("streams", []):
                    if stream.get("codec_type") == "video":
                        nb = stream.get("nb_frames", "0")
                        info["nbFrames"] = int(nb) if str(nb).isdigit() else 0
                        dur = stream.get("duration",
                                         data.get("format", {}).get("duration", "0"))
                        info["durationS"] = float(dur) if dur else 0.0
                        rfr = stream.get("r_frame_rate", "0/1")
                        parts = rfr.split("/")
                        if len(parts) == 2 and parts[1] != "0":
                            info["fpsAvg"] = float(parts[0]) / float(parts[1])
                        break
                if info["durationS"] > 0 and info["nbFrames"] > 0:
                    info["fpsAvg"] = info["nbFrames"] / info["durationS"]
            except (json.JSONDecodeError, ValueError, KeyError):
                pass

        return info
