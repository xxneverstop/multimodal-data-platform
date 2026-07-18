"""
G1_CONVERT_TO_LEROBOT Pipeline
将合并后的 G1 HDF5 数据转换为 LeRobot VLA 训练格式。

处理流程：
  1. 读取 input_dir 中的合并后 HDF5 文件（由 G1_MERGE_CAMERA_ROBOT 产出）
  2. 构建 LeRobot 特征定义（observation.images.left + 动作/状态向量）
  3. 逐 episode 流式读取帧 → 写入 LeRobotDataset（batch loading 控制内存）
  4. 输出 parquet + video 文件作为产物

依赖: lerobot, h5py, numpy, opencv-python
"""
import os
import re
import gc
import traceback
from typing import List, Dict, Optional
from pathlib import Path

import numpy as np

from .base import BasePipeline


# LeRobot 特征定义（与 convert_to_lerobot.py 中 G1_CONFIG 一致）
G1_FEATURES_CONFIG = {
    "images": {
        "left": {
            "dtype": "image",
            "shape": (720, 1280, 3),
            "names": ["height", "width", "channel"],
        },
    },
    "states": {
        # G1 合并数据中暂无可直接映射的 observation state，留空
    },
    "actions": {
        "action_eef": {
            "dtype": "float32",
            "shape": (14,),
            "names": ["action_eef"],
        },
        "action_delta_eef": {
            "dtype": "float32",
            "shape": (12,),
            "names": ["action_delta_eef"],
        },
        "teleop_navigate": {
            "dtype": "float32",
            "shape": (3,),
            "names": ["teleop_navigate_command"],
        },
        "delta_height": {
            "dtype": "float32",
            "shape": (1,),
            "names": ["delta_height"],
        },
        "hand_status": {
            "dtype": "float32",
            "shape": (2,),
            "names": ["hand_status"],
        },
    },
}


def _extract_episode_number(filename: str) -> Optional[int]:
    """从文件名中提取 episode 编号"""
    m = re.search(r'episode[_-](\d+)', filename, re.IGNORECASE)
    if m:
        return int(m.group(1))
    return None


def _find_hdf5_files(input_dir: str) -> List[str]:
    """递归查找 input_dir 下所有 .hdf5 文件，按 episode 编号排序"""
    hdf5_files = []
    for root, _dirs, files in os.walk(input_dir):
        for f in files:
            if f.endswith('.hdf5') or f.endswith('.h5'):
                hdf5_files.append(os.path.join(root, f))
    hdf5_files.sort(key=lambda p: _extract_episode_number(os.path.basename(p)) or 0)
    return hdf5_files


def generate_features_from_config(config: dict) -> dict:
    """从配置生成 LeRobot features 字典"""
    features = {}
    for key, value in config.get("images", {}).items():
        features[f"observation.images.{key}"] = {
            "dtype": value["dtype"],
            "shape": value["shape"],
            "names": value["names"],
        }
    for key, value in config.get("states", {}).items():
        features[f"observation.{key}"] = {
            "dtype": value["dtype"],
            "shape": value["shape"],
            "names": value["names"],
        }
    for key, value in config.get("actions", {}).items():
        features[key] = {
            "dtype": value["dtype"],
            "shape": value["shape"],
            "names": value["names"],
        }
    return features


def _decode_image(img_data, cv2_module):
    """解码图像数据 → RGB numpy array"""
    if isinstance(img_data, np.ndarray) and img_data.ndim == 3:
        return img_data
    if isinstance(img_data, bytes):
        img = cv2_module.imdecode(np.frombuffer(img_data, np.uint8), cv2_module.IMREAD_COLOR)
        return cv2_module.cvtColor(img, cv2_module.COLOR_BGR2RGB)
    if isinstance(img_data, np.ndarray) and img_data.ndim == 1:
        img = cv2_module.imdecode(img_data.astype(np.uint8), cv2_module.IMREAD_COLOR)
        return cv2_module.cvtColor(img, cv2_module.COLOR_BGR2RGB)
    if isinstance(img_data, np.void):
        img_bytes = bytes(img_data)
        img = cv2_module.imdecode(np.frombuffer(img_bytes, np.uint8), cv2_module.IMREAD_COLOR)
        return cv2_module.cvtColor(img, cv2_module.COLOR_BGR2RGB)
    raise ValueError(f"未知图像格式: type={type(img_data)}, "
                     f"shape={getattr(img_data, 'shape', 'N/A')}")


