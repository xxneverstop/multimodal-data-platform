import os
import shutil
import unittest

import cv2
import h5py
import numpy as np

from pipelines.g1_generate_playback import G1GeneratePlaybackPipeline


@unittest.skipUnless(shutil.which("ffmpeg"), "需要 ffmpeg")
class G1GeneratePlaybackPipelineTest(unittest.TestCase):

    def setUp(self):
        self.work_dir = os.path.join(os.getcwd(), f".test-g1-playback-{os.getpid()}")
        self.source_key = "robot_hdf5"
        self.input_dir = os.path.join(self.work_dir, "input")
        self.source_dir = os.path.join(self.input_dir, self.source_key)
        self.output_dir = os.path.join(self.work_dir, "output")
        os.makedirs(self.source_dir)

    def tearDown(self):
        shutil.rmtree(self.work_dir, ignore_errors=True)

    def test_generates_complete_stereo_mp4_from_merged_hdf5(self):
        filename = "episode_0.hdf5"
        self._create_hdf5(filename, left_values=(30, 120, 220), right_values=(40, 130, 230))

        outputs = G1GeneratePlaybackPipeline().execute(
            self.input_dir,
            self.output_dir,
            [self._input_file(filename)],
        )

        self.assertEqual(2, len(outputs))
        self.assertEqual(
            ["camera_svo2_left", "camera_svo2_right"],
            [output["sourceKey"] for output in outputs],
        )
        self.assertEqual(
            ["g1_playback_left.mp4", "g1_playback_right.mp4"],
            [output["fileName"] for output in outputs],
        )
        for output in outputs:
            self.assertEqual("RGB_VIDEO_MP4", output["assetType"])
            self.assertEqual("video/mp4", output["contentType"])
            self.assertGreater(os.path.getsize(output["localPath"]), 0)
            capture = cv2.VideoCapture(output["localPath"])
            self.assertEqual(3, int(capture.get(cv2.CAP_PROP_FRAME_COUNT)))
            self.assertEqual(20, int(round(capture.get(cv2.CAP_PROP_FPS))))
            self.assertEqual(32, int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)))
            self.assertEqual(24, int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)))
            capture.release()

        left_first = self._read_frame(outputs[0]["localPath"], 0)
        right_first = self._read_frame(outputs[1]["localPath"], 0)
        self.assertGreater(abs(float(left_first.mean()) - float(right_first.mean())), 5)

    def test_rejects_mismatched_stereo_frames_without_publishing_output(self):
        filename = "episode_0.hdf5"
        self._create_hdf5(filename, left_values=(30, 120), right_values=(40,))

        with self.assertRaisesRegex(RuntimeError, "双目帧数不一致"):
            G1GeneratePlaybackPipeline().execute(
                self.input_dir,
                self.output_dir,
                [self._input_file(filename)],
            )

        self.assertFalse(os.path.exists(os.path.join(self.output_dir, "g1_playback_left.mp4")))
        self.assertFalse(os.path.exists(os.path.join(self.output_dir, "g1_playback_right.mp4")))

    def test_rejects_hdf5_without_right_eye_dataset(self):
        filename = "episode_0.hdf5"
        self._create_hdf5(filename, left_values=(30,), right_values=None)

        with self.assertRaisesRegex(RuntimeError, "observation_image_right"):
            G1GeneratePlaybackPipeline().execute(
                self.input_dir,
                self.output_dir,
                [self._input_file(filename)],
            )

    def test_natural_episode_order(self):
        filenames = ["episode_10.hdf5", "episode_2.hdf5", "episode_1.hdf5"]
        filenames.sort(key=G1GeneratePlaybackPipeline._natural_key)
        self.assertEqual(
            ["episode_1.hdf5", "episode_2.hdf5", "episode_10.hdf5"],
            filenames,
        )

    def test_concatenates_multiple_episodes_in_natural_order(self):
        episodes = (
            ("episode_10.hdf5", 210, 220),
            ("episode_2.hdf5", 110, 120),
            ("episode_1.hdf5", 10, 20),
        )
        for filename, left_value, right_value in episodes:
            self._create_hdf5(filename, (left_value,), (right_value,))

        outputs = G1GeneratePlaybackPipeline().execute(
            self.input_dir,
            self.output_dir,
            [self._input_file(filename) for filename, _, _ in episodes],
        )

        left_means = [
            float(self._read_frame(outputs[0]["localPath"], index).mean())
            for index in range(3)
        ]
        right_means = [
            float(self._read_frame(outputs[1]["localPath"], index).mean())
            for index in range(3)
        ]
        self.assertTrue(left_means[0] < left_means[1] < left_means[2])
        self.assertTrue(right_means[0] < right_means[1] < right_means[2])

    def _input_file(self, filename):
        return {
            "sourceKey": self.source_key,
            "assetType": "G1_MERGED_HDF5",
            "originalFilename": filename,
        }

    def _create_hdf5(self, filename, left_values, right_values):
        hdf5_path = os.path.join(self.source_dir, filename)
        with h5py.File(hdf5_path, "w") as hdf5_file:
            self._create_dataset(hdf5_file, "observation_image_left", left_values)
            if right_values is not None:
                self._create_dataset(hdf5_file, "observation_image_right", right_values)

    def _create_dataset(self, hdf5_file, name, values):
        frames = []
        for value in values:
            image = np.full((24, 32, 3), value, dtype=np.uint8)
            ok, encoded = cv2.imencode(".jpg", image)
            self.assertTrue(ok)
            frames.append(encoded)
        dtype = h5py.vlen_dtype(np.dtype("uint8"))
        dataset = hdf5_file.create_dataset(name, (len(frames),), dtype=dtype)
        for index, frame in enumerate(frames):
            dataset[index] = frame

    def _read_frame(self, video_path, frame_index):
        capture = cv2.VideoCapture(video_path)
        capture.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
        ok, frame = capture.read()
        capture.release()
        self.assertTrue(ok)
        return frame


if __name__ == "__main__":
    unittest.main()
