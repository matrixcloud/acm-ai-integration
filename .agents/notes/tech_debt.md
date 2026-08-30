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
