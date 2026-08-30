# Tech Debt

## 2026-08-29 bootBuildImage 的 builder 与 runImage 版本策略

- 位置：根 `build.gradle` 的 `bootBuildImage` 约定
- 现状：`builder` 使用浮动 tag `docker.m.daocloud.io/paketobuildpacks/builder-noble-java-tiny:latest`，`runImage` 硬编码 `ubuntu-noble-run-tiny:0.0.122`（取自当前 builder 元数据，review 时已实测一致）
- 风险：Paketo 重建 builder 后二者可能漂移；同 stack ID 下一般不阻断，最坏构建期大声失败或运行在旧 run rootfs
- 起因：本机 docker.io 不可达，走 DaocCloud 镜像源，无法便捷查询版本化 tag
- 消除方式：将 builder 与 runImage 成对 pin 到版本化 tag，升级时同步更换
- 来源：review-acm-code F1（WAIVER）

## 2026-08-29 customer-agent token 用量未在 done 事件传递

- 位置：`customer-agent` `AgentService.streamReply` 用 `stream().content()`（`Flux<String>`），`SseReplyStream.emitDone` 仅传 `content`，未传 token 数（设计 §12.4 要求 `AgentReplyDone` 带 promptTokens/completionTokens/totalTokens）
- 现状：token 成本核算缺失，合并后的 customer-agent（原计划由 customer-svc 持久化 token 成本）无法获得用量数据
- 影响：演示级无碍（业务层只需完整回复内容）；工业级失 token 成本核算与用量告警
- 起因：流式下 `Usage` 只在 `ChatResponse`（`stream().chatResponse()`）流尾才完整，`stream().content()` 拿不到；拿 token 需替换为 `chatResponse()` 并读 `ChatResponseMetadata.getUsage()`
- 消除方式：AgentService 改用 `stream().chatResponse()`，从流尾取 `getUsage()` 塞进 done 事件；或待工业级演进（§12.5 已定位「演示期不含 token 成本核算」）
- 来源：review-acm-code WAIVER（自 review，subagent 限流）

## 2026-08-30 幂等事务内同步等待 LLM，最长占住 DB 连接约 30s

- 位置：合并后的 `customer-agent` `ConversationService.streamMessageInternal`（经 `IdempotencyService.execute` 的单事务边界）
- 现状：`send-message` 事务内完成「存客户消息 → 同步等待 agent 流式生成（LLM 最长 30s，ReAct 多轮更久）→ 存回复 → complete 幂等记录」，期间 chunk 网络写也发生在事务内
- 影响：演示并发下可能耗尽 Hikari 连接池；长事务拉高锁持有时间（`conversations` 行锁 + `@Version`）
- 起因：沿用原 customer-svc 的同步幂等设计；SSE 流式只优化了首字延迟，未改事务边界（合并设计 §13 明示此代价）
- 消除方式：拆两段事务——先「幂等预留 + 客户消息落库」短事务，流式完成后第二段「存回复 + complete」，失败走状态机补偿；或将 agent 流式聚合移出事务（收集完成后再开短事务落库）
- 来源：customer-agent 合并设计 §13

## 2026-08-30 共享 SSE 线程池有效并发仅 4，排队请求表现为「无响应直到超时」

- 位置：`customer-agent` `interfaces/http/SseExecutorConfig`（`sseExecutor`：core=4 / max=16 / queue=100），被 `/conversations/{no}/messages` 流式端点与 `/agent/reply` 共享
- 现状：ThreadPoolTaskExecutor 在队列（100）填满前不扩容到 max，有效并发流 = 4；第 5 个并发请求在队列中等待，emitter 60s 计时已启动；队列与线程池全满时 `TaskRejectedException` 走容器默认 500（非 ProblemDetail）
- 影响：演示级可接受（Hikari 连接数 10 > 4，不会先耗尽 DB 连接；60s emitter 超时兜底）；多用户并发演示时观感为「卡住」
- 消除方式：流式场景改 core=max（或 SynchronousQueue 直接扩容），并将拒绝策略映射为 503 ProblemDetail；或按端点拆分独立池并限流
- 来源：review-acm-code F3（WAIVER）

