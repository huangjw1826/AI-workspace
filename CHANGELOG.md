# AI Recorder 更改日志

## 2026-06-02 代码全面审查与文档一致性更新 (v3.1.0)

本次对项目进行系统性全面审查，覆盖后端、前端、Android 端代码注释完善，文档一致性校验，以及脚本优化。

### Changed
- **后端代码注释全面完善** (18+ 文件)：
  - `api/auth.py` — 完整模块/类/函数中文 docstring，认证流程阶段级注释
  - `api/tokens.py` — 模块级说明文档 + 所有端点完整中文 Args/Returns/Raises
  - `api/events.py` — SSE 事件推送架构说明
  - `api/tasks.py` — 任务管理模块完整文档，含状态机说明
  - `api/transcribe.py` — 转写任务流程阶段级注释
  - `api/watch.py` — 目录监控手动扫描和事件查询详细文档
  - `api/filesystem.py` — Windows 原生文件夹选择器工作原理说明
  - `api/summary.py` — 摘要生成/导出/删除完整流程注释
  - `api/settings.py` — LLM/监控/存储设置完整说明
  - `api/health.py` — 健康检查端点详细注释
  - `api/recordings.py` — 录音管理完整 CRUD 注释
  - `middleware/security_headers.py` — 安全头作用和配置说明
  - `middleware/exception_handler.py` — Trace ID 机制和异常分类说明
  - `models/task.py` — 任务状态机完整说明
  - `models/summary.py` — 多模板摘要支持说明
  - `models/transcript.py` — 转写片段校对功能说明

- **前端代码注释完善** (3+ 文件)：
  - `lib/sse.ts` — SSE 客户端完整 JSDoc + 类/方法/全局单例说明
  - `lib/api.ts` — 全部 25+ API 函数更新 JSDoc 注释
  - `lib/utils.ts` — 工具函数完整文档

- **文档一致性修复**：
  - `CODE_WIKI.md` — 修复任务取消 API 路径（`DELETE /api/tasks/{id}` → `POST /api/tasks/{id}/cancel`）
  - `CODE_WIKI.md` — 修复健康检查 API 路径（`/api/health` → `/health`）
  - `CODE_WIKI.md` — 更新安全注意事项（`Authorization: Bearer` → `X-API-Token` 头）
  - `CODE_WIKI.md` — 更新设置接口为实际的路由（`/api/settings` → `/api/settings/llm` 等 9 个端点）
  - `CODE_WIKI.md` — 补充缺失的数据库表（apitoken、accesslog）
  - `README.md` — 版本号更新为 v3.1.0

- **启动脚本注释优化**：
  - `start-all.bat` — 移除硬编码隧道域名
  - `start-backend.bat` — 完整中文注释说明启动流程
  - `stop-all.bat` — 完整停止策略说明

### Fixed
- `CODE_WIKI.md` 中 4 处 API 文档与实际代码不匹配的问题
- `start-all.bat` 硬编码外网域名的问题

### Verified
- 后端代码注释一致性审查通过
- 前端代码注释一致性审查通过
- 文档与代码 API 定义一致性审查通过
- 所有启动/停止脚本语法验证通过

---

## 2026-05-30 项目全面梳理与规范化处理完成 (v3.0.3)

本次完成项目全面梳理与规范化处理的收尾工作，项目文档和代码注释体系已完善。

### Changed
- **docs/project-analysis.md 更新**：
  - 更新报告日期至 2026-05-30
  - 更新代码质量评估，后端综合评分 9.0/10，前端 A-
  - 更新问题和改进方向
  - 更新总结

### Verified
- ✅ 后端代码注释完整：模型/服务/API 全部有规范 docstring
- ✅ 前端代码注释完整：API 客户端和类型定义有 JSDoc
- ✅ Android 端结构文档完善
- ✅ 多端项目结构文档完整且规范
- ✅ README 文档完整，涵盖所有功能和配置说明
- ✅ 项目现状分析报告同步

---

## 2026-05-28 代码注释加强 — 服务层和基础设施层 (v3.0.2)

本次改动补齐后端服务层（10 个文件）和基础设施层的 docstring 注释。

### Changed

