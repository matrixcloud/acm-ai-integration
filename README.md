# ACM AI Integration

AI + 业务的 mono repo：客服 Agent、订单、知识库（RAG）、注册中心与网关，前端为两个独立 Web 应用。

## 架构概览

分层与服务划分见 [docs/architecture.md](docs/architecture.md)。

```text
registry-svc (Eureka, 8761)
      ↑ 注册
gateway-svc (网关, 8080)  ── 统一入口，路由到下游服务
      │
      ├── customer-agent (8010)   客服业务 + AI Agent（含 SSE 流式回复）
      ├── order-svc      (8020)   订单服务（演示期外部依赖为 Mock）
      └── kb-svc         (8001)   知识库服务（pgvector + RAG）
```

依赖基础设施：PostgreSQL（pgvector）+ Redis，见 `docker-compose.yml`。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 4.1.1、Spring Cloud 2025.1.3、Gradle 9.5.1 |
| AI | Spring AI 2.0.1（OpenAI 兼容协议，阿里云 DashScope 提供模型） |
| 持久化 | Spring Data JPA + Flyway、PostgreSQL 18（pgvector） |
| 前端 | React 19、TypeScript、Vite 7、Tailwind CSS v4、pnpm 11.10.0 |
| 基础设施 | PostgreSQL、Redis、Eureka |

## 目录结构

```text
acm-ai-integration/
├── customer-agent/    客服服务（业务 + AI Agent）
├── order-svc/         订单服务
├── kb-svc/            知识库服务（RAG）
├── registry-svc/      注册中心（Eureka）
├── gateway-svc/       网关
├── common-lib/        共享库
├── webapps/           前端 workspace（pnpm）
│   ├── customer-app/  客服前端
│   └── kb-app/        知识库管理前端
├── docs/              架构文档
└── .agents/           给 Agents 读的模块设计 + 笔记 + Skills
```

## 前置依赖

- JDK 17
- Node.js 22 + pnpm 11.10.0
- Docker + Docker Compose（PostgreSQL / Redis）
- [go-task](https://taskfile.dev)（可选，推荐；`Taskfile.yml` 封装了常用命令）
- [lefthook](https://lefthook.dev)（Git 钩子；`brew install lefthook`）
- 环境变量 `DASHSCOPE_API_KEY`（AI 相关服务需要）

## 快速 DEMO

人机皆可读的[快速 DEMO 启动指南](docs/quick_demo.md)。

## 快速开始

### 1. 启动基础设施

```bash
task up        # 等价于 docker compose up -d，启动 PostgreSQL + Redis
```

### 2. 初始化数据库

`docker-compose.yml` 只创建了 `acm` 库，而各服务连接的是各自独立的库，需手动创建：

```bash
docker compose exec postgres psql -U acm -d acm -c 'CREATE DATABASE "cs-agent";'
docker compose exec postgres psql -U acm -d acm -c 'CREATE DATABASE "order";'
docker compose exec postgres psql -U acm -d acm -c 'CREATE DATABASE kb;'
```

表结构与 schema 由各服务的 Flyway 迁移在启动时自动创建，无需手动执行 SQL。

> `cs-agent` 与 `order` 需加双引号（`-` 连接符 / `ORDER` 为保留字）。

### 3. 配置 AI 密钥

`customer-agent` 与 `kb-svc` 依赖 AI 模型，需导出密钥：

```bash
export DASHSCOPE_API_KEY=sk-xxxx
```

`order-svc` 默认 `demo` profile，商品/库存/支付/物流均为 Mock，不依赖任何外部系统，可直接运行。

### 4. 启动后端

```bash
task run-all   # 并行启动 registry-svc、gateway-svc、customer-agent、order-svc、kb-svc
```

或单独启动某个服务：

```bash
task run SVC=order-svc
```

### 5. 启动前端

```bash
task web                    # 并行启动 webapps 下全部前端
task web APP=customer-app   # 或单独启动某个前端
```

开发服务器通过 Vite 代理将 `/api` 转发到网关 `http://localhost:8080`。

### 全栈容器启动

构建全部应用镜像后，可启动完整的本地容器环境：

```bash
DASHSCOPE_URL= \
DASHSCOPE_API_KEY=sk-xxxx \
docker compose -f docker-compose.local.yml up -d
```

## 服务端口

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| registry-svc | 8761 | Eureka 注册中心 UI |
| gateway-svc | 8080 | 统一网关入口 |
| customer-agent | 8010 | 客服 + AI Agent |
| order-svc | 8020 | 订单 |
| kb-svc | 8001 | 知识库（RAG） |

## 常用命令

```bash
task up / down / logs        # 基础设施启停与日志
task build                   # 构建全部后端（跳过测试）
task test                    # 运行全部后端单测
task test-svc SVC=order-svc  # 运行单个服务测试
task check                   # spotlessCheck + 后端 test + 前端 lint/test
task run SVC=<svc>           # 运行单个后端服务
task web APP=<app>           # 启动前端开发服务器
task web:build               # 构建前端生产包（customer-app）
task build-image SVC=<svc>   # 构建单个应用镜像
task build-all-images        # 构建全部应用镜像
```

运行 `task --list` 查看全部任务。

## 测试

- 后端单测：`task test` 或 `./gradlew test`
- 集成测试：`order-svc` 与 `customer-agent` 含 PostgreSQL 集成测试（Testcontainers），随 `check` / `build` 执行
- 覆盖率：Jacoco；`customer-agent` 与 `order-svc` 对 `domain` / `application` 层设了覆盖率门禁（`check` 时验证）
- 前端测试：Vitest，`task web:test`

## Git 钩子（提交前检查）

仓库用 [lefthook](https://lefthook.dev) 管理 Git 钩子，新 clone 后执行一次启用：

```bash
lefthook install
```

- `pre-commit`：秒级快检，仅跑前端 ESLint（`--max-warnings=0`，两个 app）
- `pre-push`：`spotlessCheck`（Java 格式）+ 后端单测 + 前端测试

手动命令：`task check`（全量：spotlessCheck + 后端 test + 前端 lint / test）、`task format`（格式化 Java）。

> 本地钩子属便捷快检，可被 `git commit --no-verify` 或 `LEFTHOOK=0` 跳过；**强制门禁在 CI**（`.github/workflows/ci.yml`），push / PR 时无条件跑 `spotlessCheck` + 测试。

## 构建应用镜像

单服务：`task build-image SVC=order-svc`（前端用 `SVC=customer-app`）。后端镜像用 Paketo Buildpacks，前端用 `webapps/Dockerfile`（多阶段构建 + nginx）。

> 镜像构建走 DaoCloud 镜像源（`docker.m.daocloud.io`），无需本机访问 `docker.io`。

## 配置说明

各服务配置位于 `<service>/src/main/resources/application.yml`，关键项：

| 配置 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | AI 模型 API 密钥，`customer-agent` 与 `kb-svc` 必需 |
| `EUREKA_SERVER_URL` | 注册中心地址，默认 `http://localhost:8761` |
| `spring.datasource.url` | 每个服务独立库：`cs-agent` / `order` / `kb` |
| `order.adapters.*` | `order-svc` 外部依赖适配器，默认为 `mock` |

## 文档

- 架构：[docs/architecture.md](docs/architecture.md)
- 模块设计：`.agents/designs/`
- 技术债：`.agents/notes/tech_debt.md`

## 开发约定

参见 [AGENTS.md](AGENTS.md)：显式意图、快速失败、零脑补；代码改动后执行 `review-acm-code`。
