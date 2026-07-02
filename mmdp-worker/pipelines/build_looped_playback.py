"""
BUILD_LOOPED_PLAYBACK Pipeline
将 JPG 图像序列合成为 MP4，再将视频循环播放 10 次，输出加长版 MP4。

示例：11 帧 @20fps = 0.55 秒 → 循环 10 次 → 5.5 秒输出视频
依赖: ffmpeg（命令行可用）
"""
import os
import subprocess
import glob
from typing import List, Dict

from .base import BasePipeline


class BuildLoopedPlaybackPipeline(BasePipeline):
    """图像序列 → MP4 → 循环 10 次"""

    pipeline_id = "BUILD_LOOPED_PLAYBACK"
    display_name = "图像序列 → 循环 MP4（x10）"
    description = "将图像序列合成为 MP4 视频后，使用 -stream_loop 将视频循环播放 10 次，输出加长版 MP4"
    version = "1.0.0"
    input_asset_types = ["RGB_SEQ_RAW", "LEFT_IMAGE_SEQUENCE", "RIGHT_IMAGE_SEQUENCE"]
    output_asset_types = ["RGB_VIDEO_MP4"]
    runtime_dependencies = ["ffmpeg"]

    default_fps = 20
    loop_count = 10  # 循环次数，1 秒变 10 秒

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        """执行图像序列 → MP4 → 循环 10 次"""
        # Step 1: 先调用 BUILD_PLAYBACK 逻辑生成原始 MP4
        raw_outputs = self._frames_to_mp4(input_dir, output_dir, input_files)

        # Step 2: 对每个原始 MP4 进行循环 10 次处理
        outputs = []
        for out in raw_outputs:
            raw_path = out["localPath"]
            source_key = out["sourceKey"]

            # 循环后的文件名
            base, ext = os.path.splitext(out["fileName"])
            looped_name = f"{base}_looped{ext}"
            looped_path = os.path.join(output_dir, looped_name)

            self._loop_video(raw_path, looped_path)

            # 删除原始短的 MP4，只保留循环后的
            os.remove(raw_path)

            file_size = os.path.getsize(looped_path)
            print(f"[LOOPED] {looped_path} ({file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": looped_name,
                "localPath": looped_path,
                "assetType": out["assetType"],
                "contentType": out["contentType"],
            })

        return outputs

    def _frames_to_mp4(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        """图像序列 → MP4（复用 BUILD_PLAYBACK 核心逻辑）"""
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
                print(f"[BUILD_LOOPED] 未在 {source_dir} 找到 jpg 文件，跳过")
                continue

            # 尝试 %06d.jpg 模式
            cmd = [
                "ffmpeg", "-y",
                "-framerate", str(self.default_fps),
                "-i", os.path.join(source_dir, "%06d.jpg"),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                output_path
            ]

            print(f"[BUILD_LOOPED] ffmpeg: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True)

            if result.returncode != 0:
                # 回退: concat 模式
                print(f"[BUILD_LOOPED] %06d 失败，尝试 concat: {result.stderr[-200:]}")
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
                    raise RuntimeError(f"ffmpeg frames->mp4 失败: {result2.stderr[-500:]}")
                os.remove(concat_list)

            file_size = os.path.getsize(output_path)
            print(f"[BUILD_LOOPED] 原始 MP4: {output_path} ({file_size} bytes)")

            outputs.append({
                "sourceKey": source_key,
                "fileName": output_name,
                "localPath": output_path,
                "assetType": "RGB_VIDEO_MP4",
                "contentType": "video/mp4",
            })

        return outputs

    def _loop_video(self, input_path: str, output_path: str):
        """
        将 MP4 视频循环 loop_count 次。
        使用 concat demuxer + 重编码方式，避免 -stream_loop -c copy 导致
        的 PTS 不连续问题（浏览器无法播放）。
        -movflags +faststart 将 moov atom 移到文件头部，浏览器可边下边播。
        """
        concat_file = output_path + ".concat.txt"
        abs_input = os.path.abspath(input_path).replace("\\", "/")
        with open(concat_file, "w") as f:
            for _ in range(self.loop_count):
                f.write(f"file '{abs_input}'\n")

        cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0",
            "-fflags", "+genpts",
            "-i", concat_file,
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            output_path
        ]
        print(f"[BUILD_LOOPED] loop (concat x{self.loop_count}): {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True)
        try:
            os.remove(concat_file)
        except OSError:
            pass
        if result.returncode != 0:
            raise RuntimeError(f"ffmpeg loop 失败: {result.stderr[-500:]}")
