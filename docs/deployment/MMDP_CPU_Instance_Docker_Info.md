# MMDP CPU 实例环境与容器运行信息

> 记录日期：2026-07-31  
> 节点用途：部署 MMDP 后端服务、MySQL、Nginx 和 CPU Worker，同时运行 RustDesk 服务。

## 1. 阿里云 CPU ECS 基本信息

| 项目 | 当前配置 |
|---|---|
| 实例名称 | `launch-advisor-20260601` |
| 实例 ID | `i-bp1hq2o77hv60nmidyd5` |
| 运行状态 | 运行中 |
| 付费类型 | 包年包月 |
| 到期时间 | `2027-06-01 23:59:59` |
| 创建时间 | `2026-06-01 13:40:00` |
| 地域 | 华东 1（杭州） |
| 可用区 | 杭州可用区 K |
| 实例规格 | `ecs.u2a-c1m2.xlarge` |
| CPU | 4 vCPU |
| 内存 | 8 GiB |
| 私网 IP | `172.31.133.185` |
| 公网 IP | `8.154.36.87` |
| 公网带宽 | 峰值 5 Mbps，按固定带宽计费 |
| 操作系统 | Ubuntu 22.04 64 位 |
| 系统盘 | ESSD Entry 云盘 100 GiB |
| 自动续费 | 手动续费 |

## 2. 宿主机系统信息

登录欢迎信息：

```text
Ubuntu 22.04.5 LTS
Linux 5.15.0-179-generic x86_64
```

终端主机名：

```text
iZbp1hq2o77hv60nmidyd5Z
```

宿主机内存快照（`free -h`）：

| 指标 | 数值 |
|---|---:|
| 物理内存总量 | 7.1 GiB |
| 已使用 | 1.5 GiB |
| 空闲 | 423 MiB |
| 共享内存 | 3.0 MiB |
| Buffer/Cache | 5.1 GiB |
| 可用内存 | 5.3 GiB |
| Swap 总量 | 0 B |
| Swap 已使用 | 0 B |

> 当前宿主机未配置 Swap。`free` 显示的物理内存总量低于实例标称的 8 GiB，属于操作系统可见容量与标称容量的口径差异。

## 3. Docker 容器部署清单

| 容器名称 | 镜像 | 启动命令 | 状态 | 对外端口 | 主要职责 |
|---|---|---|---|---|---|
| `mmdp-nginx` | `nginx:1.27-alpine` | `/docker-entrypoint.…` | Up 4 weeks | `80:80`、`443:443`（IPv4/IPv6） | Web 入口、HTTPS 和反向代理 |
| `mmdp-worker` | `mmdp-worker:latest` | `python main.py` | Up 4 weeks | 无 | CPU 数据处理 Worker |
| `mmdp-backend` | `mmdp-backend:latest` | `java -jar mmdp-back…` | Up 4 weeks | 容器内 `19021/tcp` | Spring Boot 后端服务 |
| `mmdp-mysql` | `mysql:8.4.2` | `/docker-entrypoint.s…` | Up 4 weeks（healthy） | `13306:3306`（IPv4/IPv6）、容器内 `33060/tcp` | MMDP MySQL 数据库 |
| `hbbs` | `rustdesk/rustdesk-server:latest` | `hbbs` | Up 6 weeks | 无 | RustDesk ID/信令服务 |
| `hbbr` | `rustdesk/rustdesk-server:latest` | `hbbr` | Up 6 weeks | 无 | RustDesk 中继服务 |

部署关系概览：

```text
CPU ECS
├── mmdp-nginx
│   ├── 80/tcp
│   └── 443/tcp
├── mmdp-backend
│   └── 19021/tcp（仅容器网络）
├── mmdp-worker
├── mmdp-mysql
│   └── 13306/tcp → 3306/tcp
├── hbbs
└── hbbr
```

## 4. 容器资源使用快照

以下数据来自记录时的 `docker stats`，属于瞬时运行状态，并非容器资源上限配置。

| 容器名称 | CPU | 内存使用 / 宿主机可见上限 | 内存占比 | 网络 I/O | 块设备 I/O | PIDs |
|---|---:|---:|---:|---:|---:|---:|
| `mmdp-nginx` | 0.00% | 2.859 MiB / 7.096 GiB | 0.04% | 26.7 MB / 29.3 MB | 221 kB / 12.3 kB | 2 |
| `mmdp-worker` | 0.00% | 30.67 MiB / 7.096 GiB | 0.42% | 604 MB / 337 MB | 20.5 kB / 247 MB | 1 |
| `mmdp-backend` | 0.03% | 542.4 MiB / 7.096 GiB | 7.46% | 1.4 GB / 906 MB | 12.3 kB / 1.14 GB | 42 |
| `mmdp-mysql` | 0.13% | 532.5 MiB / 7.096 GiB | 7.33% | 1.35 GB / 3.64 GB | 43 MB / 241 MB | 56 |
| `hbbs` | 0.02% | 2.449 MiB / 7.096 GiB | 0.03% | 0 B / 0 B | 0 B / 2.86 MB | 13 |
| `hbbr` | 0.00% | 1.324 MiB / 7.096 GiB | 0.02% | 0 B / 0 B | 0 B / 0 B | 7 |

快照摘要：

- 6 个容器均处于运行状态；
- MySQL 健康检查状态为 `healthy`；
- 容器合计使用内存约 1.09 GiB；
- 宿主机当前可用内存约 5.3 GiB；
- 当前 CPU 使用率整体较低；
- 内存占用主要来自 `mmdp-backend` 和 `mmdp-mysql`；
- 所有容器共享约 7.096 GiB 的宿主机可见内存，快照中未显示单独设置的容器内存限制。

## 5. 当前部署摘要

```text
实例：
ecs.u2a-c1m2.xlarge
4 vCPU / 8 GiB RAM
ESSD Entry 100 GiB
公网带宽 5 Mbps

系统：
Ubuntu 22.04.5 LTS
Linux 5.15.0-179-generic x86_64
无 Swap

MMDP：
Nginx 1.27 Alpine
MMDP Backend
MMDP CPU Worker
MySQL 8.4.2

附加服务：
RustDesk hbbs
RustDesk hbbr
```
