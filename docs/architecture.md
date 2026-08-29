# ACM AI Integration Architecture

## 分层

```text
┌─────────────────────────────┐     ┌────────────────────────────┐
│ AI 应用层                    │     │ AI 支持层                   │
│                             │     │                            │
│  Customer AI Agent          │     │ LLM Gateway   KB Service   │
└─────────────────────────────┘     │                            │
┌─────────────────────────────┐     │ LLM Obs.      LLM Sandbox  │
│ 业务层                       │     │                            │
│                             │     └────────────────────────────┘
│ Order Service  Customer Svc │
└─────────────────────────────┘
┌────────────────────────────────────────────────────────────────┐
│ 基础设施层                                                       │
│                                                                │
│ Milvus              PostgreSQL              Redis              │
└────────────────────────────────────────────────────────────────┘
```

## Mono Repo

- customer-agent: 客服服务 Agent
- customer-svc: 客服服务
- order-svc: 订单服务
- kb-svc: 知识库服务（原 RAG 服务）
- webapps: 前端应用
  - customer-app: 客服前端
  - kb-app: 知识库管理前端