## 2026-08-30 下游调用超时预算 8s 不是硬上界，真实上界约 14.2s

- 位置：`customer-agent` `OrderQueryClientImpl` / `KbSearchClientImpl` 的 `@Retryable(timeout = 8000)`，配合既有 HTTP connect 2s / read 5s
- 现状：Spring Framework 7 retry 的 `timeout` 是「是否再发起下一次重试」的 deadline 检查，不中断进行中的调用；单次尝试最坏 7s，7.1s 时仍会发起第 2 次（再 7s），真实上界 ≈ 2×7s + backoff ≈ 14.2s
- 影响：下游挂死时 SSE 端最长约 14s 才收到 error 事件，而非方案口径的 8s；对快速失败场景（如 503）预算成立（测试断言 <8s 仅覆盖该场景）
- 起因：这是已确认方案给定参数（connect 2s / read 5s / maxRetries 1 / timeout 8s）的固有算术结果，非实现偏差；若要硬上界需砍单次超时或引入可中断的 TimeLimiter（后者会截断合法 5s 读并引入线程池跳转，已因前述代价显式关闭）
- 消除方式：收紧单次超时（如 read 3s）使 8s 真实生效；或接受 14.2s 上界并在容量规划时按此口径
- 来源：review-acm-code F3（WAIVER，待用户确认）

## 2026-08-30 订单发货管理 API 依赖 order_items.id，但订单详情响应未暴露该字段

- 位置：`order-svc` `ShipmentAdminController`（`POST /admin/orders/{orderNo}/shipments` 要求 `orderItemId`）+ `CreateOrderResponse.Item`（仅 `lineNo`，无 `id`）
- 现状：`shipment_items.order_item_id` 外键指向 `order_items.id`（PK），但订单详情/列表响应均不暴露 `order_items.id`，纯 HTTP 客户端无法拼接发货请求；`scripts/seed/seed.mjs` 以只读 psql 查 `order_items.id` 补该 PK（仍走真实发货 API）
- 影响：演示后台发货能力无法端到端由 HTTP 驱动；seed 脚本引入只读 DB 依赖
- 起因：响应 DTO `CreateOrderResponse.Item` 无 `id` 字段，MapStruct 按名映射时静默丢弃 `OrderItem.id`
- 消除方式：在 `CreateOrderResponse.Item` 增加 `id`（MapStruct 自动映射）或新增 `orderItemId` 字段；验证 shipping 集成测试是否可改为纯 HTTP
- 来源：seed 交付（发现于 order-svc 冒烟）
## 2026-08-30 order-svc initializeDetails 用 size() 触发 Hibernate 懒加载

- 位置：`order-svc` `OrderService.java:140,142`、`OrderLifecycleService.java:314,317` 的 `initializeDetails`
- 现状：用 `order.getPayments().size()`、`getShipments().forEach(s -> s.getItems().size())` 触发集合懒加载，返回值被忽略
- 影响：SpotBugs `RV: size() ignored`（effort=max）；意图不透明，代码味道差
- 起因：Hibernate 懒加载初始化的惯用 hack
- 消除方式：已处理——`Hibernate.initialize` 对 unmodifiable wrapper 无效；lambda 内的 size() 被 SpotBugs 归到合成方法、`<Method>` 无法精确匹配，故按类豁免（`code=RV` + `OrderService`/`OrderLifecycleService`，WAIVER）；两个类当前无其它 RV，新增 RV 时需复核此豁免
- 来源：质量关卡垂直切片 SpotBugs（2026-08-30）

## 2026-08-30 InventoryClientImpl.failureRegistry 同步不一致

- 位置：`order-svc` `InventoryClientImpl.java:38`，读端 `synchronized`、写端 `@Autowired setFailureRegistry` 非同步
- 现状：SpotBugs `IS` 报 inconsistent synchronization（locked 80% of time）
- 影响：实际 Spring 单线程注入、启动后只读，false positive，无真实并发问题
- 起因：mock 组件用 setter 注入供测试替换
- 消除方式：已处理——setter 加 `synchronized`（与读端锁一致）
- 来源：质量关卡垂直切片 SpotBugs（2026-08-30）

