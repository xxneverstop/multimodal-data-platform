FROM python:3.10-slim-bookworm

# CPU Worker 运行库 + evdev 编译工具链
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg libgl1 libglib2.0-0 build-essential && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# PyTorch 2.10 wheel 需要新版 pip 正确处理包名元数据
RUN python -m pip install --no-cache-dir \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    --upgrade pip setuptools wheel

# 固定已验证的 CPU 版 PyTorch，避免 lerobot 拉取 CUDA 版或升级到不兼容版本
RUN python -m pip install --no-cache-dir --default-timeout 600 \
    torch==2.10.0 torchvision==0.25.0 \
    --index-url https://download.pytorch.org/whl/cpu

# 其他依赖；lerobot 在 Linux 上会编译 evdev
COPY src/requirements.txt /app/requirements.txt
RUN python -m pip install --no-cache-dir --default-timeout 600 \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    -r requirements.txt && \
    python -c "import cv2, evdev, lerobot, torch; assert torch.version.cuda is None"

# 卸载编译工具，缩回镜像体积
RUN apt-get remove -y build-essential && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Worker 源码（经常改动，放最后）
COPY src/ /app/
RUN mkdir -p /tmp/mmdp-worker

ENV MMDP_WORKER_TYPE=CPU
ENTRYPOINT ["python", "main.py"]
