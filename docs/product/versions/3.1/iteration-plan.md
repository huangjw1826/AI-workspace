# AI Recorder 3.1 版本迭代计划

## 版本概述

- **版本号**: v3.1.0
- **主题**: 实时体验增强 + 技术架构升级
- **开始日期**: TBD
- **预计周期**: 3-4 周

---

## 阶段一：架构重构（M1）

### 目标
建立 SSE 实时推送基础框架，完善异常处理体系

### 任务清单

| 任务 | 类型 | 依赖 | 工时 |
|------|------|------|------|
| 创建 backend/app/exceptions/ 异常类模块 | 新增 | 无 | 1h |
| 创建 backend/app/middleware/exception_handler.py | 新增 | exceptions | 1h |
| 创建 backend/app/services/sse_service.py | 新增 | exceptions | 2h |
| 创建 backend/app/api/events.py SSE端点 | 新增 | sse_service | 1h |
| 改造 pipeline/workflow.py 支持SSE事件发布 | 改造 | sse_service | 1h |

### 交付物
- 中间代码分支 `feature/sse-backend`

---

## 阶段二：核心功能开发（M2）

### 目标
完成 SSE 前端集成、URL 路由系统

### 任务清单

| 任务 | 类型 | 依赖 | 工时 |
|------|------|------|------|
| 安装前端依赖（zustand, @tanstack/react-query, react-router-dom）| 改造 | 无 | 0.5h |
| 创建 frontend/src/lib/sse.ts SSE客户端 | 新增 | 后端 events | 1h |
| 创建 frontend/src/stores/ 状态管理 | 新增 | sse.ts | 1.5h |
| 创建 frontend/src/hooks/ React Query hooks | 新增 | stores | 1h |
| 改造 App.tsx 路由系统 | 改造 | react-router-dom | 2h |
| 改造 ToastStack.tsx 支持SSE事件 | 改造 | sse.ts, stores | 1h |
| 改造 RecordingDetailPanel.tsx 实时进度 | 改造 | stores | 1h |

### 交付物
- Feature 分支 `feature/sse-frontend`
- Feature 分支 `feature/url-router`

---

## 阶段三：Android 路由（联动）

### 目标
实现 Android DeepLink 支持

### 任务清单

| 任务 | 类型 | 依赖 | 工时 |
|------|------|------|------|
| 添加 AndroidManifest.xml DeepLink 配置 | 改造 | 无 | 0.5h |
| 实现 Android 路由 Navigation | 改造 | manifest | 1h |
| 验证 DeepLink 跳转 | 测试 | navigation | 0.5h |

### 交付物
- Feature 分支 `feature/android-deeplink`

---

## 阶段四：质量加固（M3）

### 目标
安全审计、测试补全、文档完善

### 任务清单

| 任务 | 类型 | 依赖 | 工时 |
|------|------|------|------|
| 安全审计与漏洞修复 | 审计 | 全代码 | 2h |
| Rate Limiting 中间件实现 | 新增 | 无 | 1h |
| 单元测试补全（services） | 测试 | 代码完成 | 3h |
| API 文档更新 | 文档 | 代码完成 | 1h |
| CHANGELOG 更新 | 文档 | 版本确定 | 0.5h |

### 交付物
- Test 分支 `test/3.1-qa`

---

## 阶段五：发布候选（M4）

### 目标
回归测试、Bug修复、发布准备

### 任务清单

| 任务 | 类型 | 依赖 | 工时 |
|------|------|------|------|
| 回归测试执行 | 测试 | M3 完成 | 2h |
| 性能基准测试 | 测试 | M3 完成 | 1h |
| Bug 修复 | 修复 | 测试结果 | 2h |
| 回滚方案验证 | 验证 | 无 | 1h |
| 正式发布打包 | 发布 | Bug 修复完成 | 0.5h |

### 交付物
- RC 分支 `rc/3.1.0`
- Release Tag `v3.1.0`

---

## 里程碑时间线

```
Week 1: M1 架构重构 + M2 前端 SSE 部分
Week 2: M2 URL 路由 + M3 Android + M3 安全
Week 3: M3 测试 + M3 文档 + M4 回归
Week 4: M4 Bug 修复 + 正式发布
```

---

## 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| SSE 连接数过多 | 服务器资源耗尽 | 连接数限制 + 心跳机制 |
| React Router History 模式与 Caddy 配置 | 部署后 404 | 提前准备 Caddy 配置 |
| 说话人分离模型加载慢 | 用户体验下降 | 懒加载 + 后台预热 |
| 移动端 DeepLink 兼容性 | 部分设备失效 | 降级为 Intent URL |