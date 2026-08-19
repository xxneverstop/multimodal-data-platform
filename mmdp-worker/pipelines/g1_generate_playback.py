"""G1_GENERATE_PLAYBACK：从合并后的 G1 HDF5 生成左右眼播放视频。"""

import os
import re
import shutil
import subprocess
from typing import Dict, List

from .base import BasePipeline


class G1GeneratePlaybackPipeline(BasePipeline):
    """G1 合并数据 -> 左右眼 MP4 播放资产。"""

    pipeline_id = "G1_GENERATE_PLAYBACK"
    display_name = "G1 双目可视化播放资产生成"
    description = "从合并后的 G1 HDF5 中提取左右眼图像，生成同步的双目 MP4"
    version = "2.0.0"
    input_asset_types = ["G1_MERGED_HDF5"]
    output_asset_types = ["RGB_VIDEO_MP4"]
    runtime_dependencies = ["h5py", "ffmpeg"]
    worker_type = "CPU"

    default_fps = 20
    _streams = (
        ("left", "observation_image_left", "camera_svo2_left", "g1_playback_left.mp4"),
        ("right", "observation_image_right", "camera_svo2_right", "g1_playback_right.mp4"),
    )

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        try:
            import h5py
        except ImportError as exc:
            raise RuntimeError("h5py 未安装，请执行 pip install h5py") from exc

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

        local_paths = [self._resolve_input_path(input_dir, item) for item in merged_files]
        expected_frames = self._validate_stereo_inputs(h5py, local_paths)

        os.makedirs(output_dir, exist_ok=True)
        final_paths = {
            eye: os.path.join(output_dir, output_name)
            for eye, _, _, output_name in self._streams
        }
        temporary_paths = {
            eye: f"{path}.part.mp4"
            for eye, path in final_paths.items()
        }
        for path in temporary_paths.values():
            if os.path.exists(path):
                os.remove(path)

        processes = {}
        frame_counts = {eye: 0 for eye, _, _, _ in self._streams}
        try:
            for eye, _, _, _ in self._streams:
                processes[eye] = self._start_encoder(ffmpeg, temporary_paths[eye])
            for local_path in local_paths:
                with h5py.File(local_path, "r") as hdf5_file:
                    datasets = {
                        eye: hdf5_file[dataset_name]
                        for eye, dataset_name, _, _ in self._streams
                    }
                    for frame_index in range(len(datasets["left"])):
                        for eye, _, _, _ in self._streams:
                            frame = datasets[eye][frame_index]
                            process_stdin = processes[eye].stdin
                            if process_stdin is None:
                                raise RuntimeError(f"{eye} 眼 ffmpeg 输入流不可用")
                            process_stdin.write(
                                frame.tobytes() if hasattr(frame, "tobytes") else bytes(frame)
                            )
                            frame_counts[eye] += 1

            errors = self._finish_encoders(processes)
            if errors:
                raise RuntimeError("；".join(errors))
            if frame_counts["left"] != frame_counts["right"]:
                raise RuntimeError(
                    f"双目输出帧数不一致: left={frame_counts['left']}, right={frame_counts['right']}"
                )
            if frame_counts["left"] != expected_frames:
                raise RuntimeError(
                    f"双目输出帧数异常: expected={expected_frames}, actual={frame_counts['left']}"
                )
            for path in temporary_paths.values():
                if not os.path.isfile(path) or os.path.getsize(path) == 0:
                    raise RuntimeError(f"ffmpeg 未生成有效 MP4: {path}")

            for eye, _, _, _ in self._streams:
                os.replace(temporary_paths[eye], final_paths[eye])
        except Exception:
            self._terminate_encoders(processes)
            for path in temporary_paths.values():
                if os.path.exists(path):
                    os.remove(path)
            raise

        print(
            "[G1_PLAYBACK] 双目生成完成: "
            f"left={final_paths['left']}, right={final_paths['right']}, "
            f"frames={frame_counts['left']}, fps={self.default_fps}"
        )
        return [
            {
                "sourceKey": source_key,
                "fileName": output_name,
                "localPath": final_paths[eye],
                "assetType": "RGB_VIDEO_MP4",
                "contentType": "video/mp4",
            }
            for eye, _, source_key, output_name in self._streams
        ]

    @staticmethod
    def _resolve_input_path(input_dir: str, item: Dict) -> str:
        local_path = os.path.join(
            input_dir,
            item.get("sourceKey", ""),
            item.get("originalFilename", ""),
        )
        if not os.path.isfile(local_path):
            raise RuntimeError(f"输入文件不存在: {local_path}")
        return local_path

    @classmethod
    def _validate_stereo_inputs(cls, h5py, local_paths: List[str]) -> int:
        total_frames = 0
        for local_path in local_paths:
            with h5py.File(local_path, "r") as hdf5_file:
                missing = [
                    dataset_name
                    for _, dataset_name, _, _ in cls._streams
                    if dataset_name not in hdf5_file
                ]
                if missing:
                    raise RuntimeError(f"HDF5 缺少数据集 {', '.join(missing)}: {local_path}")
                left_count = len(hdf5_file["observation_image_left"])
                right_count = len(hdf5_file["observation_image_right"])
                if left_count != right_count:
                    raise RuntimeError(
                        f"HDF5 双目帧数不一致: left={left_count}, right={right_count}, file={local_path}"
                    )
                total_frames += left_count
        if total_frames == 0:
            raise RuntimeError("HDF5 中没有可用的双目图像帧")
        return total_frames

    def _start_encoder(self, ffmpeg: str, output_path: str) -> subprocess.Popen:
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
        return subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )

    @staticmethod
    def _finish_encoders(processes: Dict[str, subprocess.Popen]) -> List[str]:
        errors = []
        for process in processes.values():
            if process.stdin and not process.stdin.closed:
                process.stdin.close()
        for eye, process in processes.items():
            stderr = process.stderr.read().decode("utf-8", errors="replace") if process.stderr else ""
            if process.stderr:
                process.stderr.close()
            return_code = process.wait()
            if return_code != 0:
                errors.append(f"{eye} 眼 ffmpeg 生成 MP4 失败: {stderr[-500:]}")
        return errors

    @staticmethod
    def _terminate_encoders(processes: Dict[str, subprocess.Popen]) -> None:
        for process in processes.values():
            if process.poll() is None:
                process.kill()
        for process in processes.values():
            process.wait()
            if process.stderr and not process.stderr.closed:
                process.stderr.close()

    @staticmethod
    def _natural_key(filename: str):
        return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", filename)]
