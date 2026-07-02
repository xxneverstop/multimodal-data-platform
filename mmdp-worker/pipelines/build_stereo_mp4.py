"""
BUILD_STEREO_MP4 Pipeline
将对齐后的左/右图像序列分别合成为 MP4 视频文件。
优先使用 aligned/ 子目录，回退到根目录。
依赖: ffmpeg（命令行可用）
"""
import os
import subprocess
import glob
from typing import List, Dict

from .base import BasePipeline


class BuildStereoMp4Pipeline(BasePipeline):
    """双目对齐帧序列 -> MP4 视频"""

    pipeline_id = "BUILD_STEREO_MP4"
    display_name = "双目帧序列->MP4"
    description = "将对齐后的左/右图像序列分别合成为MP4视频，优先使用aligned/子目录"
    version = "1.0.0"
    input_asset_types = ["LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE"]
    output_asset_types = ["RGB_VIDEO_MP4"]
    runtime_dependencies = ["ffmpeg"]

    default_fps = 20

    def execute(
        self, input_dir: str, output_dir: str, input_files: List[Dict]
    ) -> List[Dict]:
        # 收集需要处理的 sourceKey
        source_keys = set()
        for f in input_files:
            at = f.get("assetType", "")
            if at in ("LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE"):
                source_keys.add(f.get("sourceKey", ""))

        outputs = []
        for source_key in sorted(source_keys):
            source_dir = os.path.join(input_dir, source_key)

            # 优先使用 aligned/ 子目录
            aligned_dir = os.path.join(source_dir, "aligned")
            if os.path.isdir(aligned_dir) and glob.glob(os.path.join(aligned_dir, "*.jpg")):
                frames_dir = aligned_dir
                print(f"[BUILD_STEREO_MP4] {source_key}: 使用 aligned/ 子目录")
            else:
                frames_dir = source_dir
                print(f"[BUILD_STEREO_MP4] {source_key}: 使用根目录")

            jpg_files = sorted(glob.glob(os.path.join(frames_dir, "*.jpg")))
            if not jpg_files:
                print(f"[BUILD_STEREO_MP4] {source_key}: 未找到 jpg 文件，跳过")
                continue

            output_name = f"{source_key}_aligned.mp4"
            output_path = os.path.join(output_dir, output_name)

            # 尝试 %06d.jpg 模式
            ffmpeg_input = os.path.join(frames_dir, "%06d.jpg")
            cmd = [
                "ffmpeg", "-y",
                "-framerate", str(self.default_fps),
                "-i", ffmpeg_input,
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                output_path,
            ]

            print(f"[BUILD_STEREO_MP4] 执行: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True)

            if result.returncode != 0:
                # 回退: concat 模式
                print(f"[BUILD_STEREO_MP4] %06d 模式失败，尝试 concat")
                concat_list = os.path.join(output_dir, f"{source_key}_concat.txt")
                with open(concat_list, "w") as fh:
                    for jpg in jpg_files:
                        fh.write(f"file '{os.path.abspath(jpg)}'\n")
                cmd2 = [
                    "ffmpeg", "-y",
                    "-f", "concat", "-safe", "0",
                    "-framerate", str(self.default_fps),
                    "-i", concat_list,
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    output_path,
                ]
                result2 = subprocess.run(cmd2, capture_output=True, text=True)
                os.remove(concat_list)
                if result2.returncode != 0:
                    raise RuntimeError(
                        f"ffmpeg 失败 [{source_key}]: {result2.stderr[-500:]}"
                    )

            file_size = os.path.getsize(output_path)
            print(f"[BUILD_STEREO_MP4] 生成: {output_path} "
                  f"({file_size} bytes, {len(jpg_files)} frames)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": output_name,
                "localPath": output_path,
                "assetType": "RGB_VIDEO_MP4",
                "contentType": "video/mp4",
            })

        return outputs
