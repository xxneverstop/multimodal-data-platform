"""
G1_MERGE_CAMERA_ROBOT Pipeline
将 ZED 双目相机 SVO2 数据与 G1 机器人 HDF5 遥操作数据合并。

处理流程：
  1. 从 input_files 中按 assetType 分类 HDF5 和 SVO2
  2. 按 episode 编号配对
  3. 时间戳对齐 → 图像 JPEG 压缩写入 → 计算 delta EEF/delta Height
  4. 输出合并后的 HDF5

依赖: pyzed (ZED SDK), h5py, numpy, scipy, opencv-python
"""
import os
import re
import json
import traceback
from typing import List, Dict, Optional
from pathlib import Path

import numpy as np
import h5py
from scipy.spatial.transform import Rotation as R

from .base import BasePipeline


def _extract_episode_number(filename: str) -> Optional[int]:
    """从文件名中提取 episode 编号，如 episode_0.hdf5 → 0"""
    m = re.search(r'episode[_-](\d+)', filename, re.IGNORECASE)
    if m:
        return int(m.group(1))
    return None


def pose_to_transform_matrix(position: np.ndarray, quaternion: np.ndarray) -> np.ndarray:
    """位姿 → 4x4 齐次变换矩阵"""
    T = np.eye(4)
    T[:3, :3] = R.from_quat(quaternion).as_matrix()
    T[:3, 3] = position
    return T


def transform_matrix_to_xyzrpy(T: np.ndarray) -> np.ndarray:
    """变换矩阵 → [x, y, z, roll, pitch, yaw]"""
    xyz = T[:3, 3]
    rpy = R.from_matrix(T[:3, :3]).as_euler('xyz')
    return np.concatenate([xyz, rpy])


def compute_delta_eef_for_hand(eef_data: np.ndarray) -> np.ndarray:
    """计算单手 delta EEF（末端执行器增量位姿）"""
    n_frames = len(eef_data)
    delta_eef = np.zeros((n_frames, 6))
    for i in range(1, n_frames):
        pos_prev = eef_data[i - 1, :3]
        quat_scalar_first_prev = eef_data[i - 1, 3:7]  # (qw, qx, qy, qz)
        pos_curr = eef_data[i, :3]
        quat_scalar_first_curr = eef_data[i, 3:7]

        quat_vector_first_prev = np.array([
            quat_scalar_first_prev[1], quat_scalar_first_prev[2],
            quat_scalar_first_prev[3], quat_scalar_first_prev[0]])
        quat_vector_first_curr = np.array([
            quat_scalar_first_curr[1], quat_scalar_first_curr[2],
            quat_scalar_first_curr[3], quat_scalar_first_curr[0]])

        T_prev = pose_to_transform_matrix(pos_prev, quat_vector_first_prev)
        T_curr = pose_to_transform_matrix(pos_curr, quat_vector_first_curr)
        delta_T = np.linalg.inv(T_prev) @ T_curr
        delta_eef[i] = transform_matrix_to_xyzrpy(delta_T)
    return delta_eef


def compute_delta_eef(eef_data: np.ndarray) -> np.ndarray:
    """计算双手 delta EEF"""
    left_eef = eef_data[:, :7]
    right_eef = eef_data[:, 7:14]
    left_delta = compute_delta_eef_for_hand(left_eef)
    right_delta = compute_delta_eef_for_hand(right_eef)
    return np.concatenate([left_delta, right_delta], axis=1)


def compute_delta_height(height_data: np.ndarray) -> np.ndarray:
    """计算 delta height（高度增量）"""
    if height_data.ndim > 1:
        height_data = height_data.squeeze()
    n_frames = len(height_data)
    delta_height = np.zeros(n_frames, dtype=np.float32)
    if n_frames > 1:
        delta_height[1:] = height_data[1:] - height_data[:-1]
    return delta_height