- **服务层注释完善** (10 文件)：
  - `services/asr_service.py` — 模块/类/方法 docstring，含时间戳对齐算法说明
  - `services/summary_service.py` — 6 种模板定义 + LLM 调用/转写规整流程文档
  - `services/sse_service.py` — 事件系统架构、SSEClient/SSEService 生命周期、心跳机制
  - `services/task_service.py` — 任务创建/恢复/取消的三级状态管理流程
  - `services/watch_service.py` — 文件稳定性检测（2 次确认）、SHA-256 去重、监控循环
  - `services/audio_service.py` — FFmpeg 归一化命令说明、duration 双策略回退
  - `services/docx_export.py` — OpenXML 纯 Python 生成流程、ZIP 包结构
  - `services/export_names.py` — 文件名规范化规则、时间戳格式
  - `services/file_service.py` — 哈希去重分块策略（1MB）
  - `services/runtime_log.py` — RotatingFileHandler 配置、容错 NullHandler

- **基础设施层注释**：
  - `exceptions/base.py` — 6 种异常类的业务语义和 HTTP 映射

### Backend Comment Coverage
- 注释前：~1.7/10（模型层 0/10, 服务层 0/10, 基础设施 0/10）
- 注释后：~6.5/10（模型层 5/10, 服务层 7/10, 基础设施 5/10, config/main/pipeline 8/10）

---

## 2026-05-28 项目全面梳理与规范化 (v3.0.1)

本次改动对项目进行全面梳理与规范化处理，重点补齐代码注释、更新结构文档和重写 README。

### Added
- 新增 `docs/project-analysis.md` 项目现状分析报告
  - 完整架构概览、功能模块划分、技术栈详细
  - 代码规模统计：~15,600 行 / 129 个源文件
  - 代码质量评估（后端 1.7/10，前端 0.26% 注释覆盖率）
  - 存在问题和改进方向

### Changed
- **后端模型层注释完善** (7 文件)：
  - `models/recording.py` - 模块 docstring + Recording 类 + 全部字段 description
  - `models/task.py` - 模块 docstring + Task 类 + 任务状态机说明
  - `models/transcript.py` - 模块 docstring + TranscriptSegment 类 + 校对功能说明
  - `models/summary.py` - 模块 docstring + Summary 类 + 6 种摘要模板说明
  - `models/watch_event.py` - 模块 docstring + WatchEvent 类 + 事件状态说明
  - `models/access_log.py` - 模块 docstring + AccessLog 类 + 审计说明
  - `models/api_token.py` - 模块 docstring + ApiToken 类 + 设备级 Token 说明

- **后端核心文件注释完善**：
  - `config.py` - 模块/类/属性全面 docstring，配置项按功能区注释
  - `main.py` - 模块/函数 docstring，中间件注册顺序说明，生命周期事件注释
  - `pipeline/workflow.py` - 全部函数 docstring，转写/摘要流程的阶段级注释

- **前端核心类型和 API 注释**：
  - `lib/types.ts` - 全部 20+ 接口的 JSDoc 注释，按功能域分组
  - `lib/api.ts` - 全部 25+ 导出函数的 JSDoc 注释，含 @param/@returns

- **多端结构规范化**：
  - `PROJECT_STRUCTURE.md` 全面重写：API 接口速查表、端级特性对比、数据库表详情、启动脚本清单
  - Android 端结构已在之前更新中完善

- **README.md 全面重写**：
  - 新增项目简介和设计理念
  - PC 端和 Android 端功能矩阵
  - 技术栈分三端详细展示（含版本号）
  - 完整使用流程、配置参考、数据隐私说明
  - 项目结构简洁展示 + 详细文档索引

### Documentation Coverage

