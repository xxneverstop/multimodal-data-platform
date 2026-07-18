> 保存于 2026-07-02 14:40

# 从零开始理解 CI/CD：以 MMDP 项目为例

## 你手动部署时的烦恼

想象一下你现在的日常：改完代码，打开 `copy-deploy.bat`，等 Maven 编译，等 npm 打包，然后打开终端，`scp` 上传到服务器，再 SSH 上去敲 `docker compose up -d --build`。如果只改了一行代码，这一套流程也要走五分钟。如果前端改了一点样式、后端修了一个 bug、Worker 加了一个 Pipeline，你就得重复三次。

更糟糕的是，某天你改了一个配置项，忘了同步到服务器，线上报错了。或者你不小心把没测试过的代码推到了服务器，挂了半小时才发现。

这些问题不是你的错。靠人肉操作来保证部署不出错，就像靠记忆来记一百个电话号码——早晚会漏。这就是为什么我们需要 CI/CD。

## CI/CD 到底是什么

CI/CD 是两个词的组合，但别被缩写吓到。

**CI（持续集成）** 做的事情很简单：每次你把代码推送到 Git 仓库，它就自动帮你编译、测试、打包，然后告诉你结果。你不需要在本地手动跑 `mvn package`、`npm run build` 了，这些事都由一台专门的机器（或者云端服务）代劳。

**CD（持续交付/部署）** 是 CI 的延续：CI 把代码变成了可运行的制品（比如 Docker 镜像），CD 就负责把这个制品推送到服务器上，让用户真正用上。

用一句话概括：**CI/CD 就是把"代码写好了，怎么让它跑到服务器上"这件事自动化了。** 你只管 push 代码，剩下的编译、测试、打包、部署全自动完成。

这里有几个你可能会遇到的术语，先解释一下：

- **Pipeline（流水线）** ：你可以把它想象成工厂的流水线。代码从一头进去，经过若干道工序（编译 → 测试 → 打包 → 部署），最终变成服务器上跑着的服务。`.gitlab-ci.yml` 文件就是这条流水线的设计图纸。

- **Runner（运行器）** ：执行流水线的工人。它是一台机器（或容器），GitLab 把任务分配给 Runner，Runner 干活并汇报结果。你可以用 GitLab 提供的免费 Runner，也可以在自己的服务器上装一个。

- **Artifact（制品）** ：流水线中间产出的东西。比如 Maven 编译出的 JAR 包、Vite 打包出的 `dist/` 目录。后面的步骤可以拿这些制品继续干活。

- **Registry（镜像仓库）** ：存 Docker 镜像的地方。你可以把它类比为 Docker 版的 GitHub——你把镜像推上去，服务器从那里拉下来。阿里云的 ACR（容器镜像服务）就是一个 Registry。

## 为什么 MMDP 项目特别适合上 CI/CD

你的项目有三个组件（前端、后端、Worker），每个都有独立的构建流程：

```
后端：  mvn package       → JAR 包
前端：  npm run build     → dist/ 目录
Worker：Python 源码       → Docker 镜像
```

这三个组件的构建方式各不相同，每次手动操作都容易遗漏。而且你的项目源码托管在 GitLab（git.xmu.edu.cn），部署在阿里云 ECS 上，这两个基础设施天然就是 CI/CD 的土壤——GitLab 自带 CI/CD 引擎，阿里云有镜像仓库可用。

另外，你之前已经做了 Docker Compose 部署方案，这给 CI/CD 铺平了路。Docker Compose 已经把服务启动方式标准化了，CI/CD 只需要把"构建 → 推镜像 → 服务器拉取"这个环节串起来。

## 先想清楚：你需不需要 K8s？

很多人一听到"自动化部署"就想到 K8s（Kubernetes）。K8s 确实是容器编排的工业标准，但它不是 CI/CD 的前提条件。

打个比方：Docker Compose 像是你开一家小店，自己管理库存和收银。K8s 像是你开了连锁超市，需要中央仓储、自动补货系统、客流分析。小店当然没必要上连锁超市的 IT 系统——不是它不好，是你的规模还没到需要它的程度。

MMDP 项目目前 4 个容器、不到 50 个用户、一台 ECS 就跑得很稳。上 K8s 意味着你要维护至少 2-3 台服务器、学一堆新概念（Pod、Service、Ingress、ConfigMap、PersistentVolume……）、每个月多花几千块。先把 CI/CD 做起来，等将来真的需要多副本、自动扩缩容、滚动更新零停机的时候再迁 K8s，你的经验积累也够了。