class G1MergeCameraRobotPipeline(BasePipeline):
    """G1 相机-机器人数据合并"""

    pipeline_id = "G1_MERGE_CAMERA_ROBOT"
    display_name = "G1相机-机器人数据合并"
    description = "将ZED双目相机SVO2数据与机器人HDF5遥操作数据合并：时间戳对齐、图像JPEG压缩、计算delta EEF/delta Height"
    version = "1.0.0"
    input_asset_types = ["G1_ROBOT_HDF5", "G1_CAMERA_SVO2"]
    output_asset_types = ["G1_MERGED_HDF5"]
    runtime_dependencies = ["pyzed", "h5py", "numpy", "scipy", "opencv-python"]

    # 时间戳差阈值（毫秒）
    WARNING_THRESHOLD_MS = 20.0
    SEVERE_THRESHOLD_MS = 50.0

    def execute(self, input_dir: str, output_dir: str, input_files: List[Dict]) -> List[Dict]:
        """
        执行合并 Pipeline。

        期望 input_dir 结构：
          input_dir/
            robot_hdf5/     ← G1_ROBOT_HDF5 文件
              episode_0.hdf5
              episode_1.hdf5
              ...
            camera_svo2/    ← G1_CAMERA_SVO2 文件
              episode_0.svo2
              episode_1.svo2
              ...
        """
        # --- 1. 按 assetType 分组 input_files，并按 episode 编号建立索引 ---
        hdf5_files: Dict[int, Dict] = {}   # episode_num -> input_file dict
        svo2_files: Dict[int, Dict] = {}   # episode_num -> input_file dict

        for f in input_files:
            at = f.get("assetType", "")
            filename = f.get("originalFilename", "")
            ep = _extract_episode_number(filename)
            if ep is None:
                print(f"[G1_MERGE] 无法从文件名提取 episode 编号: {filename}，跳过")
                continue
            if at == "G1_ROBOT_HDF5":
                hdf5_files[ep] = f
            elif at == "G1_CAMERA_SVO2":
                svo2_files[ep] = f

        # 配对
        paired_episodes = sorted(set(hdf5_files.keys()) & set(svo2_files.keys()))
        if not paired_episodes:
            raise RuntimeError(
                f"未找到任何配对的 episode。HDF5 有: {sorted(hdf5_files.keys())}, "
                f"SVO2 有: {sorted(svo2_files.keys())}"
            )
        print(f"[G1_MERGE] 找到 {len(paired_episodes)} 个配对的 episode: {paired_episodes}")

        unpaired_hdf5 = set(hdf5_files.keys()) - set(svo2_files.keys())
        unpaired_svo2 = set(svo2_files.keys()) - set(hdf5_files.keys())
        if unpaired_hdf5:
            print(f"[G1_MERGE] [WARN] 以下 episode 仅有 HDF5 无 SVO2: {unpaired_hdf5}")
        if unpaired_svo2:
            print(f"[G1_MERGE] [WARN] 以下 episode 仅有 SVO2 无 HDF5: {unpaired_svo2}")

        # --- 2. 导入 ZED SDK ---
        try:
            import pyzed.sl as sl
        except ImportError:
            raise RuntimeError(
                "ZED SDK (pyzed) 未安装。请先安装 ZED SDK 及其 Python binding。\n"
                "  Windows: 运行 C:\\Program Files (x86)\\ZED SDK\\get_python_api.py\n"
                "  Linux:   https://www.stereolabs.com/docs/installation/linux/"
            )

        try:
            import cv2
            _CV2_AVAILABLE = True
        except ImportError:
            _CV2_AVAILABLE = False
            from PIL import Image
            import io

        # --- 3. 逐 episode 合并 ---
        outputs = []
        merge_report = {
            "episodes": {},
            "total_frames": 0,
            "total_warnings": 0,
            "total_severe_warnings": 0,
        }

        for ep_num in paired_episodes:
            hdf5_meta = hdf5_files[ep_num]
            svo2_meta = svo2_files[ep_num]
            source_key = hdf5_meta.get("sourceKey", "robot_hdf5")

            # 构建本地文件路径（input_dir/{sourceKey}/{filename}）
            hdf5_local = os.path.join(input_dir, source_key, hdf5_meta["originalFilename"])
            # SVO2 的 sourceKey 可能不同，需要找对应的
            svo2_source_key = svo2_meta.get("sourceKey", "camera_svo2")
            svo2_local = os.path.join(input_dir, svo2_source_key, svo2_meta["originalFilename"])

            if not os.path.exists(hdf5_local):
                print(f"[G1_MERGE] [WARN] HDF5 文件不存在: {hdf5_local}，跳过 episode_{ep_num}")
                continue
            if not os.path.exists(svo2_local):
                print(f"[G1_MERGE] [WARN] SVO2 文件不存在: {svo2_local}，跳过 episode_{ep_num}")
                continue

            output_filename = f"episode_{ep_num}.hdf5"
            output_path = os.path.join(output_dir, output_filename)

            print(f"[G1_MERGE] 处理 episode_{ep_num}: "
                  f"HDF5={os.path.basename(hdf5_local)}, SVO2={os.path.basename(svo2_local)}")

            ep_report = self._merge_episode(
                hdf5_local, svo2_local, output_path, ep_num,
                sl, _CV2_AVAILABLE
            )

            if ep_report is None:
                print(f"[G1_MERGE] [ERR] episode_{ep_num} 合并失败，跳过")
                continue

            merge_report["episodes"][str(ep_num)] = {
                "frame_count": ep_report["frame_count"],
                "warning_count": ep_report["warning_count"],
                "severe_warning_count": ep_report["severe_warning_count"],
                "avg_time_diff_ms": ep_report["avg_time_diff_ms"],
                "max_time_diff_ms": ep_report["max_time_diff_ms"],
            }
            merge_report["total_frames"] += ep_report["frame_count"]
            merge_report["total_warnings"] += ep_report["warning_count"]
            merge_report["total_severe_warnings"] += ep_report["severe_warning_count"]

            file_size = os.path.getsize(output_path)
            outputs.append({
                "sourceKey": source_key,
                "fileName": output_filename,
                "localPath": output_path,
                "assetType": "G1_MERGED_HDF5",
                "contentType": "application/x-hdf5",
            })
            print(f"[G1_MERGE] episode_{ep_num} 完成: {ep_report['frame_count']} 帧, "
                  f"{file_size / 1024 / 1024:.1f} MB")

        # --- 4. 保存合并报告 ---
        report_path = os.path.join(output_dir, "merge_report.json")
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(merge_report, f, ensure_ascii=False, indent=2)
        print(f"[G1_MERGE] 报告: {report_path}")
        print(f"[G1_MERGE] 总计: {merge_report['total_frames']} 帧, "
              f"{len(paired_episodes)} episodes, "
              f"warnings={merge_report['total_warnings']}, "
              f"severe={merge_report['total_severe_warnings']}")

        return outputs

    # ------------------------------------------------------------------
    # 内部方法
    # ------------------------------------------------------------------

    def _merge_episode(self, hdf5_path: str, svo2_path: str,
                       output_path: str, ep_num: int,
                       sl, cv2_available: bool) -> Optional[Dict]:
        """合并单个 episode"""

        # 读取 SVO2 中的全部图像和时间戳
        camera_data = self._read_svo2(svo2_path, sl, cv2_available)
        if camera_data is None or len(camera_data["timestamps_ns"]) == 0:
            print(f"[G1_MERGE] episode_{ep_num}: SVO2 无有效帧")
            return None

        with h5py.File(hdf5_path, 'r') as robot_f:
            if "timestamp" not in robot_f:
                print(f"[G1_MERGE] episode_{ep_num}: HDF5 缺少 timestamp 字段")
                return None

            robot_timestamps = robot_f["timestamp"][:]
            num_frames = len(robot_timestamps)

            with h5py.File(output_path, 'w') as output_f:
                # 复制原始数据
                for key in robot_f.keys():
                    output_f.create_dataset(key, data=robot_f[key][:], compression="gzip")

                # 计算 delta EEF
                if 'observation_eef_state' in robot_f and 'action_eef' in robot_f:
                    obs_delta = compute_delta_eef(robot_f['observation_eef_state'][:])
                    action_delta = compute_delta_eef(robot_f['action_eef'][:])
                    ds = output_f.create_dataset('obs_delta_eef', data=obs_delta, compression='gzip')
                    ds.attrs['description'] = ('Delta EEF from observation: '
                                               '[left: dx,dy,dz,roll,pitch,yaw, right: dx,dy,dz,roll,pitch,yaw]')
                    ds.attrs['units'] = 'meters for xyz, radians for rpy'
                    ds = output_f.create_dataset('action_delta_eef', data=action_delta, compression='gzip')
                    ds.attrs['description'] = ('Delta EEF from action: '
                                               '[left: dx,dy,dz,roll,pitch,yaw, right: dx,dy,dz,roll,pitch,yaw]')
                    ds.attrs['units'] = 'meters for xyz, radians for rpy'

                # 计算 delta height
                if 'teleop_base_height_command' in robot_f:
                    height_cmd = robot_f['teleop_base_height_command'][:]
                    delta_height = compute_delta_height(height_cmd)
                    ds = output_f.create_dataset('delta_height', data=delta_height, compression='gzip')
                    ds.attrs['description'] = 'Delta height from teleop_base_height_command'
                    ds.attrs['units'] = 'meters'

                # 创建图像数据集（variable-length JPEG bytes）
                dt = h5py.special_dtype(vlen=np.uint8)
                left_ds = output_f.create_dataset("observation_image_left", shape=(num_frames,), dtype=dt)
                right_ds = output_f.create_dataset("observation_image_right", shape=(num_frames,), dtype=dt)

                # 时间戳对齐
                cam_ts_ns = camera_data["timestamps_ns"]
                robot_ts_ns = (robot_timestamps * 1e9).astype(np.int64)

                indices = np.searchsorted(cam_ts_ns, robot_ts_ns)
                indices = np.clip(indices, 0, len(cam_ts_ns) - 1)
                prev_indices = np.maximum(indices - 1, 0)

                curr_diffs = np.abs(cam_ts_ns[indices] - robot_ts_ns)
                prev_diffs = np.abs(cam_ts_ns[prev_indices] - robot_ts_ns)
                better_indices = np.where(curr_diffs < prev_diffs, indices, prev_indices)

                time_diffs_ms = np.abs(cam_ts_ns[better_indices] - robot_ts_ns) / 1e6
                matched_cam_ts = cam_ts_ns[better_indices]

                # 统计告警
                severe_mask = time_diffs_ms > self.SEVERE_THRESHOLD_MS
                warning_mask = (time_diffs_ms > self.WARNING_THRESHOLD_MS) & (~severe_mask)
                severe_count = int(np.sum(severe_mask))
                warning_count = int(np.sum(warning_mask))

                # 写入图像
                cam_left = camera_data["images_left"]
                cam_right = camera_data["images_right"]
                for i in range(num_frames):
                    idx = better_indices[i]
                    left_ds[i] = cam_left[idx]
                    right_ds[i] = cam_right[idx]

                output_f.create_dataset("camera_timestamp", data=matched_cam_ts, compression="gzip")
                output_f.create_dataset("timestamp_diff_ms", data=time_diffs_ms, compression="gzip")

        return {
            "frame_count": num_frames,
            "warning_count": warning_count,
            "severe_warning_count": severe_count,
            "avg_time_diff_ms": float(np.mean(time_diffs_ms)),
            "max_time_diff_ms": float(np.max(time_diffs_ms)),
        }

    def _read_svo2(self, svo2_path: str, sl, cv2_available: bool) -> Optional[Dict]:
        """读取 SVO2 文件中的所有图像帧（JPEG 压缩）"""
        zed = sl.Camera()
        init_params = sl.InitParameters()
        init_params.set_from_svo_file(str(svo2_path))
        init_params.svo_real_time_mode = False
        init_params.coordinate_units = sl.UNIT.METER

        err = zed.open(init_params)
        if err != sl.ERROR_CODE.SUCCESS:
            print(f"[G1_MERGE] 无法打开 SVO2: {svo2_path}, 错误码: {err}")
            return None

        nb_frames = zed.get_svo_number_of_frames()
        timestamps_ns = []
        images_left = []
        images_right = []

        left_image = sl.Mat()
        right_image = sl.Mat()
        zed.set_svo_position(0)

        frame_count = 0
        while frame_count < nb_frames:
            if zed.grab() == sl.ERROR_CODE.SUCCESS:
                timestamp = zed.get_timestamp(sl.TIME_REFERENCE.IMAGE)
                timestamps_ns.append(timestamp.get_nanoseconds())

                zed.retrieve_image(left_image, sl.VIEW.LEFT, sl.MEM.CPU)
                zed.retrieve_image(right_image, sl.VIEW.RIGHT, sl.MEM.CPU)

                images_left.append(self._mat_to_jpeg(left_image, cv2_available))
                images_right.append(self._mat_to_jpeg(right_image, cv2_available))
                frame_count += 1
            else:
                break

        zed.close()

        if len(timestamps_ns) == 0:
            return None

        return {
            "timestamps_ns": np.array(timestamps_ns, dtype=np.int64),
            "images_left": images_left,
            "images_right": images_right,
        }

    @staticmethod
    def _mat_to_jpeg(mat, cv2_available: bool) -> np.ndarray:
        """ZED Mat → JPEG byte array"""
        try:
            image_array = mat.numpy()
        except Exception:
            image_array = mat.get_data()

        channels = mat.get_channels()

        if cv2_available:
            import cv2
            if channels == 4:
                image_rgb = cv2.cvtColor(image_array[:, :, :3], cv2.COLOR_BGR2RGB)
            elif channels == 3:
                image_rgb = cv2.cvtColor(image_array, cv2.COLOR_BGR2RGB)
            elif channels == 1:
                image_rgb = cv2.cvtColor(image_array.squeeze(), cv2.COLOR_GRAY2RGB)
            else:
                image_rgb = image_array
            encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 95]
            _, jpeg_bytes = cv2.imencode('.jpg', image_rgb, encode_param)
            return np.frombuffer(jpeg_bytes, dtype=np.uint8)
        else:
            from PIL import Image
            import io
            if channels == 4:
                image_rgb = Image.fromarray(image_array[:, :, [2, 1, 0]], 'RGB')
            elif channels == 3:
                image_rgb = Image.fromarray(image_array[:, :, [2, 1, 0]], 'RGB')
            elif channels == 1:
                image_rgb = Image.fromarray(image_array.squeeze(), 'L').convert('RGB')
            else:
                image_rgb = Image.fromarray(image_array)
            buf = io.BytesIO()
            image_rgb.save(buf, format='JPEG', quality=95)
            return np.frombuffer(buf.getvalue(), dtype=np.uint8)