class G1ConvertToLerobotPipeline(BasePipeline):
    """G1 数据 → LeRobot 训练格式"""

    pipeline_id = "G1_CONVERT_TO_LEROBOT"
    display_name = "G1数据→LeRobot训练格式"
    description = "将合并后的G1 HDF5数据转换为LeRobot VLA训练格式（Parquet+Video），支持批量流式转换"
    version = "1.0.0"
    input_asset_types = ["G1_MERGED_HDF5"]
    output_asset_types = ["G1_LEROBOT_PARQUET"]
    runtime_dependencies = ["lerobot", "h5py", "numpy", "opencv-python"]

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        """
        执行 LeRobot 格式转换。

        参数（通过 parameters JSON 传入）:
          - fps: 帧率，默认 20
          - task_instruction: 任务描述，默认 "G1 robot manipulation task"
          - batch_size: 每批加载帧数，默认 100
        """
        # --- 1. 解析参数 ---
        params = self._load_parameters(input_dir)
        fps = int(params.get("fps", 20))
        task_instruction = params.get("task_instruction", "G1 robot manipulation task")
        batch_size = int(params.get("batch_size", 100))
        repo_id = params.get("repo_id", "g1_dataset")

        print(f"[G1_LEROBOT] 参数: fps={fps}, task='{task_instruction}', "
              f"batch_size={batch_size}, repo_id={repo_id}")

        # --- 2. 导入依赖 ---
        try:
            import cv2
        except ImportError:
            raise RuntimeError("opencv-python 未安装，请执行: pip install opencv-python")

        try:
            import h5py
        except ImportError:
            raise RuntimeError("h5py 未安装，请执行: pip install h5py")

        try:
            from lerobot.datasets.lerobot_dataset import LeRobotDataset
        except ImportError:
            try:
                from lerobot.common.datasets.lerobot_dataset import LeRobotDataset
            except ImportError:
                raise RuntimeError(
                    "lerobot 未安装或版本不兼容，请执行: pip install lerobot\n"
                    "如果安装失败，可能需要从 GitHub 源码安装:\n"
                    "  pip install git+https://github.com/huggingface/lerobot.git"
                )

        # --- 3. 查找输入 HDF5 ---
        hdf5_files = _find_hdf5_files(input_dir)
        if not hdf5_files:
            raise RuntimeError(f"input_dir 中未找到 .hdf5 文件: {input_dir}")
        print(f"[G1_LEROBOT] 找到 {len(hdf5_files)} 个 HDF5 文件")

        # --- 4. 创建 LeRobot Dataset ---
        features = generate_features_from_config(G1_FEATURES_CONFIG)
        print(f"[G1_LEROBOT] 特征定义: {list(features.keys())}")

        # LeRobotDataset.create() 内部 os.makedirs(root) 不带 exist_ok，
        # 而 Worker 主循环已预创建了 output_dir，在 Windows 上会报 WinError 183。
        # 移除空目录让 LeRobot 自行创建。
        if os.path.isdir(output_dir) and not os.listdir(output_dir):
            os.rmdir(output_dir)

        print(f"[G1_LEROBOT] 创建 LeRobot dataset: root={output_dir}, repo_id={repo_id}")
        dataset = LeRobotDataset.create(
            repo_id=repo_id,
            root=output_dir,
            fps=fps,
            robot_type="g1",
            features=features,
            image_writer_threads=4,
            image_writer_processes=2,
        )

        local_dir = os.path.join(output_dir, repo_id)

        # --- 5. 逐 episode 转换 ---
        total_frames = 0
        successful_episodes = 0
        for hdf5_path in hdf5_files:
            ep_num = _extract_episode_number(os.path.basename(hdf5_path))
            ep_name = f"episode_{ep_num}" if ep_num is not None else os.path.basename(hdf5_path)
            print(f"[G1_LEROBOT] 处理 {ep_name}: {hdf5_path}")

            try:
                ep_frames = 0
                for frames_batch in self._load_episode_batch(
                    hdf5_path, task_instruction, batch_size, cv2
                ):
                    for frame in frames_batch:
                        dataset.add_frame(frame)
                        ep_frames += 1

                dataset.save_episode()
                total_frames += ep_frames
                successful_episodes += 1
                print(f"[G1_LEROBOT]   {ep_name}: {ep_frames} 帧 (累计 {total_frames})")

            except Exception as e:
                print(f"[G1_LEROBOT] [ERR] {ep_name} 转换失败: {e}")
                traceback.print_exc()
                dataset.episode_buffer = None  # 重置缓冲区
                continue

            # 定期 GC
            if successful_episodes % 5 == 0:
                gc.collect()

        print(f"[G1_LEROBOT] 转换完成: {successful_episodes}/{len(hdf5_files)} episodes, "
              f"{total_frames} 帧, {dataset.num_episodes} episodes in dataset")

        # --- 6. 收集产物文件 ---
        outputs = []
        source_key = "robot_hdf5"  # 继承上游 sourceKey

        # 扫描整个 output_dir（不同版本 LeRobot 的 root/repo_id 路径行为有差异，
        # 直接扫 output_dir 避免子目录结构假设不一致导致漏收集）
        print(f"[G1_LEROBOT] 扫描产物目录: {output_dir}")

        # 产物扩展名 → (assetType, contentType) 映射，其余文件跳过
        OUTPUT_EXT_MAP = {
            '.parquet': ('G1_LEROBOT_PARQUET', 'application/octet-stream'),
            '.mp4':     ('RGB_VIDEO_MP4', 'video/mp4'),
            '.json':    ('OTHER', 'application/json'),
            '.jsonl':   ('OTHER', 'application/jsonl'),
            '.md':      ('OTHER', 'text/markdown'),
        }

        for root, dirs, files in os.walk(output_dir):
            for f in files:
                file_path = os.path.join(root, f)
                rel_path = os.path.relpath(file_path, output_dir)

                matched = False
                for ext, (asset_type, content_type) in OUTPUT_EXT_MAP.items():
                    if f.endswith(ext):
                        matched = True
                        break
                if not matched:
                    continue

                outputs.append({
                    "sourceKey": source_key,
                    "fileName": rel_path.replace(os.sep, '/'),
                    "localPath": file_path,
                    "assetType": asset_type,
                    "contentType": content_type,
                })

        if not outputs:
            # 调试：列出实际存在的文件帮助定位
            print(f"[G1_LEROBOT] [WARN] 未找到匹配产物！目录结构:")
            for root, dirs, files in os.walk(output_dir):
                for f in files:
                    print(f"[G1_LEROBOT]   {os.path.join(root, f)}")

        print(f"[G1_LEROBOT] 产物文件数: {len(outputs)}")
        for o in outputs:
            print(f"[G1_LEROBOT]   {o['fileName']} ({o['assetType']})")

        return outputs

    # ------------------------------------------------------------------
    # 内部方法
    # ------------------------------------------------------------------

    @staticmethod
    def _get_parameters(input_files: List[Dict]) -> dict:
        """从 input_dir/parameters.json 读取作业参数。

        Worker 在调用 execute() 前会将 job.parameters 写入
        work_dir/parameters.json（work_dir 即 input_dir）。
        """
        # 方式1: 从 input_dir 的父目录（work_dir）读 parameters.json
        #        注意: execute 的 input_dir 参数就是 work_dir
        #        但我们在这个静态方法中没法直接拿到 input_dir
        #        所以从 input_files 推断路径（如果 files 为空则回退到其他方式）
        # 方式2: 环境变量
        import json as _json
        env_params = os.environ.get("MMDP_JOB_PARAMETERS", "")
        if env_params:
            try:
                return _json.loads(env_params)
            except _json.JSONDecodeError:
                pass
        return {}

    def _load_parameters(self, input_dir: str) -> dict:
        """从 input_dir 加载参数（实例方法，有 input_dir 上下文）"""
        import json as _json
        params_path = os.path.join(input_dir, "parameters.json")
        if os.path.exists(params_path):
            try:
                with open(params_path, 'r', encoding='utf-8') as f:
                    return _json.load(f)
            except Exception:
                pass
        return {}

    @staticmethod
    def _load_episode_batch(hdf5_path: str, task_instruction: str,
                            batch_size: int, cv2_module):
        """批量加载 HDF5 episode 帧数据（生成器，控制内存）"""
        import h5py

        with h5py.File(hdf5_path, 'r') as f:
            # 检查必要字段
            required = [
                "action_eef", "action_delta_eef", "teleop_navigate_command",
                "delta_height", "hand_status", "observation_image_left"
            ]
            missing = [k for k in required if k not in f]
            if missing:
                raise KeyError(f"HDF5 缺少字段: {missing}")

            num_frames = f["action_eef"].shape[0]

            for start_idx in range(0, num_frames, batch_size):
                end_idx = min(start_idx + batch_size, num_frames)

                teleop_navigate = np.array(
                    f["teleop_navigate_command"][start_idx:end_idx], dtype=np.float32)
                delta_height_data = np.array(
                    f["delta_height"][start_idx:end_idx], dtype=np.float32)
                hand_status_data = np.array(
                    f["hand_status"][start_idx:end_idx], dtype=np.float32)
                action_eef = np.array(
                    f["action_eef"][start_idx:end_idx], dtype=np.float32)
                action_delta_eef = np.array(
                    f["action_delta_eef"][start_idx:end_idx], dtype=np.float32)

                images_left_raw = f["observation_image_left"][start_idx:end_idx]
                images_left = [_decode_image(img, cv2_module) for img in images_left_raw]

                frames = []
                for i in range(end_idx - start_idx):
                    frame = {
                        "observation.images.left": np.array(images_left[i], dtype=np.uint8),
                        "action_eef": action_eef[i],
                        "action_delta_eef": action_delta_eef[i],
                        "teleop_navigate": teleop_navigate[i],
                        "delta_height": delta_height_data[i].reshape(1,),
                        "hand_status": hand_status_data[i],
                        "task": task_instruction,
                    }
                    frames.append(frame)
                yield frames

                del teleop_navigate, delta_height_data, hand_status_data
                del action_eef, action_delta_eef, images_left, images_left_raw