## 2026-08-30 kb-svc 与 common-lib 测试覆盖欠债，② 覆盖率底线无法立即落地

- 位置：`kb-svc`（A 级，test 仅 `KbSvcApplicationTests` context-load）；`common-lib`（C 级，无 test 目录，5 个 http 类）
- 现状：`kb-svc` 的 `domain/eval`、`domain/kb`、`application/service` 零单测；`common-lib` 的 `PageRequest`/`SearchRequest`/`FilterOperator` 等零测试
- 影响：② 覆盖率底线（kb-svc 核心包 0.85/0.75、common-lib 0.80）当前无法设 verification——设了立即 0% 覆盖红；已全局接 jacoco 插件做观测，但 verification 阈值待补测试后启用
- 起因：模块在建阶段侧重骨架与集成，未同步补领域/应用层单测
- 消除方式：补 kb-svc 的 domain/application 单测与 common-lib http 类单测，达标后接 `jacocoTestCoverageVerification` 阈值
- 来源：质量关卡横向复制（2026-08-30）

## 2026-08-30 SpotBugs EI/EI2 豁免（DTO/record 数据传输意图）

- 位置：`config/spotbugs/exclude.xml` 的 DTO 命名正则（`~.*(Request|Response|Command|Query|Summary|Report|Thread|Config)$`）+ 3 个特例（`RecursiveCharacterTextSplitter$Builder`、`ToolCallObservingAdvisor`、`AgentService`）
- 现状：DTO/record 返回可变 `List` 是传输意图，非内部表示泄漏；Spring 框架对象（`MeterRegistry`/`ObservationRegistry`）注入被 EI2 误报
- 影响：业务聚合根（`Order` 等）已用 unmodifiable/defensive copy 防护，不在豁免范围；豁免按命名模式精确收窄，非全局关闭检测类目
- 起因：Lombok record + `List` 字段在 DTO/配置边界的标准模式
- 消除方式：WAIVER——保留精确豁免；新增的非 DTO 类 EI 报错仍会被正常检测
- 来源：质量关卡横向复制 review（2026-08-30）

## 2026-08-30 SpotBugs FS 豁免（LLM prompt 模板 \n）

- 位置：`config/spotbugs/exclude.xml` 的 `EvaluationService`
- 现状：`ANSWER_PROMPT_TEMPLATE` 的 `\n` 是传给 LLM 的文本，不是控制台/文件输出换行
- 影响：改用 `%n` 会破坏 prompt 语义（引入平台相关换行）
- 起因：LLM prompt 模板用 `\n` 作为数据内容
- 消除方式：WAIVER——保留精确豁免（仅 `EvaluationService` 的 FS）
- 来源：质量关卡横向复制 review（2026-08-30）

## 2026-08-30 customer-app 客服回复按 markdown 渲染，软换行（单个 \n）折叠为空格

- 位置：`webapps/customer-app/src/components/chat/chat-message.tsx`（support 消息经 `ReactMarkdown` + `remark-gfm` 渲染）
- 现状：AGENT 回复不再用 `whitespace-pre-wrap` 原样输出，改为 CommonMark/GFM 语义——段落换行需 `\n\n`，单个 `\n` 软换行折叠为空格；流式中的「正在输入」气泡显式 `renderMarkdown={false}` 保持纯文本，落定后整体替换为 markdown 渲染（消除流式逐 token 重解析的 O(n²) 与半成品语法闪烁）
- 影响：若后端 LLM 输出`单 \n 换行`的纯文本（非 `\n\n` 段落、非列表/代码块），已落库或新回复的可见换行会丢失；customer 消息始终走纯文本分支，不受影响
- 起因：后端 `customer-agent` 的 system prompt（application.yml）未强制 markdown 换行约定，依赖 qwen 自然输出 markdown（bug 报告即证明 markdown 已产出但前端未渲染）
- 消除方式：若后端确认存在单 `\n` 纯文本回复，则加 `remark-breaks` 或于 `toChatMessage` 边界把 `\n` 归一为 `\n\n`；否则维持现状并作为前端↔LLM 的 markdown 契约
- 来源：review-acm-code WAIVER（Design Fit：软换行语义）