所以本文的方案基于 **GitLab CI + 阿里云 ACR + Docker Compose**，在现有架构上改动最小、风险最低。

## 整体思路：让代码自己跑到服务器上

我们的目标是：你在本地 `git push`，几分钟后，新代码就跑到了服务器上。中间发生了什么？

```
你在本地 git push
      │
      ▼
GitLab 收到推送，触发 CI Pipeline
      │
      ┌─────────┼──────────┐
      ▼         ▼          ▼
  后端编译   前端编译   Worker构建
  mvn pkg   npm build  docker build
      │         │          │
      ▼         ▼          ▼
  各自打成 Docker 镜像
      │
      ▼
  镜像推送到阿里云 ACR（容器镜像仓库）
      │
      ▼
  GitLab CI SSH 到你的 ECS
      │
      ▼
  服务器拉下新镜像
      │
      ▼
  docker compose up -d（新容器启动）
```

这其中有三个关键环节需要解释清楚。

### 环节一：谁来替你编译？

GitLab 有一个叫 **GitLab Runner** 的东西。当代码 push 上去后，GitLab 会找一个可用的 Runner，让它执行 `.gitlab-ci.yml` 里定义的任务。

Runner 可以是 GitLab 官方提供的免费共享 Runner（跑在 GitLab 的服务器上），也可以是你自己装在某台机器上的专属 Runner。共享 Runner 的优点是零配置，缺点是编译环境每次都是全新的（没有缓存），而且国内网络访问 Docker Hub 可能很慢。

对于 MMDP 项目，建议起步就用 GitLab 的共享 Runner。虽然每次 Maven 缓存会丢，但你的项目编译只要 3-5 秒，完全在可接受范围内。等将来项目大了再考虑自建 Runner。

### 环节二：Docker 镜像放哪里？

你之前在服务器上 `docker compose up -d --build`，实际上是在服务器本地构建镜像。但 CI 流程里，代码是推到 GitLab 的，构建发生在 GitLab 的 Runner 上，产生的镜像需要一个能同时被 Runner 和 ECS 服务器访问到的存放位置。

这就是 **阿里云 ACR（容器镜像服务）** 的作用。它本质上是一个远程的 Docker 镜像仓库，跟 Docker Hub 是同一类东西。但它有两个关键优势：

第一，它在阿里云杭州机房内，你的 ECS 也在阿里云杭州，内网拉镜像速度飞快（100MB/s+），不需要走公网，零流量费。第二，它的个人版是免费的，有 3 个命名空间额度，对你的项目绰绰有余。

ACR 用起来也很简单。在它上面建好仓库之后，跟本地 `docker push` / `docker pull` 完全一样的命令，只需要把镜像名前面加上 ACR 的地址前缀，例如 `registry.cn-hangzhou.aliyuncs.com/mmdp/mmdp-backend:latest`。

### 环节三：怎么让服务器知道有新代码？

镜像推到了 ACR 之后，还需要通知服务器："有新版本了，去拉镜像然后重启"。这个动作通常的做法是让 CI 流程 SSH 到服务器执行命令。

是的，就是让 GitLab Runner SSH 到你的 ECS。这听上去有点粗暴，但它是中小项目最稳定、最透明的部署方式。Runner 登录到服务器后，依次执行 `docker login`（登录 ACR）、`docker compose pull`（拉新镜像）、`docker compose up -d`（启动），跟你在本地敲的命令一模一样，只是把这些命令写成了自动化脚本。

你在 GitLab 的 CI/CD Variables 里配置好 ECS 的 IP 和 SSH 私钥，Runner 就能免密登录你的服务器。这些变量是加密存储的，不会泄露。

## 动手之前要准备什么

具体来说有三样东西要准备好。

### 1. 阿里云 ACR 建仓库

登录阿里云 ACR 控制台，创建一个命名空间叫 `mmdp`，然后在里面建三个仓库：`mmdp-backend`、`mmdp-worker`、`mmdp-frontend`。类型选"私有"。

建好之后，去访问凭证页面设一个固定密码。这个密码就是 CI 流程里用来登录 ACR 的凭证。

记下三个仓库的地址，它们长这样：

```
registry.cn-hangzhou.aliyuncs.com/mmdp/mmdp-backend
registry.cn-hangzhou.aliyuncs.com/mmdp/mmdp-worker
registry.cn-hangzhou.aliyuncs.com/mmdp/mmdp-frontend
```

### 2. ECS 服务器上 docker login 一次

