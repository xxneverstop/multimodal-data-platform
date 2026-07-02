FROM python:3.10-slim

# 安装系统依赖（ffmpeg 供视频处理 Pipeline 使用）
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 安装 Python 依赖
COPY src/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 复制 Worker 源码
COPY src/ /app/

# 确保工作目录存在
RUN mkdir -p /tmp/mmdp-worker

ENTRYPOINT ["python", "main.py"]
