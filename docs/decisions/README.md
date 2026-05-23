# 架构决策记录

这里保存 AI Recorder 的 ADR（Architecture Decision Record）。

当出现以下情况时，新增一条 ADR：

- 改变后台任务执行模型。
- 改变数据库迁移或数据模型策略。
- 新增重要外部依赖。
- 改变部署方式或便携包结构。
- 改变音频、ASR、LLM 调用链路。

## 命名

使用递增编号：

```text
ADR-001-task-execution-model.md
ADR-002-audio-playback-range-api.md
```

## 模板

```markdown
# ADR-001: 决策标题

## 状态
Accepted

## 日期
2026-05-23

## 背景

为什么需要做这个决策。

## 决策

最终选择的方案。

## 备选方案

考虑过但没有采用的方案，以及原因。

## 后果

这个决策带来的收益、成本和后续约束。
```
