# Tech Debt

## 2026-08-29 bootBuildImage 的 builder 与 runImage 版本策略

- 位置：根 `build.gradle` 的 `bootBuildImage` 约定
- 现状：`builder` 使用浮动 tag `docker.m.daocloud.io/paketobuildpacks/builder-noble-java-tiny:latest`，`runImage` 硬编码 `ubuntu-noble-run-tiny:0.0.122`（取自当前 builder 元数据，review 时已实测一致）
- 风险：Paketo 重建 builder 后二者可能漂移；同 stack ID 下一般不阻断，最坏构建期大声失败或运行在旧 run rootfs
- 起因：本机 docker.io 不可达，走 DaocCloud 镜像源，无法便捷查询版本化 tag
- 消除方式：将 builder 与 runImage 成对 pin 到版本化 tag，升级时同步更换
- 来源：review-acm-code F1（WAIVER）
