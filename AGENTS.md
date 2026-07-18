# AI 代理工作指引

多模态数据平台（MMDP）AI 编码代理的入口文件。详细架构、命令、设计系统见 [CLAUDE.md](CLAUDE.md)。

## 会话语言

所有问答、代码注释、文档均使用**中文**。技术术语（如 API、DTO、Mapper、Pinia、JWT）保留英文原名。

## 快速定位

| 需求 | 入口 |
|------|------|
| 项目架构 | [CLAUDE.md](CLAUDE.md) |
| 文档索引 | [docs/README.md](docs/README.md) |
| 后端 API | [mmdp-backend/README.md](mmdp-backend/README.md) |

## 工作原则

1. 修改代码前先读 CLAUDE.md 了解架构约定
2. 涉及新功能先查 docs/ 下的规范文档
3. 不确定的业务逻辑优先查现有 Controller/Service 实现
4. 禁止提交账号密码、环境配置、编译产物
