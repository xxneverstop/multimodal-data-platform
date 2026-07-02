"""
BUILD_PLAYBACK Pipeline
将 JPG 图像序列目录分别合成为 MP4 视频文件。
依赖: ffmpeg（命令行可用）
"""
import os
import subprocess
import glob
from typing import List, Dict

from .base import BasePipeline


class BuildPlaybackPipeline(BasePipeline):
    """图像序列 → MP4 视频合成"""

    pipeline_id = "BUILD_PLAYBACK"
    display_name = "图像序列 → MP4 视频"
    description = "将按帧命名的 JPG/PNG 图像序列目录合成为 MP4 视频文件，支持 %06d 和 concat 两种模式"
    version = "1.0.0"
    input_asset_types = ["RGB_SEQ_RAW", "LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE"]
    output_asset_types = ["RGB_VIDEO_MP4"]
    runtime_dependencies = ["ffmpeg"]

    default_fps = 20

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        """执行图像序列 → MP4 合成"""
        # 按 sourceKey 分组，找到图像序列目录
        sequence_dirs: Dict[str, List[str]] = {}
        for f in input_files:
            source_key = f.get("sourceKey", "")
            filename = f.get("originalFilename", "")
            if filename.lower().endswith((".jpg", ".jpeg", ".png", ".bmp")):
                if source_key not in sequence_dirs:
                    sequence_dirs[source_key] = []
                sequence_dirs[source_key].append(filename)

        outputs = []
        for source_key in sequence_dirs:
            source_dir = os.path.join(input_dir, source_key)
            output_name = f"{source_key}.mp4"
            output_path = os.path.join(output_dir, output_name)

            jpg_files = sorted(glob.glob(os.path.join(source_dir, "*.jpg")))
            if not jpg_files:
                print(f"[BUILD_PLAYBACK] 未在 {source_dir} 找到 jpg 文件，跳过")
                continue

            # 尝试用 %06d.jpg 模式
            cmd = [
                "ffmpeg", "-y",
                "-framerate", str(self.default_fps),
                "-i", os.path.join(source_dir, "%06d.jpg"),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                output_path
            ]

            print(f"[BUILD_PLAYBACK] 执行: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True)

            if result.returncode != 0:
                # 回退: 用 concat 方式
                print(f"[BUILD_PLAYBACK] %06d 模式失败，尝试 concat: {result.stderr[-200:]}")
                concat_list = os.path.join(output_dir, f"{source_key}_concat.txt")
                with open(concat_list, "w") as f:
                    for jpg in jpg_files:
                        f.write(f"file '{os.path.abspath(jpg)}'\n")
                cmd2 = [
                    "ffmpeg", "-y",
                    "-f", "concat", "-safe", "0",
                    "-framerate", str(self.default_fps),
                    "-i", concat_list,
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    output_path
                ]
                result2 = subprocess.run(cmd2, capture_output=True, text=True)
                if result2.returncode != 0:
                    raise RuntimeError(f"ffmpeg 失败: {result2.stderr[-500:]}")
                os.remove(concat_list)

            file_size = os.path.getsize(output_path)
            print(f"[BUILD_PLAYBACK] 生成: {output_path} ({file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": output_name,
                "localPath": output_path,
                "assetType": "RGB_VIDEO_MP4",
                "contentType": "video/mp4",
            })

        return outputs
