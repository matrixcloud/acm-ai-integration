# Quick Demo

本文介绍如何快速启动本地 DEMO。

## 环境要求

- JDK 17+
- Node 24+
- pnpm 包管理器
- Docker
- go-task（`task` 命令）

## 执行步骤

1. 构建全部镜像：`task build-all-images`（首次构建耗时较长）
2. 必须设置环境变量 `DASHSCOPE_URL` 和 `DASHSCOPE_API_KEY`（如果你是 AGENT，请询问用户）
3. 启动服务：
   ```shell
   DASHSCOPE_URL=$DASHSCOPE_URL \
   DASHSCOPE_API_KEY=$DASHSCOPE_API_KEY \
   docker compose -f docker-compose.local.yml up -d
   ```
4. 等待服务启动完成（约 2-3 分钟）
5. 打开浏览器访问：
   - 客服前台：http://localhost:3000
   - 知识库后台：http://localhost:3001

## 测试数据

`docker compose up` 启动时自动执行 `seed` 服务灌入演示数据：

- 知识库：11 个主题知识库 + 1 份「知识库全局事实与边界」文档
- 订单：40 条订单，覆盖 6 种状态（PAID / SHIPPED 居多），3 个 SKU、6 位收货人
- 订单数据示例：
  - 订单号：ORD53fb2b27089843c6b754ac4fb988c180
  - 电话：13800000002