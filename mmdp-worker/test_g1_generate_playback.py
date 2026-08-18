import os
import shutil
import unittest

import cv2
import h5py
import numpy as np

from pipelines.g1_generate_playback import G1GeneratePlaybackPipeline


@unittest.skipUnless(shutil.which("ffmpeg"), "需要 ffmpeg")
class G1GeneratePlaybackPipelineTest(unittest.TestCase):

    def test_generates_single_mp4_from_merged_hdf5(self):
        work_dir = os.path.join(os.getcwd(), f".test-g1-playback-{os.getpid()}")
        try:
            source_key = "robot_hdf5"
            input_dir = os.path.join(work_dir, "input")
            source_dir = os.path.join(input_dir, source_key)
            output_dir = os.path.join(work_dir, "output")
            os.makedirs(source_dir)

            filename = "episode_0.hdf5"
            hdf5_path = os.path.join(source_dir, filename)
            jpeg_frames = []
            for value in (30, 120, 220):
                image = np.full((24, 32, 3), value, dtype=np.uint8)
                ok, encoded = cv2.imencode(".jpg", image)
                self.assertTrue(ok)
                jpeg_frames.append(encoded)

            with h5py.File(hdf5_path, "w") as hdf5_file:
                dtype = h5py.vlen_dtype(np.dtype("uint8"))
                dataset = hdf5_file.create_dataset("observation_image_left", (len(jpeg_frames),), dtype=dtype)
                for index, frame in enumerate(jpeg_frames):
                    dataset[index] = frame

            outputs = G1GeneratePlaybackPipeline().execute(
                input_dir,
                output_dir,
                [{
                    "sourceKey": source_key,
                    "assetType": "G1_MERGED_HDF5",
                    "originalFilename": filename,
                }],
            )

            self.assertEqual(1, len(outputs))
            self.assertEqual("RGB_VIDEO_MP4", outputs[0]["assetType"])
            self.assertEqual("camera_svo2", outputs[0]["sourceKey"])
            self.assertGreater(os.path.getsize(outputs[0]["localPath"]), 0)
        finally:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
