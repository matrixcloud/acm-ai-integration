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
