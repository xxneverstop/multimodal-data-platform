"""
mmdp-worker 配置
所有敏感信息通过环境变量注入，与后端共享同一套 OSS 环境变量。
"""
import os

# 后端地址
BACKEND_URL = os.getenv("MMDP_BACKEND_URL", "http://localhost:19021")

# OSS 配置（与后端 application.yml 共享同一套环境变量）
OSS_ENDPOINT = os.getenv("MMDP_OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com")
OSS_ACCESS_KEY_ID = os.getenv("MMDP_OSS_ACCESS_KEY_ID", "")
OSS_ACCESS_KEY_SECRET = os.getenv("MMDP_OSS_ACCESS_KEY_SECRET", "")
OSS_BUCKET = os.getenv("MMDP_OSS_BUCKET", "mmdp-test")
OSS_REGION = os.getenv("MMDP_OSS_REGION", "cn-hangzhou")

# Worker 本地临时目录
WORK_DIR = os.getenv("MMDP_WORKER_WORK_DIR", "/tmp/mmdp-worker")

# 轮询间隔（秒）
POLL_INTERVAL = int(os.getenv("MMDP_WORKER_POLL_INTERVAL", "5"))

# OSS 产物路径前缀
OUTPUT_PREFIX = "processed/sessions/{session_id}/jobs/{job_id}"


def validate():
    """启动前校验必要配置"""
    missing = []
    for key in ("OSS_ENDPOINT", "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET", "OSS_BUCKET"):
        if not globals()[key]:
            missing.append(f"MMDP_{key}")
    if missing:
        raise RuntimeError(f"缺少必要环境变量: {', '.join(missing)}")
