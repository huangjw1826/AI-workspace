# AI Recorder 3.1 版本设计规格

## 版本目标

实时体验增强 + 技术架构升级 + 全链路质量加固

## 核心主题

- WebSocket/SSE 实时任务推送
- URL 路由系统
- 架构规范化（DI + 全局异常）
- 说话人分离实验功能

---

## 一、用户有感知的核心功能

### 1.1 SSE 实时任务推送

#### 体验变化

| 现状 | 3.1 体验 |
|------|----------|
| 手动刷新或每3秒轮询任务状态 | 自动实时推送，页面无刷新 |
| 进度需等待完成后才可见 | Toast 进度阶段实时通知 |
| 用户主动刷新发现任务完成 | 前端弹出 Toast 完成通知 |

#### 技术规格

| 特性 | 实现 |
|------|------|
| **协议** | SSE（Server-Sent Events） |
| **推送阶段** | `task.started` → `task.processing` → `task.completed` / `task.failed` |
| **通知形式** | 页面内 Toast（无需浏览器授权） |
| **断线重连** | 指数退避：1s → 2s → 4s → max 30s |
| **消息格式** | JSON: `{ "event": "task.completed", "data": { "task_id": "...", "recording_id": "..." } }` |

#### 后端改动

- 新增 `backend/app/services/sse_service.py` — SSE 连接管理与事件发布
- 新增 `backend/app/api/events.py` — `/api/events` SSE 端点
- 改造 `backend/app/pipeline/workflow.py` — 任务状态变更发布事件

#### 前端改动

- 新增 `frontend/src/lib/sse.ts` — SSE 客户端封装（享元模式）
- 新增 `frontend/src/stores/taskStore.ts` — Zustand 任务状态管理
- 改造 `frontend/src/components/feedback/ToastStack.tsx` — 支持 SSE 事件触发 Toast
- 改造 `frontend/src/components/recording/RecordingDetailPanel.tsx` — 实时进度展示

#### 验收标准

```
✓ 任务状态变更到 Toast 显示 < 500ms
✓ 断网 30 秒内自动重连
✓ 同时只能有一个 SSE 连接
✓ 后端重启后前端自动重连
```

---

### 1.2 URL 路由系统

#### 体验变化

| 现状 | 3.1 体验 |
|------|----------|
| 无法分享录音页面 | 独立 URL 可直接分享 |
| 刷新页面回到默认页 | 保持当前页面状态 |
| 移动端无法从链接跳转 | Android DeepLink 直接打开录音详情 |

#### 路由设计

| 路由 | 页面 | 参数 |
|------|------|------|
| `/` | 录音库 | `?search=&tag=&status=` |
| `/watch` | 目录监控 | — |
| `/settings` | 设置页 | — |
| `/health` | 健康面板 | — |
| `/recording/:id` | 录音详情 | `id` — 录音 UUID |
| `/recording/:id/transcript` | 转写校对 | `id` |
| `/recording/:id/summary` | 摘要查看 | `id` |

#### 技术规格

| 特性 | 实现 |
|------|------|
| **路由模式** | React Router v6 / History 模式 |
| **分享链接** | `/recording/{uuid}` 无 hash |
| **懒加载** | 路由级 code splitting，首页 < 100KB |
| **权限守卫** | 未登录重定向 |
| **元信息** | 每个路由动态更新 `document.title` |
| **浏览器历史** | 完整支持前进/后退 |

#### Android DeepLink

| 页面 | DeepLink 格式 |
|------|---------------|
| 录音详情 | `airecorder://recording/{id}` |
| 设置页 | `airecorder://settings` |

#### 验收标准

```
✓ 刷新页面保持当前路由
✓ 录音详情可分享链接
✓ 移动端打开分享链接直接跳转对应页面
✓ 浏览器前进/后退正常
```

---

### 1.3 说话人分离（实验性功能）

#### 体验变化

| 现状 | 3.1 体验（开启后） |
|------|-------------------|
| 转写结果为纯文本 | 显示 `发言人1:` `发言人2:` 分段 |
| 校对界面连续文本 | 按说话人分段显示 |
| 无需额外处理 | 增加约 5-10 秒处理时间 |

#### 技术规格

| 特性 | 实现 |
|------|------|
| **模型** | pyannote-audio 3.0 speaker diarization |
| **默认状态** | **开启**（标注为实验性功能） |
| **开关位置** | 设置页「转写设置」区域 |
| **输出格式** | 每段转写增加 `speaker: "发言人1"` 字段 |

#### 用户界面标注

- 设置页该选项标注：**「实验性功能，可能影响处理速度」**
- 功能文档页标注：**「说话人分离为实验性功能，默认开启，当前版本为 Beta」**

#### 验收标准

```
✓ 开启后转写结果包含说话人标签
✓ 关闭后不影响原转写流程
✓ 处理时间增加控制在 15 秒内
```

---

## 二、技术架构改进（用户无感知）

### 2.1 全局异常处理体系

| 异常类 | HTTP 状态码 | 用途 |
|--------|-------------|------|
| `RecordingNotFoundError` | 404 | 录音不存在 |
| `TaskFailedError` | 422 | 任务执行失败 |
| `AudioProcessingError` | 400 | 音频处理错误 |
| `LLMServiceError` | 502 | 大模型服务不可用 |
| `StoragePathError` | 403 | 存储路径无权限 |

- 新增 `backend/app/exceptions/` — 异常类定义
- 新增 `backend/app/middleware/exception_handler.py` — 全局中间件
- 所有 API 返回统一错误格式：`{ "error": "...", "code": "...", "trace_id": "..." }`

### 2.2 依赖注入规范化

- 引入 `dependency-injector`
- 创建 `Container` 统一管理服务实例
- API 层通过容器注入，替代直接实例化

### 2.3 前端状态管理

- 引入 **Zustand** — 全局 UI 状态
- 引入 **TanStack Query** — 服务端状态缓存

---

## 三、安全加固

| 功能 | 说明 |
|------|------|
| Rate Limiting | `/api/*` 限流 60 req/min |
| CORS 配置 | 仅允许配置的域名 |
| 请求 ID 追踪 | 每个请求生成 `X-Request-ID` |
| 敏感操作审计 | 删除、导出操作记录日志 |
| 日志脱敏 | API Token、密钥字段打码 |

---

## 四、兼容性适配

### 浏览器

| 浏览器 | 最低版本 | 测试重点 |
|--------|----------|----------|
| Chrome | 90+ | SSE、WebRTC |
| Edge | 90+ | SSE、WebRTC |
| Firefox | 88+ | SSE |
| Safari | 14+ | SSE、History API |

### Android

| 系统版本 | 最低支持 |
|----------|----------|
| Android | 8.0 (API 26) |
| Jetpack Compose | BOM 2024.01 |

---

## 五、验收标准汇总

| 类别 | 验收条件 |
|------|----------|
| **SSE 推送** | 延迟 < 500ms，断线自动重连，Toast 正常显示 |
| **URL 路由** | 全页面覆盖，分享链接可用，移动端 DeepLink 跳转正常 |
| **说话人分离** | 默认开启，标签正确显示，关闭时无影响 |
| **安全** | 无高危漏洞，敏感信息不泄露 |
| **文档** | API 文档 100% 覆盖，新功能有使用说明 |
| **测试** | 单元测试覆盖率 ≥80%，核心路径 E2E 通过 |

---

## 六、版本信息

| 项目 | 内容 |
|------|------|
| 版本号 | v3.1.0 |
| 发布形式 | Git Tag + Release |
| 文档变更 | CHANGELOG.md、API 文档、用户手册 |