手动在服务器上登录 ACR，这样后续 docker compose pull 就不用每次都认证了：

```bash
docker login --username=<你的阿里云账号> registry.cn-hangzhou.aliyuncs.com
# 输入刚才设的固定密码
```

服务器上的 `docker-compose.yml` 需要做一个关键改动：把 Backend 和 Worker 的镜像从本地 `build` 改成直接引用 ACR 镜像。原来是这样的：

```yaml
mmdp-backend:
  build:
    context: ./backend
    dockerfile: Dockerfile
  image: mmdp-backend:latest
```

改成：

```yaml
mmdp-backend:
  image: registry.cn-hangzhou.aliyuncs.com/mmdp/mmdp-backend:${IMAGE_TAG:-latest}
```

这样就不需要服务器本地构建了，直接通过 `IMAGE_TAG` 环境变量拉指定版本。前端暂时保持原来的目录挂载方式也行（改动量小），以后再容器化。

### 3. GitLab 配好 CI 变量

在 GitLab 项目页面 → Settings → CI/CD → Variables，添加以下变量：

| 变量 | 内容 |
|------|------|
| `ACR_REGISTRY` | `registry.cn-hangzhou.aliyuncs.com` |
| `ACR_NAMESPACE` | `mmdp` |
| `ACR_USERNAME` | 你的阿里云账号 |
| `ACR_PASSWORD` | ACR 固定密码 |
| `ECS_HOST` | ECS 公网 IP |
| `ECS_USER` | `root` |
| `ECS_SSH_PRIVATE_KEY` | SSH 私钥内容（注意粘贴完整，包括 `-----BEGIN` 和 `-----END` 行） |

这些变量会被 `.gitlab-ci.yml` 引用。GitLab 对变量做了加密存储，只有流水线运行时才能读到。

## 核心：编写 .gitlab-ci.yml

`.gitlab-ci.yml` 是整个 CI/CD 的大脑。它放在项目根目录，告诉 GitLab：收到 push 之后，按什么顺序、做什么事情。

我们按"阶段"来组织流水线，一共三个阶段：

**第一阶段：Build（构建）**

三条线并行跑。后端用 Maven 镜像编译 JAR 包，前端用 Node 镜像打包，Worker 因为没有编译步骤，直接用 Docker 构建。

```yaml
stages:
  - build
  - push
  - deploy

build-backend:
  stage: build
  image: maven:3.9-eclipse-temurin-21    # 直接在 Maven 容器里编译
  script:
    - cd mmdp-backend
    - mvn clean package -DskipTests -q
    - cp target/mmdp-backend-*.jar ../deploy/backend/mmdp-backend.jar
  artifacts:
    paths:
      - deploy/backend/mmdp-backend.jar   # 传给下一阶段用
    expire_in: 1h                         # 1 小时后自动清理
```

这里用到了一个概念叫 `artifacts`（制品传递）。构建出来的 JAR 包会被暂时保存，后续的 push 阶段可以直接拿来用，不需要重新编译。

前端和 Worker 的构建写法类似。Worker 更简单——它没有编译过程，Dockerfile 里直接从源码构建。

**第二阶段：Push（推镜像）**

拿到上一阶段的构建产物后，用 Docker 建镜像，然后推到 ACR。

```yaml
push-backend:
  stage: push
  image: docker:27
  services:
    - docker:27-dind                    # dind = Docker-in-Docker，在容器里跑 Docker
  needs:
    - build-backend                     # 等 backend 编译完才执行
  script:
    - docker build -t $ACR_REGISTRY/$ACR_NAMESPACE/mmdp-backend:$CI_COMMIT_REF_SLUG \
        -f deploy/backend/Dockerfile deploy/backend/
    - docker login --username=$ACR_USERNAME --password=$ACR_PASSWORD $ACR_REGISTRY
    - docker push $ACR_REGISTRY/$ACR_NAMESPACE/mmdp-backend:$CI_COMMIT_REF_SLUG
```

这里有一个可能是你第一次见的概念——**Docker-in-Docker（dind）**。CI Runner 本身跑在一个容器里，但要执行 `docker build` 就需要容器里能访问 Docker 引擎。`docker:27-dind` 服务就是提供一个嵌套的 Docker 环境。你不需要深入了解它的原理，只需要知道：CI 中凡是执行 `docker build` 或 `docker push` 的步骤，都要加上 `services: [docker:27-dind]`。

