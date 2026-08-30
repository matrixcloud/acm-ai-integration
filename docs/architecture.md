# ACM AI Integration Architecture

## 分层

```text
┌─────────────────────────────┐     ┌────────────────────────────┐
│ AI 应用层                    │     │ AI 支持层                   │
│                             │     │                            │
│  Customer AI Agent          │     │ LLM Gateway   KB Service   │
│  （含客服业务层，合并部署）      │     │                            │
└─────────────────────────────┘     │ LLM Obs.      LLM Sandbox  │
│ 业务层                       │     │                            │
│                             │     └────────────────────────────┘
│ Order Service               │
└─────────────────────────────┘
┌────────────────────────────────────────────────────────────────┐
│ 基础设施层                                                       │
│                                                                │
│ PostgreSQL (pgvector)   Redis                                   │
└────────────────────────────────────────────────────────────────┘
```

## Mono Repo

- customer-agent: 客服服务（业务 + AI Agent，原 customer-svc 已并入）
- order-svc: 订单服务
- kb-svc: 知识库服务（原 RAG 服务）
- registry-svc: 注册中心（Eureka）
- gateway-svc: 网关
- webapps: 前端应用
  - customer-app: 客服前端
  - kb-app: 知识库管理前端

## 项目结构约定

### 后端：分层架构

每个服务是独立 Gradle 模块，根包 `org.acm.<缩写>`。模块与包名一一对应：

| 模块 | 根包 | 说明 |
| --- | --- | --- |
| customer-agent | org.acm.ca | 客服 + AI Agent |
| order-svc | org.acm.os | 订单 |
| kb-svc | org.acm.kb | 知识库（RAG） |
| gateway-svc | org.acm.gw | 网关（WebFlux） |
| registry-svc | org.acm.rg | 注册中心（Eureka） |
| common-lib | org.acm.common | 共享库（通用分页/筛选） |

业务服务（customer-agent / order-svc / kb-svc）内部遵循统一分层：

```text
org.acm.<缩写>
├── domain/              # 领域层：聚合根、仓储端口、领域异常、shared；不依赖其他层
├── application/         # 应用层：用例编排 + 端口定义
│   ├── port/in/         #   入站用例端口 + command/query（adapter 依赖其抽象）
│   ├── port/out/        #   出站端口 + 端口契约异常（infra 实现）
│   └── service/         #   应用服务（实现入站端口）
├── infra/               # 基础设施层：出站适配器（client/llm/vectorstore...）+ 配置
└── interfaces/http/     # 适配器层：入站 REST
    ├── controller/      #   控制器
    ├── request/         #   入站请求 DTO
    ├── response/        #   出站响应 DTO
    ├── mapper/          #   MapStruct 映射器
    └── exception/       #   异常 + GlobalExceptionHandler（Problem Details, RFC 9457）
```

依赖方向：`interfaces.http → application.port.in → application.service → domain`；
`infra.client → application.port.out`（依赖倒置）；`domain` 不依赖任何外层。

gateway-svc 与 registry-svc 是无业务域的小服务，不套用上述分层。

### 前端：pnpm workspace

应用位于 `webapps/`，每个应用一个独立包。应用内统一目录约定：

```text
webapps/<app>/src/
├── components/
│   ├── ui/          # 通用 UI 组件（button/card/avatar/badge...）
│   └── <业务域>/    # 业务组件（如 chat/、kb/）
├── services/        # API 客户端（含 SSE）
├── types/           # 领域类型定义
├── lib/             # 通用工具
├── test/            # Vitest setup
├── App.tsx          # 根组件
├── main.tsx         # 入口
└── index.css        # 全局样式（Tailwind）
```

- 统一 React 19 + TypeScript + Vite 7 + Tailwind CSS v4，测试用 Vitest + Testing Library
- 开发环境经 Vite 代理将 `/api` 转发到 `gateway-svc`（http://localhost:8080），不直连下游服务
