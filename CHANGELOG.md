# AI Recorder 更改日志

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