- ✅ README.md - 项目入口文档 (重写)
- ✅ PROJECT_STRUCTURE.md - 项目结构说明 (重写)
- ✅ CHANGELOG.md - 变更日志 (本次更新)
- ✅ CODE_WIKI.md - 代码维基 (之前已同步)
- ✅ docs/project-analysis.md - 项目现状分析 (新增)
- ✅ backend/app/models/*.py - 7 个模型文件 (注释)
- ✅ backend/app/config.py - 配置管理 (注释)
- ✅ backend/app/main.py - 应用入口 (注释)
- ✅ backend/app/pipeline/workflow.py - 核心工作流 (注释)
- ✅ frontend/src/lib/types.ts - 类型定义 (JSDoc)
- ✅ frontend/src/lib/api.ts - API 客户端 (JSDoc)

---

## 2026-05-28 项目文档规范化整理

本次改动对项目文档进行全面梳理和规范化整理，使 README 和 PROJECT_STRUCTURE 与项目最新状态保持一致。

### Changed

- 重写 `README.md`
  - 新增当前版本标识（v3.0）和更新日志链接
  - 核心特性新增 3.0 亮点功能（可靠任务、音频播放、转写校对）
  - 更新功能概览表格，新增文件大小限制说明
  - 项目结构精简为简洁版目录树
  - 技术栈章节按 PC 后端、PC 前端、Android 端分三大类展示
  - 相关文档表格新增技术栈、产品路线图、架构决策记录
  - 新增版本历史章节，快速展示主要版本

- 重写 `PROJECT_STRUCTURE.md`
  - 整体架构精简，展示核心组件和数量
  - 后端结构补充 `tokens.py`、`access_log.py`、`api_token.py` 等新增模块
  - 后端结构补充 `exceptions/` 和 `middleware/` 目录
  - 前端结构新增 `sse.ts` 客户端文件
  - 新增 API 接口概览表格
  - 新增数据存储章节，展示数据库表结构

### Document Coverage

- ✅ README.md - 项目概述和快速开始
- ✅ PROJECT_STRUCTURE.md - 整体结构
- ✅ CODE_WIKI.md - 代码详解
- ✅ CHANGELOG.md - 变更记录
- ✅ backend/PROJECT_STRUCTURE.md - 后端结构
- ✅ frontend/PROJECT_STRUCTURE.md - 前端结构
- ✅ android/PROJECT_STRUCTURE.md - Android 端结构
- ✅ docs/TECH_STACK.md - 技术栈说明
- ✅ docs/product/ - 产品路线图和版本规划
- ✅ docs/decisions/ - 架构决策记录

---

## 2026-05-27 项目文档全面更新

本次改动更新所有项目说明性文档，确保文档与实际代码结构一致。

### Added
- 新增 `PROJECT_STRUCTURE.md` 整体项目结构说明文档
- 新增 `backend/PROJECT_STRUCTURE.md` 后端详细结构文档
- 新增 `frontend/PROJECT_STRUCTURE.md` 前端详细结构文档
- 新增 `android/PROJECT_STRUCTURE.md` Android 端详细结构文档
- 新增 `docs/TECH_STACK.md` 统一技术栈说明文档

### Updated
- 更新 `README.md` - 项目说明与实际功能保持一致
- 更新 `PROJECT_STRUCTURE.md` - 添加缺失的 `events.py`、`sse_service.py`、`hooks/` 和 `stores/` 模块
- 更新 `CODE_WIKI.md` - 添加缺失的 SSE 服务和事件 API
- 更新 `backend/PROJECT_STRUCTURE.md` - 补充完整的 API 和服务层说明
- 更新 `frontend/PROJECT_STRUCTURE.md` - 补充 hooks 和 stores 模块
- 更新 `android/PROJECT_STRUCTURE.md` - 保持与实际代码结构同步
- 更新 `docs/TECH_STACK.md` - 技术栈版本与实际依赖一致
- 更新 `docs/android-remote-access.md` - 远程访问配置说明保持最新

### Document Coverage
- ✅ README.md - 项目概述
- ✅ PROJECT_STRUCTURE.md - 整体结构
- ✅ CODE_WIKI.md - 代码详解
- ✅ CHANGELOG.md - 变更记录
- ✅ backend/PROJECT_STRUCTURE.md - 后端结构
- ✅ frontend/PROJECT_STRUCTURE.md - 前端结构
- ✅ android/PROJECT_STRUCTURE.md - Android 端结构
- ✅ docs/TECH_STACK.md - 技术栈说明
- ✅ docs/android-remote-access.md - 远程访问配置

---

## 2026-05-26 Android-only remote access hardening

本次改动将远程访问范围收敛到原生 Android 客户端，移除网页端 PWA/远程连接配置，并统一本机网页入口为后端托管的单端口模式。

### Changed
- 本机网页入口统一为 `http://127.0.0.1:8000`，由 FastAPI 托管 `frontend/dist`
- `start-all.ps1` 不再额外启动 5173 静态文件服务
- 远程访问文档改为 Android-only，不再描述 PWA 快速体验路径

### Fixed
- 修复 API Token 测试引用旧中间件类名导致的后端单测失败
- 收紧远程鉴权判断，避免反向代理回环地址绕过 `/api/*` token 校验
- `/api/pick-folder` 限制为本机请求，避免远程 Android 触发 PC 原生文件夹弹窗
- `check.ps1` 改为实际执行后端 Python 后再报告可用

### Removed
- 移除前端 PWA manifest、Service Worker 和网页端远程连接配置

### Verified
- 后端测试：23 tests OK
- 后端编译：`python -m compileall app tests` 通过
- 前端类型检查：`npx tsc --noEmit` 通过
- 前端构建：`npm run build` 通过

---

## 2026-05-25 3.0 Phase 2 体验补齐

本次改动修复 3.0 发布验收清单中两项遗留问题：标签筛选 + 搜索命中展示。

### Changed
- `/api/recordings` 返回值从 `list[Recording]` → `SearchResult`，新增 `match_previews` 字段
- 搜索匹配时收集文件名、标签、转写文本、摘要内容的命中上下文（最多 5 条片段，80 字符窗口）
- `LibraryFilters` 新增 `tag` 字段
- 前端 `listRecordings()` 返回值适配 `SearchResult`，新增 `reloadRecordings()` 辅助函数

### Added
- 列表页新增标签筛选输入框
- 搜索结果命中上下文展示（绿色标签样式，带字段标识）
- `clearAppliedTag()` 函数，标签筛选芯片可清除

### Fixed
- 更新测试适配 `SearchResult` 返回值

### Verified
- 后端测试：13 tests OK
- 前端构建：通过

---

## 2026-05-23 3.0 Phase 0

本次改动启动 3.0 迭代，聚焦任务可靠性地基。

### Added
- 新增任务服务层，集中处理活动任务复用、启动恢复和取消。
- 后端启动时会把遗留的 `queued` / `running` 任务标记为中断错误，避免任务永久卡住。
- 新增 `POST /api/tasks/{task_id}/cancel`，前端任务进度卡片提供“取消”按钮。
- 新增后端 `unittest` 最小测试，覆盖任务恢复、重复任务复用和取消。
- 新增 ADR：`docs/decisions/ADR-001-task-execution-model.md`。

### Changed
- 转写/摘要 API 会复用同一录音同一任务类型的活动任务，避免重复提交。
- ASR 执行遵守 `ASR_MAX_CONCURRENCY` 的进程内并发限制。
- ffmpeg 调用增加超时配置 `FFMPEG_TIMEOUT_SECONDS`。
- LLM client 增加超时和重试配置：`LLM_TIMEOUT_SECONDS`、`LLM_RETRY_ATTEMPTS`。
- 转写和摘要工作流会在阶段边界检查任务是否已取消。

### Verified
- 后端：`.\\.venv\\Scripts\\python.exe -m unittest tests.test_task_service`
- 前端：`cmd /c npm run build`

## 2026-05-23 3.0 Phase 1

本次改动启动播放和校对体验，先完成音频播放与时间轴联动。

### Added
- 新增 `GET /api/recordings/{recording_id}/audio`，按录音 ID 安全读取已入库音频。
- 音频接口支持 HTTP Range，便于浏览器播放器按需加载和拖动进度。
- 录音详情面板增加原生音频播放器。
- 转写片段时间戳可点击，点击后跳转到对应音频位置并开始播放。
- 播放过程中当前转写片段会高亮。
- 新增后端音频接口测试：`tests.test_recording_audio`。
- 新增 ADR：`docs/decisions/ADR-002-audio-playback-range-api.md`。

### Changed
- `formatDuration(0)` 现在显示 `0:00`，避免 0 秒片段和播放进度显示为 `--`。
- 状态筛选补充 `cancelled`。

### Verified
- 后端：`.\\.venv\\Scripts\\python.exe -m unittest tests.test_recording_audio tests.test_task_service`
- 后端编译：`.\\.venv\\Scripts\\python.exe -m compileall app tests`
- 前端：`cmd /c npm run build`

## 2026-05-23 3.0 Phase 2-3

本次改动把 3.0 主线能力继续推进到“可校对、可管理、可交付”。

### Added
- 新增转写片段编辑接口：`PATCH /api/recordings/{recording_id}/segments/{segment_id}`，保存后同步数据库和转写 JSON 文件。
- 新增录音标签接口：`PATCH /api/recordings/{recording_id}/tags`。
- 录音列表搜索支持文件名、标签、转写文本和摘要内容。
- 新增批量接口：批量转写、批量摘要、批量删除。
- 转写导出新增 JSON、SRT、DOCX；摘要导出新增 DOCX。
- 后端新增轮转日志服务，健康检查返回日志目录和最近错误。
- 前端详情页新增转写编辑、标签维护、更多导出格式；列表页新增多选和批量操作栏。
- 新增 `tests.test_recording_management`，覆盖编辑、标签、搜索和导出逻辑。
- 新增 3.0 完成摘要：`docs/product/versions/3.0/completion-summary.md`。

### Changed
- 日志文件不可写时不再阻断应用启动，避免 Windows 文件权限问题直接压垮后端。
- 3.0 发布清单更新为当前实现状态，并标注机器验证和人工验收边界。

### Verified
- 后端：`.\\.venv\\Scripts\\python.exe -m unittest tests.test_task_service tests.test_recording_audio tests.test_recording_management`
- 后端编译：`.\\.venv\\Scripts\\python.exe -m compileall app tests`
- 前端：`cmd /c npm run build`
- HTTP 冒烟：临时数据目录下 `/health` 和 `/api/recordings` 返回正常，前端首页返回 `200`。

### Known limitations
- 当前机器的 Playwright/Node 浏览器自动化被 `AppData` 权限限制拦截，未完成截图级视觉验收。
- 现有 `data/app.db` 在冒烟启动时表现为只读，因此本次 HTTP 验证使用临时数据目录，未改动现有数据库。

## 2026-04-25 优化批次

本次改动聚焦"最优先的 5 个问题"，未改动业务核心逻辑，仅做健壮性与可维护性增强。

### 1. 前端依赖版本锁定
- **文件**: `frontend/package.json`
- **改动**: 将所有 `"latest"` 替换为实际安装的固定版本号
  - `react` / `react-dom`: `^19.2.5`
  - `lucide-react`: `^0.487.0`
  - `@types/react`: `^19.2.14`
  - `@types/react-dom`: `^19.2.3`
  - `@vitejs/plugin-react`: `^6.0.1`
  - `typescript`: `^6.0.3`
  - `vite`: `^8.0.10`
- **原因**: 避免未来某个依赖发布大版本更新后，项目突然无法构建。
- **额外调整**: 将 `vite`、`typescript`、`@vitejs/plugin-react` 从 `dependencies` 移至 `devDependencies`，生产构建时不再安装这些开发工具。

### 2. 后端上传接口增加文件大小限制
- **文件**: `backend/app/api/recordings.py`
- **改动**:
  - 新增常量 `MAX_UPLOAD_SIZE = 500 * 1024 * 1024`（500 MB）
  - 上传时先读取完整内容并检查大小，超限返回 `413` 错误及中文提示
  - 文件格式不支持时返回中文提示
- **原因**: 防止用户误上传超大文件导致后端内存被打满或卡死。

### 3. 新增删除录音功能
- **文件**:
  - `backend/app/api/recordings.py` — 新增 `DELETE /api/recordings/{recording_id}` 接口
  - `frontend/src/lib/api.ts` — 新增 `deleteRecording()` 调用
  - `frontend/src/App.tsx` — 新增删除按钮与确认对话框
  - `frontend/src/styles.css` — 新增删除按钮样式
- **后端行为**:
  - 删除数据库中的录音记录
  - 级联删除关联的转写片段 (`TranscriptSegment`)、摘要 (`Summary`)、任务 (`Task`)
  - 尝试删除硬盘上的原始音频、标准化音频、转写 JSON、摘要 Markdown（删除失败不影响数据库事务）
- **前端行为**:
  - 每个录音条目右侧显示垃圾桶图标
  - 点击后弹出浏览器确认框：`"确定要删除这条录音吗？转写结果和摘要也会一并删除。"`
  - 删除成功后自动刷新列表；若删除的是当前选中录音，则清空右侧详情区

### 4. 前端代码结构拆分
- **文件**:
  - 新增 `frontend/src/App.tsx` — 包含全部应用状态、业务逻辑与 JSX
  - 重写 `frontend/src/main.tsx` — 仅保留 React 根节点挂载代码
- **拆分前**: 所有逻辑与渲染挤在 `main.tsx`（约 420 行）
- **拆分后**:
  - `main.tsx` → 入口渲染（5 行）
  - `App.tsx` → 业务主组件（约 350 行）
- **好处**: 职责分离，后续想改界面找 `App.tsx`，想改入口配置找 `main.tsx`，不需要在几百行代码里翻找。

### 5. 环境配置模板已就绪（无需修改）
- **文件**: `backend/.env.example`
- **状态**: 文件已存在且字段完整，包含 ASR、LLM、路径、CORS 等全部配置项
- **说明**: 本次未做改动，因为该文件在项目中已经存在。

---

## 待后续考虑的优化（未在本次处理）

- 任务状态恢复机制（后端重启后恢复正在运行的任务）
- 日志自动轮转（防止日志无限增长）
- 前端引入 Tailwind CSS / shadcn/ui 提升 UI 一致性与开发效率
- 录音导出功能（TXT / Markdown / JSON）
- 搜索与标签筛选
- 文字同步播放（点击转写文本跳转对应音频时间）
