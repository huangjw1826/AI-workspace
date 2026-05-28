# AI Recorder 项目结构说明

> 版本：v3.0+ | 最后更新：2026-05-28

## 目录

- [项目概述](#项目概述)
- [整体架构](#整体架构)
- [PC 端结构](#pc-端结构)
  - [后端](#后端)
  - [前端](#前端)
- [Android 端结构](#android-端结构)
- [数据存储](#数据存储)
- [启动脚本](#启动脚本)
- [文档索引](#文档索引)

---

## 项目概述

AI Recorder 是一个面向 Windows 本机的录音整理工具，包含：

- **PC 端**：音频处理、FunASR 离线转写、LLM 智能摘要、Web 界面、目录监控
- **Android 端**：原生客户端，远程访问 PC 端录音库、上传音频、健康监控
- **远程访问**：Cloudflare Tunnel + API Token 认证

### 端级特性对比

| 特性 | PC 端 | Android 端 |
|------|-------|-----------|
| 音频上传 | ✅ | ✅ |
| 查看录音列表/详情 | ✅ | ✅ |
| 转写 | ✅ (FunASR 本地) | ❌ (依赖 PC 端) |
| 摘要生成 | ✅ (LLM) | ❌ (依赖 PC 端) |
| 音频播放 | ✅ | ✅ (流式播放) |
| 转写校对 | ✅ | ❌ |
| 导出 | ✅ | ❌ |
| 健康监控 | ✅ | ✅ |
| 标签管理 | ✅ | ❌ |
| 批量操作 | ✅ | ❌ |
| 目录监控 | ✅ | ❌ |

---

## 整体架构

```
AI-workspace/ (项目根目录)
├── backend/                          # FastAPI 后端 (Python, 46 文件, ~5,500 行)
│   ├── app/
│   │   ├── api/                      # 路由层 (12 模块)
│   │   ├── db/                       # 数据库层
│   │   ├── exceptions/               # 异常定义
│   │   ├── middleware/               # 中间件 (异常处理/安全头)
│   │   ├── models/                   # 数据模型 (7 表, SQLModel)
│   │   ├── pipeline/                 # 工作流编排
│   │   ├── services/                 # 业务逻辑 (10 服务)
│   │   ├── config.py                 # 环境配置 (pydantic-settings)
│   │   └── main.py                   # 应用入口 (FastAPI 工厂)
│   ├── tests/                        # 单元测试 (23 用例)
│   ├── .env.example                  # 环境变量模板
│   └── requirements.txt              # Python 依赖
├── frontend/                         # React 前端 (TypeScript, 28 文件, ~3,100 行)
│   ├── src/
│   │   ├── components/               # 可复用组件 (12 组件, 5 子目录)
│   │   ├── hooks/                    # 自定义 Hooks (2 个)
│   │   ├── lib/                      # 工具函数/API 客户端 (5 文件)
│   │   ├── pages/                    # 页面组件 (4 页面)
│   │   └── stores/                   # 状态管理 (2 Zustand stores)
│   └── vite.config.mjs               # Vite 配置
├── android/                          # Android 原生客户端 (Kotlin, ~47 文件, ~6,500 行)
│   └── app/src/main/java/com/airecorder/android/
│       ├── data/                     # 数据层 (local/model/remote/repository)
│       ├── di/                       # Hilt 依赖注入
│       ├── ui/                       # Compose UI (components/screens/theme/navigation)
│       └── util/                     # 工具类
├── data/                             # 运行时数据目录
│   ├── app.db                        # SQLite 数据库
│   ├── recordings/                   # 上传的原始音频
│   ├── normalized/                   # 归一化音频 (16kHz WAV)
│   ├── transcripts/                  # 转写结果 JSON
│   └── summaries/                    # 摘要结果 Markdown
├── models/                           # FunASR 离线模型缓存
├── logs/                             # 应用日志
├── docs/                             # 项目文档 (26 文件)
│   ├── product/                      # 产品路线图和版本规划
│   ├── decisions/                    # 架构决策记录 (ADR)
│   └── *.md                         # 技术文档
├── scripts/                          # 辅助脚本
├── deploy/                           # 部署配置模板
├── README.md                         # 项目入口文档
├── CHANGELOG.md                      # 变更日志
├── PROJECT_STRUCTURE.md              # 本文件
├── CODE_WIKI.md                      # 代码维基
├── security_audit_report.md          # 安全审计报告
├── *.ps1                             # PowerShell 启动/停止脚本
└── *.bat                             # Batch 启动/停止脚本
```

---

## PC 端结构

### 后端 (backend/)

```
backend/
├── app/
│   ├── api/                         # API 路由层 — 处理 HTTP 请求/响应
│   │   ├── __init__.py              # 包初始化
│   │   ├── auth.py                  # 认证中间件 (LocalBypassToken: 本地免认证 + 远程 API Token)
│   │   ├── events.py                # SSE 事件推送 API
│   │   ├── filesystem.py            # 文件系统 API (Windows 原生文件夹选择器)
│   │   ├── health.py                # 健康检查 API (系统状态/资源使用/隧道状态)
│   │   ├── recordings.py            # 录音管理 API (CRUD/上传/搜索/标签/导出)
│   │   ├── settings.py              # 设置 API (LLM/监控/存储)
│   │   ├── summary.py               # 摘要 API (生成/导出/删除/模板列表)
│   │   ├── tasks.py                 # 任务 API (状态查询/取消)
│   │   ├── tokens.py                # API Token 管理 API (CRUD)
│   │   ├── transcribe.py            # 转写 API (单个/批量)
│   │   └── watch.py                 # 目录监控 API (扫描/事件列表)
│   ├── db/                          # 数据库层
│   │   ├── __init__.py
│   │   └── database.py              # SQLite 连接/初始化/迁移/清理
│   ├── exceptions/                   # 自定义异常
│   │   ├── __init__.py
│   │   └── base.py                  # 异常基类 + 6 种子类 (NotFound/Validation/ServiceError 等)
│   ├── middleware/                   # 中间件
│   │   ├── exception_handler.py     # 统一异常处理 (Trace ID 注入)
│   │   └── security_headers.py      # 安全响应头 (CSP/X-Content-Type-Options 等)
│   ├── models/                      # 数据模型 (SQLModel)
│   │   ├── __init__.py              # 模型导出
│   │   ├── access_log.py            # 访问日志表
│   │   ├── api_token.py             # API Token 表 (设备级 Token 管理)
│   │   ├── recording.py             # 录音记录表 (核心实体)
│   │   ├── summary.py               # 摘要记录表
│   │   ├── task.py                  # 任务记录表
│   │   ├── transcript.py            # 转写片段表
│   │   └── watch_event.py           # 目录监控事件表
│   ├── pipeline/                    # 工作流编排
│   │   ├── __init__.py
│   │   └── workflow.py              # 转写/摘要任务的完整执行流程
│   ├── services/                    # 业务逻辑层
│   │   ├── __init__.py
│   │   ├── asr_service.py           # FunASR 语音转写引擎 (并发控制: BoundedSemaphore)
│   │   ├── audio_service.py         # 音频处理 (FFmpeg 归一化/时长读取)
│   │   ├── docx_export.py           # DOCX 格式生成 (python-docx)
│   │   ├── export_names.py          # 导出文件名规则生成
│   │   ├── file_service.py          # 文件操作 (路径安全校验)
│   │   ├── remote_access.py         # Cloudflare Tunnel 管理 (启动/停止/健康检查)
│   │   ├── runtime_log.py           # 轮转日志配置和错误查询
│   │   ├── sse_service.py           # SSE 事件推送 (任务进度/状态实时通知)
│   │   ├── summary_service.py       # LLM 摘要生成 (6 模板/3 提供商)
│   │   ├── task_service.py          # 任务管理 (创建/复用/恢复/取消)
│   │   └── watch_service.py         # 目录监控 (定时扫描/文件稳定性检测/去重)
│   ├── config.py                    # 环境变量配置 (pydantic-settings, LRU 缓存单例)
│   └── main.py                      # FastAPI 应用工厂 (中间件组装/路由注册/生命周期)
├── tests/                           # 单元测试
│   ├── test_api_auth.py             # 认证中间件测试
│   ├── test_recording_audio.py      # 音频接口测试 (Range 请求)
│   ├── test_recording_management.py # 录音管理测试 (编辑/标签/搜索/导出)
│   └── test_task_service.py         # 任务服务测试 (恢复/复用/取消)
├── .env.example                     # 环境变量模板 (完整配置项)
├── requirements.txt                 # Python 依赖清单
├── start.ps1                        # 后端独立启动脚本
└── PROJECT_STRUCTURE.md             # 后端详细结构文档
```

#### 后端核心模块职责

| 模块 | 路径 | 职责 |
|------|------|------|
| **API 层** | `app/api/` | RESTful API 端点，HTTP 请求/响应处理，参数校验 |
| **数据模型层** | `app/models/` | SQLModel ORM 模型，数据库表结构定义 |
| **业务逻辑层** | `app/services/` | 核心业务逻辑（转写/摘要/音频处理/SSE/任务/监控） |
| **工作流层** | `app/pipeline/` | 任务执行流程编排，阶段进度和取消控制 |
| **中间件层** | `app/middleware/` | 安全头、异常处理、认证鉴权 |
| **数据库层** | `app/db/` | SQLite 连接池、表创建、Schema 迁移 |
| **配置层** | `app/config.py` | 环境变量加载、路径解析、LLM 预设管理 |

#### API 接口速查

| 领域 | 接口 | 方法 |
|------|------|------|
| **录音** | `/api/recordings` | GET(列表) / POST(上传) |
| | `/api/recordings/{id}` | GET(详情) / DELETE |
| | `/api/recordings/{id}/tags` | PATCH |
| | `/api/recordings/{id}/segments/{sid}` | PATCH |
| | `/api/recordings/{id}/audio` | GET(播放) |
| | `/api/recordings/{id}/exports/transcript` | GET(导出) |
| | `/api/recordings/batch-delete` | POST |
| **转写** | `/api/transcribe/{id}` | POST |
| | `/api/transcribe/batch` | POST |
| **摘要** | `/api/summary/{id}` | POST |
| | `/api/summary/batch` | POST |
| | `/api/summary/templates` | GET |
| | `/api/summaries/{id}` | DELETE |
| | `/api/summaries/{id}/export` | GET |
| **任务** | `/api/tasks/{id}` | GET |
| | `/api/tasks/{id}/cancel` | POST |
| **设置** | `/api/settings/llm` | GET / PUT |
| | `/api/settings/llm/test` | POST |
| | `/api/settings/watch` | GET / PUT |
| | `/api/settings/storage` | GET / PUT |
| **监控** | `/api/watch/scan` | POST |
| | `/api/watch/events` | GET |
| **Token** | `/api/tokens` | GET(列表) / POST(创建) |
| | `/api/tokens/{id}` | DELETE(删除) / PUT(启禁) |
| **系统** | `/health` | GET(健康检查) |
| | `/api/events` | GET(SSE 推送) |
| | `/api/pick-folder` | POST(文件夹选择) |

---

### 前端 (frontend/)

```
frontend/
├── src/
│   ├── components/                   # 可复用 UI 组件 (12 个)
│   │   ├── feedback/                 # 反馈类
│   │   │   ├── ConfirmDialog.tsx     # 确认对话框
│   │   │   └── ToastStack.tsx        # Toast 消息堆栈
│   │   ├── layout/                   # 布局类
│   │   │   └── NavBar.tsx            # 顶部导航栏 (页面切换)
│   │   ├── markdown/                 # 内容渲染
│   │   │   └── MarkdownView.tsx      # 自研 Markdown 渲染器
│   │   ├── recording/                # 录音相关
│   │   │   ├── MetricCard.tsx        # 指标卡片 (时长/大小/格式)
│   │   │   ├── RecordingDetailPanel.tsx # 录音详情面板 (最大组件, 287 行)
│   │   │   └── SummaryCard.tsx       # 摘要卡片
│   │   └── ui/                       # 基础 UI 组件
│   │       ├── ExportButtons.tsx     # 导出按钮组
│   │       ├── FolderPicker.tsx      # 文件夹选择器
│   │       ├── InfoCard.tsx          # 信息卡片
│   │       ├── SettingsSection.tsx   # 设置区块
│   │       └── StatusBadge.tsx       # 状态标签
│   ├── hooks/                        # 自定义 Hooks (2 个)
│   │   ├── useRecordings.ts          # 录音数据管理 (React Query)
│   │   └── useSSE.ts                 # SSE 事件订阅
│   ├── lib/                          # 工具函数 (5 个)
│   │   ├── api.ts                    # API 客户端 (25+ 函数)
│   │   ├── format.ts                 # 格式化函数 (时间/大小/状态)
│   │   ├── sse.ts                    # SSE 客户端 (自动重连/指数退避)
│   │   ├── types.ts                  # TypeScript 类型定义 (20+ 接口)
│   │   └── viewTypes.ts             # 视图状态类型
│   ├── pages/                        # 页面组件 (4 个)
│   │   ├── HealthPage.tsx            # 健康面板
│   │   ├── LibraryPage.tsx           # 录音库 (345 行, 最复杂页面)
│   │   ├── SettingsPage.tsx          # 设置页面
│   │   └── WatchPage.tsx             # 目录监控
│   ├── stores/                       # 状态管理 (2 个 Zustand stores)
│   │   ├── appStore.ts               # 应用全局状态
│   │   └── taskStore.ts              # 任务状态管理
│   ├── App.tsx                       # 应用主组件 (809 行, 状态中心)
│   ├── main.tsx                      # 入口文件 (React 挂载)
│   └── styles.css                    # 全局样式
├── index.html                        # HTML 模板
├── package.json                      # 项目元数据和依赖
├── tsconfig.json                     # TypeScript 配置
├── vite.config.mjs                   # Vite 构建配置
└── build.ps1                         # 前端构建脚本
```

#### 前端页面功能

| 页面 | 文件 | 主要功能 |
|------|------|---------|
| **LibraryPage** | `pages/LibraryPage.tsx` | 录音列表展示、搜索/标签筛选、排序、多选、批量操作 |
| **WatchPage** | `pages/WatchPage.tsx` | 监控目录配置、手动扫描、监控事件列表 |
| **SettingsPage** | `pages/SettingsPage.tsx` | LLM 配置、存储路径、监控设置 |
| **HealthPage** | `pages/HealthPage.tsx` | 系统状态、资源使用、隧道状态、最近错误日志 |

---

## Android 端结构

```
android/
├── app/src/main/
│   ├── java/com/airecorder/android/
│   │   ├── data/                          # 数据层
│   │   │   ├── local/                     # 本地存储
│   │   │   │   ├── AudioCacheManager.kt   # 音频缓存
│   │   │   │   ├── CacheHelper.kt         # 缓存辅助
│   │   │   │   └── PreferencesManager.kt  # DataStore 配置
│   │   │   ├── model/                     # 数据模型
│   │   │   │   └── Models.kt              # API 响应数据结构
│   │   │   ├── remote/                    # 远程 API
│   │   │   │   ├── ApiService.kt          # Retrofit 接口定义
│   │   │   │   ├── AuthInterceptor.kt    # Token 认证拦截器
│   │   │   │   ├── SseClient.kt           # SSE 事件客户端
│   │   │   │   └── SseServiceManager.kt   # SSE 服务管理器
│   │   │   └── repository/                # 仓库层
│   │   │       ├── RecordingRepository.kt # 录音数据仓库
│   │   │       ├── SettingsRepository.kt  # 设置数据仓库
│   │   │       └── WatchRepository.kt     # 监控数据仓库
│   │   ├── di/                            # 依赖注入 (Hilt)
│   │   │   └── AppModule.kt               # Hilt 模块 (OkHttp/Retrofit/DataStore)
│   │   ├── ui/                            # UI 层 (Jetpack Compose)
│   │   │   ├── components/                # 可复用组件 (17 个)
│   │   │   │   ├── ActiveFilterTags.kt    # 活跃筛选标签
│   │   │   │   ├── AnimationComponents.kt # 动画组件
│   │   │   │   ├── AudioPlayerBar.kt      # 音频播放器栏
│   │   │   │   ├── BatchOperationBar.kt   # 批量操作栏
│   │   │   │   ├── BottomNavigationBar.kt # 底部导航栏
│   │   │   │   ├── FilterChips.kt         # 筛选芯片
│   │   │   │   ├── MarkdownContent.kt     # Markdown 渲染
│   │   │   │   ├── MetricCard.kt          # 指标卡片
│   │   │   │   ├── MetricCardsRow.kt      # 指标卡片行
│   │   │   │   ├── RecordingItem.kt       # 录音列表项
│   │   │   │   ├── SkeletonLoading.kt     # 骨架屏加载
│   │   │   │   ├── StateComponents.kt     # 状态组件 (加载/空/错误)
│   │   │   │   ├── StatusIndicator.kt     # 状态指示器
│   │   │   │   ├── SummaryListItem.kt     # 摘要列表项
│   │   │   │   ├── ToastManager.kt        # Toast 消息管理
│   │   │   │   └── TranscriptSegmentItem.kt # 转写片段项
│   │   │   ├── navigation/                # 导航
│   │   │   │   └── NavDestinations.kt     # 路由定义
│   │   │   ├── screens/                   # 页面 (5 页面 + ViewModels)
│   │   │   │   ├── LibraryScreen.kt       # 录音库首页
│   │   │   │   ├── LibraryViewModel.kt    # 录音库状态管理
│   │   │   │   ├── DetailScreen.kt        # 录音详情页 (含 Tab)
│   │   │   │   ├── DetailViewModel.kt     # 详情状态管理
│   │   │   │   ├── HealthScreen.kt        # 健康面板
│   │   │   │   ├── SettingsScreen.kt      # 设置页
│   │   │   │   ├── UploadBottomSheet.kt   # 上传底部弹窗
│   │   │   │   ├── detail/                # 详情子页面
│   │   │   │   │   ├── InfoTab.kt         # 信息 Tab
│   │   │   │   │   ├── PlayTab.kt         # 播放 Tab
│   │   │   │   │   ├── SummaryDetailScreen.kt # 摘要详情
│   │   │   │   │   ├── SummaryTab.kt      # 摘要 Tab
│   │   │   │   │   └── TaskTab.kt         # 任务 Tab
│   │   │   │   └── watch/                 # 监控子页面
│   │   │   │       ├── WatchScreen.kt     # 监控页面
│   │   │   │       └── WatchViewModel.kt  # 监控状态管理
│   │   │   ├── theme/                     # 主题
│   │   │   │   ├── Animation.kt           # 动画定义
│   │   │   │   ├── Color.kt               # 色彩系统
│   │   │   │   ├── Shape.kt               # 形状系统
│   │   │   │   ├── Theme.kt               # Material 3 主题
│   │   │   │   └── Type.kt                # 字体样式
│   │   │   ├── animation/                 # 动画
│   │   │   │   └── PageTransitions.kt     # 页面转场动画
│   │   │   └── AIRecorderApp.kt           # 应用入口 Composable
│   │   ├── util/                          # 工具类
│   │   │   ├── AudioPlayerManager.kt      # 音频播放管理
│   │   │   ├── AudioUtils.kt              # 音频工具
│   │   │   ├── FormatUtils.kt             # 格式化工具
│   │   │   └── HapticFeedback.kt          # 触觉反馈
│   │   ├── MainActivity.kt                # 主 Activity
│   │   └── AIRecorderApplication.kt       # Application (Hilt 入口)
│   ├── res/                               # 资源文件
│   │   ├── drawable/                      # 图标
│   │   ├── mipmap-anydpi-v26/            # 自适应图标
│   │   ├── values/                        # 字符串和主题
│   │   └── xml/                           # 备份/数据提取规则
│   └── AndroidManifest.xml                # 应用清单
├── build.gradle.kts                       # 项目级 Gradle 配置
├── settings.gradle.kts                    # Gradle 设置
├── gradle.properties                      # Gradle 属性
├── README.md                              # Android 端快速开始
└── PROJECT_STRUCTURE.md                   # Android 端详细结构
```

#### Android 端架构分层

| 层级 | 目录 | 职责 | 关键组件 |
|------|------|------|---------|
| **UI Layer** | `ui/` | Compose 组件 + ViewModel | LibraryScreen, DetailScreen, HealthScreen, SettingsScreen |
| **Data Layer** | `data/` | Repository + ApiService + DataStore | RecordingRepository, SettingsRepository |
| **DI Layer** | `di/` | Hilt 依赖注入配置 | AppModule (OkHttp, Retrofit, DataStore) |

---

## 数据存储

### 目录结构

```
data/
├── app.db                    # SQLite 数据库 (主存储)
├── recordings/               # 上传的原始音频文件
├── normalized/               # 归一化后的音频 (单声道 16kHz WAV)
├── transcripts/              # 转写结果 (JSON 备份, 按录音 ID 命名)
└── summaries/                # 摘要结果 (Markdown 备份, 按摘要 ID 命名)
```

### 数据库表 (7 张)

| 表名 | 模型文件 | 说明 | 核心字段 |
|------|---------|------|---------|
| `recording` | `models/recording.py` | 录音记录 | filename, status, content_hash, tags |
| `task` | `models/task.py` | 任务记录 | task_type, status, progress, recording_id |
| `transcript_segment` | `models/transcript.py` | 转写片段 | start_time, end_time, text, sequence |
| `summary` | `models/summary.py` | 摘要记录 | mode, content, recording_id |
| `watch_event` | `models/watch_event.py` | 监控事件 | file_path, status, recording_id |
| `access_log` | `models/access_log.py` | 访问日志 | method, path, status_code, token_id |
| `api_token` | `models/api_token.py` | API Token | token, name, is_active, last_used_at |

---

## 启动脚本

| 脚本 | 类型 | 功能 |
|------|------|------|
| `start-all.ps1` | PowerShell | 一键启动：后端 (uvicorn) + 无头隐藏窗口 |
| `stop-all.ps1` | PowerShell | 停止所有服务，清理 PID 文件 |
| `check.ps1` | PowerShell | 环境检查：依赖/端口/配置/服务状态 |
| `package-portable.ps1` | PowerShell | 打包便携版（含 miniconda + node） |
| `start_all.bat` | Batch | 简化启动 (调用 start-all.ps1) |
| `start_backend.bat` | Batch | 仅启动后端 |
| `start_tunnel.bat` | Batch | 仅启动 Cloudflare 隧道 |
| `stop_all.bat` | Batch | 简化停止 (调用 stop-all.ps1) |
| `backend/start.ps1` | PowerShell | 后端独立启动 (开发用) |
| `frontend/build.ps1` | PowerShell | 前端构建 (npm run build) |

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [README.md](README.md) | 项目入口文档 (安装、使用、配置) |
| [CHANGELOG.md](CHANGELOG.md) | 完整变更日志 |
| [CODE_WIKI.md](CODE_WIKI.md) | 代码维基 (类/函数/API 详细说明) |
| [security_audit_report.md](security_audit_report.md) | 安全审计报告 |
| [docs/project-analysis.md](docs/project-analysis.md) | 项目现状分析报告 |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | 完整技术栈说明 |
| [docs/product/roadmap.md](docs/product/roadmap.md) | 产品路线图 |
| [docs/decisions/](docs/decisions/) | 架构决策记录 (ADR) |
| [docs/android-remote-access.md](docs/android-remote-access.md) | Android 远程访问配置 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 故障排查指南 |
| [docs/cloud-llm-providers.md](docs/cloud-llm-providers.md) | LLM 服务商配置 |
| [backend/PROJECT_STRUCTURE.md](backend/PROJECT_STRUCTURE.md) | 后端详细结构 |
| [frontend/PROJECT_STRUCTURE.md](frontend/PROJECT_STRUCTURE.md) | 前端详细结构 |
| [android/README.md](android/README.md) | Android 端快速开始 |
| [android/PROJECT_STRUCTURE.md](android/PROJECT_STRUCTURE.md) | Android 端详细结构 |
