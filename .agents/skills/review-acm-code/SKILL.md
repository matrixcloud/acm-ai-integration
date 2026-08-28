---
name: review-acm-code
description: Use when reviewing changes before commiting
---

# Reviewing 待提交变动

使用最高 effort subagent 进行 review，纯文档改动可跳过。

## 执行门禁

找出是否存在以下问题：

| 维度 | 含义 | 判定 |
| --- | --- | --- |
| **Scope Control** | 改动范围是否最小化，没有带进无关变更 | PASS / BLOCK / WAIVER / N/A |
| **Design Fit** | 方案是否与现有架构、状态机、数据流一致 | PASS / BLOCK / WAIVER / N/A |
| **Invariant Clarity** | 不变量是否显式，没有隐式假设和过度防御 | PASS / BLOCK / WAIVER / N/A |
| **Hot Path Perf** | 关键路径是否引入了性能退化 | PASS / BLOCK / WAIVER / N/A |

判定说明：

- `PASS`：自查通过，无需讨论
- `BLOCK`：存在必须修复的问题，修复后才能提交
- `WAIVER`：仅用于用户明确接受的 workaround，必须同步记录到 `../../notes/tech_debt.md`
- `N/A`：本次改动不涉及该维度

## 输出要求

- 按 Findings 的严重程度排序
- 每个问题必须带文件/行号、影响、建议修复方向
- 最后必须给出以下表格，每项只能判定 `PASS` / `BLOCK` / `WAIVER` / `N/A`