---
name: merge-worktree
description: Use when merging a git worktree
---

## 合并策略

- 使用 rebase 策略合并至本地主分支
- 如果本地主分支不干净，禁止删除、stash 等操作，需让用户确认再合并