镜像的标签用的是 `$CI_COMMIT_REF_SLUG`，这是 GitLab 内置变量，自动取分支名（比如 `dev`）。所以 push 到 dev 分支就会打 `dev` 标签，main 分支就是 `main` 标签。这样服务器上可以指定拉哪个分支的版本。

**第三阶段：Deploy（部署）**

SSH 到 ECS，拉镜像，重启服务。这个步骤设为了手动触发（`when: manual`），因为部署到服务器你是想有掌控感的——确认前面的构建都没问题，再去点一下按钮部署。

```yaml
deploy:
  stage: deploy
  image: alpine:3.20
  before_script:
    - apk add --no-cache openssh-client
    - mkdir -p ~/.ssh
    - echo "$ECS_SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
    - chmod 600 ~/.ssh/id_rsa
    - ssh-keyscan -H $ECS_HOST >> ~/.ssh/known_hosts
  script:
    - |
      ssh $ECS_USER@$ECS_HOST "
        set -e
        cd /data/mmdp/deploy
        docker login --username=$ACR_USERNAME --password=$ACR_PASSWORD $ACR_REGISTRY
        IMAGE_TAG=$CI_COMMIT_REF_SLUG docker compose pull
        IMAGE_TAG=$CI_COMMIT_REF_SLUG docker compose up -d
      "
  only:
    - dev
    - main
  when: manual
```

自动部署还是手动确认，这是你可以自己把握的。如果团队就你一个人，而且你在 dev 分支做开发，可以把 `when: manual` 去掉，让 dev 分支的 push 全自动部署到测试服务器。main 分支保留手动确认，作为生产环境的最后一道防线。

## 第一次跑起来的步骤

把这些文件准备好之后，按这个顺序来：

1. 在阿里云 ACR 建好三个镜像仓库，设好固定密码
2. 在 ECS 服务器上 `docker login` 到 ACR
3. 更新服务器上的 `docker-compose.yml`，把 `build` 改成 `image` 引用 ACR
4. 创建 `.gitlab-ci.yml` 放在项目根目录，提交到 dev 分支
5. 在 GitLab 项目设置里配好 CI/CD Variables
6. `git push` → 打开 GitLab 网页 → CI/CD → Pipelines → 看到流水线在跑

第一次大概率不会一次过。可能 Runner 拉 Maven 镜像超时了，可能 SSH 密钥格式不对，可能 ACR 密码输错了。这些都是正常的——看流水线日志，哪一步红了就修哪一步。这个过程本身就是在学习。

## 出问题了怎么回滚

回滚比你想象中简单得多。你只需要让服务器拉回上一个正常的镜像版本：

```bash
# 在服务器上
cd /data/mmdp/deploy
IMAGE_TAG=上一个正常的标签 docker compose pull
IMAGE_TAG=上一个正常的标签 docker compose up -d
```

或者你根本不用记标签——因为每次 push 构建的镜像都打上了对应 commit 的分支标签。你只需要把 `IMAGE_TAG` 换回上一个正常状态的 commit hash，一分钟内就能回到之前的版本。

这也是为什么要用镜像仓库而不是本地 build 的原因：每一次构建都有一个独立的镜像版本，回滚就等于切换版本标签。

## 几点实战经验

**数据安全第一**。MySQL 的数据文件（`mysql-data/`）不要放在容器里，一定要挂载到宿主机目录。这个你现在的部署方案已经做到了，CI/CD 不要碰这个目录。即使整个容器环境崩溃，数据文件还在，重新 docker compose up 就行。

**从简单开始，逐步加码**。不要一上来就追求"push main 自动上生产"这种高级目标。先让 dev 分支的 CI 能跑通（只 build 和 push 镜像），部署还是你手动 SSH 去触发。等你对流程熟悉了，再把部署步骤加入流水线。

**前端可以先不动**。前端改得少的时候，保持原来的 `dist/` 挂载模式就行。git push 只触发后端和 Worker 的 CI/CD。前端的 CI 步骤后面再加，不着急。

**监控很重要，但现在不是最急的**。CI/CD 跑起来的头几天，你肯定会频繁去看 GitLab 的流水线页面和服务器日志。这很正常。等稳定了再考虑接日志告警。

**K8s 是真香，但不是现在**。当你发现 Docker Compose 的"先停再启"导致短暂服务不可用让你受不了的时候，当你需要自动扩缩容来应对突发流量的时候，当你有多个独立服务需要独立部署的时候——那才是认真考虑 K8s 的时机。到那时候再回头看这篇文章，你会发现 CI/CD 这层基础你已经打好了，剩下的只是"把 docker compose 命令换成 kubectl apply"。
