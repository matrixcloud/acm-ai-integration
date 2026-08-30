# 2026-08-30 质量关卡方案与垂直切片验证

## 目标

为 ACM monorepo 建立五关卡 + CI 合入闸门的质量门禁体系。单一事实源挂 Gradle `check`，本地跑模块子集，CI 全量重跑。

## 原则

1. 门禁单一事实源：全部挂 `check`，本地跑模块子集，CI 跑全集。
2. CI 不信任本地，全量重跑；required checks 才是合入闸门。
3. 本地 hook 只放秒级自动修复；其余本地算力给 agent 修复循环。
4. 阈值是底线不是目标，只升不降。
5. 任何关卡红了先修，不豁免。

## 关卡清单

| 关 | 内容 | 工具 | 当前状态 |
|---|---|---|---|
| ① 行为规格 | 任务合同 = 先写失败单测（替代 Gherkin） | JUnit 6 | 工作流约定 |
| ② 单测 + 覆盖率 | 底线报警，不进 AI 提示词 | JaCoCo | ✅ 已验证 |
| ③ 变异测试 ★ | 故意注 bug 验假测试，只打 A 级模块 | PIT | ❌ JUnit 6 阻断 |
| ④ 静态约束 | 格式/lint + 类型缺陷 + 依赖安全 | Spotless/Checkstyle/SpotBugs/NullAway/OWASP | ⚠️ 部分验证 |
| ⑤ 属性测试 | 随机输入轰炸不变量，只打核心，后置 | jqwik | ❌ JUnit 6 阻断 |
| ⑥ 合入闸门 | 全绿才合入 | CI required checks | 未落地 |

前后端映射：

- 后端：`check = test + jacoco + checkstyle + spotbugs`；`qa = check + pitest(改动模块)`；`nightly = pitest(全量) + dependencyCheckAggregate`。
- 前端（webapps）：② Vitest+v8 coverage 60；④ eslint+tsc+prettier；⑥ 进同一 qa 流程；③⑤ 暂缓。

## 模块分级

| 级别 | 模块 | 关卡 |
|---|---|---|
| A 业务服务 | customer-agent、order-svc、kb-svc | ①②③④⑤ |
| B 小服务 | gateway-svc、registry-svc | ②④ |
| C 共享库 | common-lib | ②④（80%） |
| D 前端 | webapps/* | ②④⑥ |

## 上策垂直切片验证（order-svc，2026-08-30 实测）

以 order-svc 为样板全关卡打通，验证工具接入 + 暴露盲区 + 定性报错。

### 确定版本（Gradle 9.5.1）

| 工具 | 版本 | 来源 |
|---|---|---|
| SpotBugs gradle plugin | 6.5.11 | plugin portal（中央旧坐标 404） |
| PIT gradle plugin | 1.19.0 | plugin portal（中央停 1.15.0，陈旧） |
| jqwik | 1.10.1 | Maven Central |
| NullAway | 0.14.1 | Maven Central |

PIT core 最新 1.30.0（2026-08），但 junit5-plugin 停在 1.2.3（2025-05）。

### 关键发现：JUnit 6 系统性阻断 ③⑤

Spring Boot 4.1.1 锁死 **JUnit 6.0.3**（jupiter + platform 均 6.0.3）。这是 2026 breaking change，JUnit 5 生态测试工具尚未跟进：

- **PIT**：`pitest-junit5-plugin 1.2.3`（最新，2025-05）pom 写死 `junit 5.9.2 / junit-platform 1.9.2`，coverage 阶段 `NoSuchMethodError` 崩溃。
- **jqwik**：`1.10.1`（最新，2026-05）依赖 platform 1.14.4，`ExecutionRequest.create(…, NamespacedHierarchicalStore)` 五参签名在 platform 6 已变。

两者同源硬不兼容，非缺注解或配置。

### 验证结果

| 关 | 状态 | 证据 |
|---|---|---|
| ② 覆盖率 | ✅ | 核心包 LINE 0.85/BRANCH 0.75 达标；merged 0.80/0.70 达标；integrationTest（Testcontainers+Postgres）本地 ~14s 跑通 |
| ④ Checkstyle | ✅ | 接入成功；修复 `log` 字段 `ConstantName` 误报（SLF4J 惯例） |
| ④ SpotBugs | ⚠️ | 接入成功，报 5 个存量债（见下） |
| ③ PIT | ❌ | blocked（上述） |
| ⑤ jqwik | ❌ | blocked（上述） |
| ④ NullAway | ⏸ | 未接入，ErrorProne 插件坐标需单独查证 |

## 已落地改动

1. `config/checkstyle/checkstyle.xml` — 语义规则集（命名/imports/语义陷阱 + 禁 `System.out`/`printStackTrace`）。与 Spotless 严格分工：格式归 googleJavaFormat，此处零格式规则。
2. `order-svc/build.gradle` — 加 `checkstyle`、`com.github.spotbugs 6.5.11`、`info.solidsoft.pitest 1.19.0`；`spotbugs effort=max`；`pitest targetClasses=domain+application, mutationThreshold=65`。

## 存量债（切片暴露，待横向复制时清理）

### SpotBugs RV：size() 触发 Hibernate 懒加载（×4）

- 位置：`OrderService.java:140,142`、`OrderLifecycleService.java:314,317` 的 `initializeDetails` 用 `orders.getX().size()` 触发集合懒加载
- 性质：真实技术债，应改 `Hibernate.initialize()` 或 `@EntityGraph`

### SpotBugs IS：failureRegistry 同步不一致（×1）

- 位置：`InventoryClientImpl.java:38`，读端 `synchronized`、写端 `@Autowired setter` 非同步
- 性质：Spring 单线程注入，实际 false positive；setter 加 `synchronized` 或改构造器注入更干净

## 决策记录（2026-08-30）

1. **③⑤ 暂缓**：等 PIT/jqwik 适配 JUnit 6，不降级 JUnit。方案本就标 ⑤「后置」，PIT/jqwik 为活跃项目。
2. **SpotBugs 债先记录**：随横向复制一起清，不在切片阶段改业务代码。
3. **阈值基准沿用现状**：核心包 0.85/0.75、merged 0.80/0.70（order-svc/customer-agent 既有），不新造更低值。

## 下一步路线图

1. **横向复制**：把 order-svc 验证通过的 checkstyle/spotbugs/jacoco 约定提取到根 `build.gradle`（`subprojects` 分发），kb-svc 补齐 jacoco/integrationTest。
2. **CI**：写 `qa.yml`（后端 `./gradlew qa` + 前端 `pnpm -r lint typecheck test`），配 branch protection。
3. **前端补齐**：`typecheck` 脚本、Vitest coverage 阈值、prettier。
4. **③⑤ 回归**：PIT/jqwik 适配 JUnit 6 后接入（配置已就位于 order-svc build.gradle）。
5. **NullAway**：单独立项评估（ErrorProne 版本 + 编译期成本）。