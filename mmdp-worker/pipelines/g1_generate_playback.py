"""
G1_GENERATE_PLAYBACK Pipeline

从合并后的 G1 HDF5 中读取左相机 JPEG 帧，生成一个可在线播放的 MP4。
"""
import os
import re
import shutil
import subprocess
from typing import Dict, List

from .base import BasePipeline


class G1GeneratePlaybackPipeline(BasePipeline):
    """G1 合并数据 -> MP4 播放资产"""

    pipeline_id = "G1_GENERATE_PLAYBACK"
    display_name = "G1可视化播放资产生成"
    description = "从合并后的G1 HDF5中提取左相机图像，生成可在线播放的MP4"
    version = "1.0.0"
    input_asset_types = ["G1_MERGED_HDF5"]
    output_asset_types = ["RGB_VIDEO_MP4"]
    runtime_dependencies = ["h5py", "ffmpeg"]
    worker_type = "CPU"

    default_fps = 20

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        try:
            import h5py
        except ImportError as exc:
            raise RuntimeError("h5py 未安装，请执行: pip install h5py") from exc

        ffmpeg = shutil.which("ffmpeg")
        if not ffmpeg:
            raise RuntimeError("未找到 ffmpeg，请先安装并加入 PATH")

        merged_files = [
            item for item in input_files
            if item.get("assetType") == "G1_MERGED_HDF5"
        ]
        merged_files.sort(key=lambda item: self._natural_key(item.get("originalFilename", "")))
        if not merged_files:
            raise RuntimeError("未找到 G1_MERGED_HDF5 输入文件")

        os.makedirs(output_dir, exist_ok=True)
        output_name = "g1_playback.mp4"
        output_path = os.path.join(output_dir, output_name)
        command = [
            ffmpeg,
            "-y",
            "-loglevel", "error",
            "-framerate", str(self.default_fps),
            "-f", "image2pipe",
            "-vcodec", "mjpeg",
            "-i", "pipe:0",
            "-an",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            output_path,
        ]

        process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        frame_count = 0
        try:
            assert process.stdin is not None
            for item in merged_files:
                local_path = os.path.join(
                    input_dir,
                    item.get("sourceKey", ""),
                    item.get("originalFilename", ""),
                )
                if not os.path.isfile(local_path):
                    raise RuntimeError(f"输入文件不存在: {local_path}")

                with h5py.File(local_path, "r") as hdf5_file:
                    if "observation_image_left" not in hdf5_file:
                        raise RuntimeError(f"HDF5 缺少 observation_image_left: {local_path}")
                    for frame in hdf5_file["observation_image_left"]:
                        process.stdin.write(frame.tobytes() if hasattr(frame, "tobytes") else bytes(frame))
                        frame_count += 1

            process.stdin.close()
            stderr = process.stderr.read().decode("utf-8", errors="replace") if process.stderr else ""
            if process.stderr:
                process.stderr.close()
            return_code = process.wait()
        except Exception:
            process.kill()
            process.wait()
            if process.stderr:
                process.stderr.close()
            raise

        if return_code != 0:
            raise RuntimeError(f"ffmpeg 生成 MP4 失败: {stderr[-500:]}")
        if frame_count == 0 or not os.path.isfile(output_path):
            raise RuntimeError("HDF5 中没有可用图像帧")

        print(f"[G1_PLAYBACK] 生成完成: {output_path}, frames={frame_count}, fps={self.default_fps}")
        return [{
            "sourceKey": "camera_svo2",
            "fileName": output_name,
            "localPath": output_path,
            "assetType": "RGB_VIDEO_MP4",
            "contentType": "video/mp4",
        }]

    @staticmethod
    def _natural_key(filename: str):
        return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", filename)]
