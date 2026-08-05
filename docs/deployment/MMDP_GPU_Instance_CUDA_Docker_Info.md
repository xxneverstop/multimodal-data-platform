# MMDP GPU 实例环境信息

> 记录日期：2026-07-23

## 1. 阿里云 GPU ECS 基本信息

| 项目 | 当前配置 |
|---|---|
| 实例名称 | `launch-advisor-20260723` |
| 实例 ID | `i-bp1fanxpewrb1b806gsw` |
| 付费类型 | 按量付费 |
| 地域 | 华东 1（杭州） |
| 可用区 | 杭州可用区 K |
| 实例规格 | `ecs.gn6i-c4g1.xlarge` |
| CPU | 4 vCPU |
| 内存 | 15 GiB |
| GPU | NVIDIA Tesla T4 |
| GPU 数量 | 1 张 |
| GPU 显存 | 15360 MiB，约 15 GiB |
| 私网 IP | `172.31.133.186` |
| 当前公网 IP | `8.154.24.218` |
| 公网带宽 | 按使用流量，峰值 5 Mbps |
| 系统盘 | ESSD 云盘 100 GiB，PL0 |
| 操作系统 | Ubuntu 22.04 64 位 |
| 镜像 | Ubuntu 22.04，预装 NVIDIA GPU 驱动和 CUDA |
| 网络 | 与原 CPU ECS 位于同一 VPC/交换网络 |

> 公网 IP 在节省停机后可能发生变化，业务通信应使用私网 IP。

---

## 2. NVIDIA GPU 与驱动信息

执行命令：

```bash
nvidia-smi
```

当前结果：

```text
GPU Model:           Tesla T4
Driver Version:      580.126.09
CUDA Version:        13.0
GPU Memory:          15360 MiB
Persistence Mode:    On
Compute Mode:        Default
```

当前空闲状态：

```text
GPU Utilization:     0%
GPU Memory Usage:    0 MiB / 15360 MiB
Temperature:         37°C
Power Usage:         10 W / 70 W
Running Processes:   None
```

说明：

- `nvidia-smi` 中的 `CUDA Version: 13.0` 表示当前 NVIDIA 驱动最高支持 CUDA 13.0；
- 它不代表宿主机安装的 CUDA Toolkit 一定是 13.0。

---

## 3. 宿主机 CUDA Toolkit 信息

执行命令：

```bash
nvcc --version
```

当前结果：

```text
CUDA Toolkit:        12.8
NVCC Version:        V12.8.93
Build:               cuda_12.8.r12.8/compiler.35583870_0
```

因此当前宿主机环境为：

```text
Ubuntu 22.04
NVIDIA Driver 580.126.09
CUDA Driver Capability 13.0
CUDA Toolkit 12.8
```

这是正常组合：

- 驱动支持 CUDA 13.0；
- 宿主机默认 CUDA Toolkit 为 12.8；
- 后续可在 Docker 容器中运行 CUDA 13 Runtime；
- 当前不需要卸载宿主机 CUDA 12.8。

---

## 4. Docker 信息

执行命令：

```bash
docker --version
```

当前结果：

```text
Docker Version: 29.5.1
Docker Build:   2518b52
```

后续还需要确认 NVIDIA Container Toolkit：

```bash
nvidia-ctk --version
docker info | grep -i runtime
```

---

## 5. 推荐的 CUDA Docker 运行方式

推荐保持宿主机环境不变：

```text
宿主机：
- Ubuntu 22.04
- NVIDIA Driver 580.126.09
- CUDA Toolkit 12.8
- Docker 29.5.1
```

GPU Worker 容器使用：

```text
容器：
- Ubuntu 22.04 Runtime
- CUDA 13
- Linux 版 ZED SDK
- Python Worker
```

结构如下：

```text
GPU ECS 宿主机
├── Ubuntu 22.04
├── NVIDIA Driver 580.126.09
├── CUDA Toolkit 12.8
├── Docker 29.5.1
└── NVIDIA Container Toolkit
    └── GPU Worker Docker
        ├── CUDA 13 Runtime
        ├── ZED SDK
        ├── pyzed
        └── MMDP GPU Worker
```

---

## 6. CUDA 13 Docker 验证命令

运行：

```bash
docker run --rm --gpus all \
  nvidia/cuda:13.0.0-base-ubuntu22.04 \
  nvidia-smi
```

正常情况下，容器中应识别到：

```text
GPU:             Tesla T4
Driver Version:  580.126.09
CUDA Version:    13.0
```

如果出现 GPU Runtime 相关错误，可执行：

```bash
nvidia-ctk --version

sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

然后重新执行 CUDA 13 Docker 验证命令。

---

## 7. 最终版本摘要

```text
实例规格：
ecs.gn6i-c4g1.xlarge

硬件：
4 vCPU
15 GiB RAM
1 × NVIDIA Tesla T4
15360 MiB GPU Memory

操作系统：
Ubuntu 22.04 64 位

NVIDIA Driver：
580.126.09

nvidia-smi 支持的 CUDA：
13.0

宿主机 CUDA Toolkit：
12.8
NVCC 12.8.93

Docker：
29.5.1

推荐 GPU Worker 容器：
Ubuntu 22.04 + CUDA 13 + Linux ZED SDK
```
