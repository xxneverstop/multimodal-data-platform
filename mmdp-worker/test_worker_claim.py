import unittest
from unittest.mock import Mock, patch

import main


class WorkerClaimTest(unittest.TestCase):
    @patch("main.requests.post")
    def test_claim_reports_supported_pipeline_ids(self, post: Mock):
        response = Mock()
        response.json.return_value = {"success": True, "data": None}
        post.return_value = response

        main.claim_job()

        payload = post.call_args.kwargs["json"]
        self.assertEqual(main.WORKER_TYPE, payload["workerType"])
        self.assertEqual(set(main.PIPELINES), set(payload["pipelineIds"]))
        self.assertIn("G1_GENERATE_PLAYBACK", payload["pipelineIds"])


if __name__ == "__main__":
    unittest.main()
