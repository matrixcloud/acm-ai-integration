# AGENTS.md

ACM AI Integration 是一个 AI+业务的 mono repo

## 全局约束

- 根据使用者的语言，使用的语言相应的语言进行回答
- 中英文间加空格
- 讨论设计或问题时优先输出伪代码
- 如无必要不写代码注释

## 编码核心原则

- EXPLICIT (显式): 意图透明。消灭魔法值，拒绝隐式约定
- FAIL-FAST (阻断): 快速失败。异常大声抛出，严禁静默容错
- NO_GUESS (零脑补): 遇盲区挂起。严禁猜测需求或伪造 API，必须提问

## 质量门禁

- 代码改动后必须执行 `review-acm-code`

## 按需阅读

- 项目架构：./docs/architecture.md
- 模块设计：./.agents/designs/

## 日志规约

每个后端服务自带 `logback-spring.xml`，两个 appender 同时生效：

- CONSOLE：可读文本，pattern 含 `%X{traceId}` / `%X{spanId}`（micrometer-tracing-brave 自动注入 MDC）
- JSON_FILE：Logstash JSON 旋转文件 `logs/<app>.json.log`（100MB / 日切 / 保留 30 天 / 总量 5GB）

### 关键点（必须打日志）

1. 入站 HTTP：每请求一条 `http.in` 摘要（method / path / status / durationMs / clientIp），由 `HttpRequestLoggingFilter`（common-lib）或 gateway 的 `RequestLoggingWebFilter` 输出
2. 出站调用：每个 out-port 适配器成功时打 `http.out` / `llm.call` 摘要（service / op / 业务键 / 结果数 / durationMs）
3. 业务事件：用例边界状态变迁打 info（如 `order.created`、`payment.succeeded`、`document.ingested`、`eval.run.started`）
4. 异常出口：`GlobalExceptionHandler` 兜底 `@ExceptionHandler(Exception.class)` 打 `http.error`（method / path / 堆栈）后原样重抛
5. 补偿与降级：补偿失败、熔断降级打 warn/error

### 格式约定

- 事件名：`<域>.<动作>.<结果>`，如 `order.created`、`agent.reply.failed`；参数一律 `key={}` 内联进 message
- 级别：info = 业务事件与摘要；warn = 降级 / 补偿 / 可重试失败；error = 不可恢复失败（带异常堆栈）
- 禁止：打印密码 / token / 完整客户数据；循环内逐条 info；同一请求超过一条 `http.in`

### 结构化检索

JSON 行自带 `service`、`traceId`、`spanId`、`level`、`logger`、`message` 字段。业务键不做字段化提取（避免业务代码耦合 logstash 依赖），以 `key=value` 形式内联在 `message` 中，检索时用行过滤（Loki `|=`）匹配。