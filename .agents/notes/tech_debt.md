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
- 现状：token 成本核算缺失，customer-svc 无法从 done 事件持久化 token 成本
- 影响：演示级无碍（customer-svc 只需完整回复内容）；工业级失 token 成本核算与用量告警
- 起因：流式下 `Usage` 只在 `ChatResponse`（`stream().chatResponse()`）流尾才完整，`stream().content()` 拿不到；拿 token 需替换为 `chatResponse()` 并读 `ChatResponseMetadata.getUsage()`
- 消除方式：AgentService 改用 `stream().chatResponse()`，从流尾取 `getUsage()` 塞进 done 事件；或待工业级演进（§12.5 已定位「演示期不含 token 成本核算」）
- 来源：review-acm-code WAIVER（自 review，subagent 限流）
