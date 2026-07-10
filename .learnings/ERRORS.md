# Errors

## [ERR-20260324-001] apply_patch

**Logged**: 2026-03-24T00:00:00+08:00
**Priority**: low
**Status**: pending
**Area**: docs

### Summary
`apply_patch` 在更新状态文件时因上下文不匹配失败一次。

### Error
```text
apply_patch verification failed: Failed to find expected lines in /Users/sanbo/code/JNet/task_plan.md
```

### Context
- Command/operation attempted: update status records after analysis work
- Input or parameters used: multi-file patch over `task_plan.md`, `findings.md`, `progress.md`
- Environment details if relevant: repository files had diverged from the assumed patch context

### Suggested Fix
在对长文件做补丁前先重新读取当前内容，再用更精确的上下文块更新。

### Metadata
- Reproducible: no
- Related Files: task_plan.md, findings.md, progress.md

---
