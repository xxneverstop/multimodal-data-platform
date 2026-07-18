FROM python:3.10-slim

# 切换为阿里云 Debian 镜像源（加速 apt-get）
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources && \
    apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 先安装 torch（CPU 版，体积最小；单独安装以利用 Docker 层缓存）
RUN pip install --no-cache-dir \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    torch --extra-index-url https://download.pytorch.org/whl/cpu

# 安装其他 Python 依赖（使用清华 pip 镜像）
COPY src/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# 复制 Worker 源码
COPY src/ /app/

# SMPL-H 人体模型文件（用于物理指标计算 V2）
COPY smpl_models/ /app/smpl_models/

# 确保工作目录存在
RUN mkdir -p /tmp/mmdp-worker

ENTRYPOINT ["python", "main.py"]
