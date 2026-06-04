

# 多模态数据平台（MMDP）
MMDP 是多模态数据平台的最小可行产品代码仓库，采用 **Vue 前端 + Spring Boot 后端** 技术架构。

## 仓库目录结构
- mmdp-frontend/：Vue 3 + Vite 前端项目
- mmdp-backend/：Spring Boot 后端项目
- docs/：项目说明、MVP 功能范围、版本相关文档
- container-bat/：本地辅助脚本

## 单仓库规范
项目迭代 MVP 阶段，前后端统一放在同一个代码仓库中，确保接口、页面、文档的修改保持同步。

## 禁止提交的内容
仓库中请勿提交以下文件/数据：
- 正式数据库、OSS 账号密钥
- 本地环境配置文件
- 前端编译生成产物
- Maven 构建文件及本地依赖缓存
- data/ 目录下的运行时数据

## 前端
启动命令：
```
cd mmdp-frontend
npm install
npm run dev
```
前端通过环境变量 `VITE_API_BASE_URL` 配置接口地址。本地开发可基于 `.env.development.example` 模板新建 `.env.development` 文件。

## 后端
启动命令：
```
cd mmdp-backend
mvn spring-boot:run
```
配置文件位于 `src/main/resources/application.yml`，已预设安全默认值，同时支持通过以下环境变量覆盖配置：
- MMDP_DB_URL 数据库地址
- MMDP_DB_USERNAME 数据库用户名
- MMDP_DB_PASSWORD 数据库密码
- MMDP_OSS_ENDPOINT OSS 服务地址
- MMDP_OSS_ACCESS_KEY_ID OSS 访问密钥
- MMDP_OSS_ACCESS_KEY_SECRET OSS 安全密钥
- MMDP_OSS_BUCKET OSS 存储桶名称
- MMDP_OSS_REGION OSS 地域

后端详细部署与配置说明，可查看 `mmdp-backend/README.md`。

