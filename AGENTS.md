# AGENTS.md

ACM AI Integration 是用于展示 AI + 业务的展示的 mono repo

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