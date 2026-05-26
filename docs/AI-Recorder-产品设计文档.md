# AI Recorder 产品设计文档

> **文档类型**: 反向产品设计文档（Reverse PRD）
> **分析基线**: 2026-05-23，包含 3.0 Phase 0 + Phase 1 已完成功能及全部设计蓝图
> **分析方法**: 基于完整项目源码、文档、架构决策记录、迭代计划和发布清单反推

---

## 目录

1. [产品概述](#1-产品概述)
2. [目标用户与使用场景](#2-目标用户与使用场景)
3. [产品架构](#3-产品架构)
4. [功能模块设计](#4-功能模块设计)
5. [数据模型设计](#5-数据模型设计)
6. [API 接口设计](#6-api-接口设计)
7. [前端交互设计](#7-前端交互设计)
8. [技术架构决策](#8-技术架构决策)
9. [部署与运维设计](#9-部署与运维设计)
10. [产品路线图](#10-产品路线图)
11. [安全与隐私设计](#11-安全与隐私设计)
12. [非功能需求](#12-非功能需求)
13. [附录](#13-附录)

---

## 1. 产品概述

### 1.1 产品定位

AI Recorder 是一款**面向 Windows 本机使用的录音整理工作台**。它将音频入库、本地离线语音转写、云端大模型智能摘要、多格式导出和目录监控整合为一个网页应用，帮助个人用户高效整理会议录音、访谈、课堂笔记和日常语音记录。

### 1.2 核心价值主张

| 价值维度 | 具体实现 |
| --- | --- |
| **数据主权** | 音频和转写数据本机存储，转写全离线完成，不上传原始音频至云端 |
| **智能加工** | 通过可配置的云端大模型（DeepSeek / 通义千问 / 小米 MiMo）生成摘要、纪要、待办 |
| **端到端闭环** | 从音频导入到结构化输出（MD/TXT/JSON/SRT/DOCX），一条链路覆盖 |
| **开箱即用** | 一键启动（`start-all.ps1`），无需 Docker/Redis/数据库服务器 |
| **可配置隐私** | 云端 LLM 需要用户自行配置 API Key，转写本身不需要任何网络连接 |

### 1.3 产品形态

- **部署形态**: 本地 Windows 单机应用（10/11）
- **用户界面**: 浏览器访问 `http://127.0.0.1:8000`，由 FastAPI 托管 React SPA
- **后台服务**: FastAPI + Uvicorn 本地 HTTP 服务（端口 8000）
- **数据存储**: SQLite 本地数据库 + 本地文件系统
- **可选打包**: 便携式文件夹（`package-portable.ps1`），可整体复制到其他 PC 使用

---

## 2. 目标用户与使用场景

### 2.1 目标用户画像

| 角色 | 典型场景 | 核心需求 |
| --- | --- | --- |
| **职场人士** | 会议录音、客户访谈 | 自动生成会议纪要、提取待办事项 |
| **学生/研究者** | 课堂录音、田野调查 | 长录音批量转写、关键结论提炼 |
| **内容创作者** | 播客录制、脚本口述 | 快速转写为文字稿、SRT 字幕导出 |
| **自由职业者** | 多语种对话、商务谈判 | 转写校对、决策风险提取 |

### 2.2 典型使用流程

```
录音获取 → 音频归一化 → 语音转文字 → 文本校对 → 智能摘要 → 导出交付
   │           │             │            │          │
   │    ffmpeg 转 16k       FunASR     人工编辑   云端 LLM
   │    wav/mono           CPU 推理   时间轴联动  多模板可选
   │
上传/目录监控
```

### 2.3 使用场景矩阵

| 场景 | 音频长度 | 关键功能 | 摘要模板推荐 |
| --- | --- | --- | --- |
| 日常站会 | 5-15 分钟 | 快速转写+待办提取 | `action_items` |
| 正式会议 | 30-90 分钟 | 转写校对+会议纪要 | `meeting_minutes` |
| 客户访谈 | 20-60 分钟 | 全文检索+结构化摘要 | `structured_summary` |
| 讲座/课程 | 45-120 分钟 | 批量管理+内容规整 | `polished_transcript` |
| 个人备忘录 | 1-5 分钟 | 快速记录+管理层简报 | `executive_brief` |

---

## 3. 产品架构

### 3.1 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      浏览器 (127.0.0.1:8000)                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │             React SPA (Vite + TypeScript)             │   │
│  │  ┌────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │   │
│  │  │录音库页 │ │ 设置页   │ │目录监控页│ │健康检查页│  │   │
│  │  │列表/详情│ │LLM/存储  │ │扫描/事件 │ │系统状态  │  │   │
│  │  └────────┘ └──────────┘ └──────────┘ └──────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP REST + 轮询 (1.5s)
┌───────────────────────────▼─────────────────────────────────┐
│                 FastAPI 后端 (127.0.0.1:8000)                │
│  ┌──────────┬──────────┬───────────┬──────────┬─────────┐  │
│  │ 健康检查 │ 录音API  │ 转写API   │ 摘要API  │ 设置API │  │
│  │ /health  │ /api/    │ /api/     │ /api/    │ /api/   │  │
│  │          │ recordings│ transcribe│ summary  │ settings│  │
│  └──────────┴──────────┴───────────┴──────────┴─────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  后台任务编排层                        │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ │   │
│  │  │ Task Service │ │   Pipeline   │ │ ASR Semaphore│ │   │
│  │  │ 恢复/复用/取消│ │ 转写/摘要流程│ │ 并发控制      │ │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    服务层                             │   │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐  │   │
│  │  │ ASR  │ │Audio │ │Summary│ │File  │ │Directory │  │   │
│  │  │Service│ │Service│ │Service│ │Service│ │ Watcher │  │   │
│  │  │FunASR│ │ffmpeg│ │OpenAI│ │Hash/ │ │定时扫描  │  │   │
│  │  │ CPU  │ │pydub │ │ SDK  │ │Suffix│ │文件发现  │  │   │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌───────────────┐  ┌───────────────────────────────────┐   │
│  │ SQLite /      │  │     本地文件系统                   │   │
│  │ SQLModel      │  │  data/recordings/  (原始音频)      │   │
│  │ 录音/任务/    │  │  data/normalized/  (归一化音频)   │   │
│  │ 转写/摘要     │  │  data/transcripts/  (转写JSON)     │   │
│  └───────────────┘  │  data/summaries/    (摘要MD)       │   │
│                     │  models/funasr/     (ASR模型缓存)  │   │
│                     │  logs/              (运行日志)     │   │
│                     └───────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 分层架构说明

| 层级 | 职责 | 关键技术 |
| --- | --- | --- |
| **表现层** | 用户界面与交互 | React 19 + TypeScript + Vite + CSS Variables |
| **API 层** | REST 接口，路由分发 | FastAPI + Uvicorn + CORS |
| **编排层** | 任务生命周期管理、流水线 | BackgroundTasks + Task Service + Semaphore |
| **服务层** | 业务逻辑封装 | ASR 引擎、音频处理、LLM 调用、文件操作 |
| **数据层** | 持久化存储 | SQLite + SQLModel ORM + 本地文件系统 |

---

## 4. 功能模块设计

### 4.1 功能全景图

```
AI Recorder
├── 1. 录音库管理
│   ├── 1.1 录音列表（搜索/筛选/状态）
│   ├── 1.2 录音详情（元数据/转写/摘要/任务）
│   ├── 1.3 文件上传（拖拽/文件选择/格式校验）
│   ├── 1.4 录音删除（单条/批量 + 级联清理）
│   ├── 1.5 标签管理（增删/筛选/持久化）
│   └── 1.6 内容搜索（文件名/转写/摘要全文检索）
├── 2. 音频处理
│   ├── 2.1 格式支持（wav/mp3/m4a/flac/aac/ogg）
│   ├── 2.2 音频归一化（ffmpeg → 16kHz mono wav）
│   ├── 2.3 音频播放（HTML5 Audio + HTTP Range）
│   └── 2.4 时间轴联动（点击转写→跳转播放位置）
├── 3. 语音转写
│   ├── 3.1 离线转写引擎（FunASR Paraformer-zh CPU）
│   ├── 3.2 VAD + 标点恢复 + 时间戳
│   ├── 3.3 并发控制（ASR_MAX_CONCURRENCY）
│   ├── 3.4 转写结果展示（分段/时间轴/高亮）
│   └── 3.5 转写文本编辑（逐段编辑/保存/持久化）
├── 4. 智能摘要
│   ├── 4.1 多供应商支持（DeepSeek/通义千问/小米MiMo）
│   ├── 4.2 6 种摘要模板
│   ├── 4.3 多份摘要留存（同录音多次摘要可对比）
│   ├── 4.4 LLM 测试连通性
│   └── 4.5 超时与重试机制
├── 5. 任务系统
│   ├── 5.1 任务状态机（queued→running→completed/error/cancelled）
│   ├── 5.2 启动恢复（遗留任务标记中断）
│   ├── 5.3 重复提交防护（同录音同类型活动任务复用）
│   ├── 5.4 取消支持（阶段间检查取消标识）
│   ├── 5.5 批量操作（批量转写/批量摘要）
│   └── 5.6 进度反馈（轮询 1.5s）
├── 6. 目录监控
│   ├── 6.1 定时扫描（可配置间隔 2-3600s）
│   ├── 6.2 文件稳定性检测（两次快照一致才入库）
│   ├── 6.3 去重（基于 content_hash SHA-256）
│   ├── 6.4 递归扫描（可配置）
│   └── 6.5 事件日志（imported/skipped/duplicate/error）
├── 7. 导出系统
│   ├── 7.1 转写导出（MD/TXT/JSON/SRT/DOCX）
│   ├── 7.2 摘要导出（MD/TXT/DOCX）
│   └── 7.3 文件名安全规则（非法字符替换）
├── 8. 设置中心
│   ├── 8.1 LLM 配置（供应商/模型/API Key/温度/TOP-P）
│   ├── 8.2 存储路径（转写保存目录/摘要保存目录）
│   ├── 8.3 目录监控配置（启用/路径/递归/间隔）
│   └── 8.4 环境变量持久化（.env 文件读写）
└── 9. 系统健康
    ├── 9.1 依赖检测（Python/ffmpeg/FunASR/LLM）
    ├── 9.2 最近错误展示
    └── 9.3 结构化日志（logs/ 目录轮转）
```

### 4.2 核心功能详细设计

#### 4.2.1 录音库管理

**录音列表页（LibraryPage）**
- 默认按创建时间倒序排列所有录音
- 每条录音展示：文件名、时长、格式、来源类型（上传/监控）、状态标签、标签、操作按钮
- 状态筛选：支持 `uploaded`、`normalizing`、`transcribing`、`transcribed`、`completed`、`error`、`cancelled` 筛选
- 搜索：支持文件名关键词搜索（当前版本）；设计目标支持全文搜索（文件名 + 转写文本 + 摘要内容）
- 标签筛选：按标签精准匹配

**录音详情面板（RecordingDetailPanel）**
- 元数据区：文件名、路径、时长、大小、格式、来源、创建时间、状态
- 标签编辑区：可添加/删除标签，最多 20 个，每标签最长 40 字符
- 转写区：分段展示，每段含时间戳和文本，支持点击跳转音频位置
- 摘要区：展示所有历史摘要，含模板名称和时间
- 任务区：展示关联任务及进度/状态
- 操作按钮：转写、摘要（需先转写）、导出、删除

**文件上传**
- 支持格式：`wav`、`mp3`、`m4a`、`flac`、`aac`、`ogg`
- 大小限制：单文件 500MB
- 上传流程：接收文件 → 检查大小和格式 → 计算 SHA-256 哈希 → 查重（同哈希返回已有录音）→ 移至录音目录 → 写入数据库
- 上传目标：必须配置目录监控的录音目录后才能上传

#### 4.2.2 音频播放与时间轴联动（Phase 1 已完成）

**音频接口**
- `GET /api/recordings/{id}/audio`：按录音 ID 安全读取音频
- 支持 HTTP Range（`bytes=start-end`），浏览器可拖动进度条
- 路径安全：只读取数据库登记的 `original_path`，拒绝路径穿越

**播放器交互**
- 详情面板嵌入 HTML5 `<audio>` 播放器
- 转写片段点击 `[00:12 - 00:18]` 跳转播放器时间
- 播放过程中当前片段高亮（基于 `start_time` 和 `end_time` 匹配）
- 无时间戳片段正常显示，不破坏页面

#### 4.2.3 语音转写引擎

**ASR 服务（ASRService）**
- 引擎：FunASR AutoModel（一键加载 Paraformer-zh + fsmn-vad + ct-punc）
- 推理设备：CPU（无 GPU/CUDA 需求）
- 并发控制：`threading.BoundedSemaphore`，遵守 `ASR_MAX_CONCURRENCY`（默认 1）
- 模型懒加载：首次调用时加载，后续复用 `_model` 实例

**转写后处理**
- FunASR 返回完整文本 + 逐字时间戳
- 按中文标点（`。！？!?；;`）切句
- 每句映射时间戳（起始字符时间 → 结束字符时间）
- 跳过纯标点和空白片段
- 结果以 `TranscriptSegment`（start_time/end_time/speaker/text/sequence）存储

**性能预期**
| 音频长度 | 保守预期 | 优化预期 |
| --- | --- | --- |
| 5 分钟 | 1-3 分钟 | < 1 分钟 |
| 30 分钟 | 5-12 分钟 | 3-6 分钟 |
| 1 小时 | 10-25 分钟 | 6-15 分钟 |

#### 4.2.4 智能摘要系统

**供应商预设**

| Provider | 默认接口 | 默认模型 | 温度 | TOP-P |
| --- | --- | --- | --- | --- |
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` | 0.2 | - |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/...` | `qwen-plus` | 0.2 | - |
| `mimo` | `https://token-plan-cn.xiaomimimo.com/v1` | `mimo-v2.5` | 1.0 | 0.95 |

**6 种摘要模板**

| 模板 ID | 名称 | 适用场景 |
| --- | --- | --- |
| `structured_summary` | 结构化摘要 | 通用：背景、议题、结论、建议 |
| `meeting_minutes` | 会议纪要 | 正式会议：议题、角色、结论、风险 |
| `action_items` | 待办事项 | 任务提取：负责人、事项、截止时间 |
| `decisions_risks` | 决策与风险 | 管理场景：已定决策、未决问题、风险 |
| `executive_brief` | 管理层简报 | 高管汇报：一句话概览、关键结论 |
| `polished_transcript` | 转写内容规整 | 出版/分发：修正口语、保持原意 |

**多人对话安全指导**
- 所有模板内置 `MULTI_SPEAKER_GUIDANCE`：不默认归属发言者、不编造姓名职务、缺失信息写"未提及"

**可靠性机制**
- 网络重试：指数退避（1s/2s/4s/8s），最多 `LLM_RETRY_ATTEMPTS`（默认 3）次
- 超时控制：`LLM_TIMEOUT_SECONDS`（默认 60s）
- 输出截断：检测 `finish_reason == "length"` 时抛出明确错误
- 转写规整长文本：自动分块处理（2200 字符/块），再截断时二分递归处理

**连通性测试**
- `POST /api/settings/llm/test`：发送最小对话，返回延迟和响应（最多 16 tokens）

#### 4.2.5 任务系统设计

**任务状态机**
```
queued → running → completed
                  → error
                  → cancelled
```

**任务生命周期**
1. **创建/复用**：同一录音 + 同一任务类型已有活动任务（queued/running）时，返回已有任务而非新建
2. **启动执行**：通过 FastAPI `BackgroundTasks` 提交
3. **阶段边界检查**：在每个 I/O 密集操作后检查 `task.status == "cancelled"`
4. **完成/失败**：写入最终状态、结果路径、完成时间
5. **启动恢复**：后端启动时扫描遗留 `queued`/`running` 任务，统一标记为 `error` + "Task was interrupted by application restart"
6. **取消**：支持 `POST /api/tasks/{id}/cancel`，取消后恢复录音状态

**任务类型**
| task_type | 描述 | 关联 API |
| --- | --- | --- |
| `transcription` | 语音转写任务 | `POST /api/transcribe/{id}` |
| `summary:{mode}` | 摘要任务（按模板区分） | `POST /api/summary/{id}?mode=xxx` |

**批量任务**
- `POST /api/transcribe/batch`：批量提交转写，每个录音独立任务
- `POST /api/summary/batch`：批量提交摘要，遵守并发限制

#### 4.2.6 目录监控

**监控流程**
1. 定时扫描指定目录（默认间隔 10s，2-3600s 可配）
2. 文件稳定性检测：连续两次扫描文件大小/mtime 不变才入库
3. 格式过滤：仅支持音频格式，不支持格式记录 `skipped` 事件
4. 内容去重：计算 SHA-256，与已有录音和事件比对
5. 去重策略：同哈希跳过（`duplicate_skipped`）
6. 监控目录去重：watched 来源的文件不重复拷贝到 `data/recordings/`

**事件记录**
- 每次扫描产生 `WatchEvent` 记录
- 事件类型：`imported`（新入库）、`skipped`（格式不支持）、`duplicate_skipped`（重复）、`error`（目录不存在）

#### 4.2.7 导出系统

**转写导出格式**

| 格式 | 文件名模式 | 说明 |
| --- | --- | --- |
| Markdown | `{name}-转写-{date}.md` | 含标题、时长元数据，分段展示 |
| TXT | `{name}-转写-{date}.txt` | 纯文本，`[时间] 内容` 格式 |
| JSON | `{name}-转写-{date}.json` | 结构化，含全部时间戳和 speaker 字段 |
| SRT | `{name}-转写-{date}.srt` | 标准字幕格式，兼容视频编辑软件 |
| DOCX | `{name}-转写-{date}.docx` | Word 文档，含时间和内容 |

**摘要导出格式**

| 格式 | 说明 |
| --- | --- |
| Markdown | 含标题、模板信息、完整摘要 |
| TXT | 纯文本，Markdown 标记剥离 |
| DOCX | Word 文档 |

**文件名安全规则**
- 替换不安全字符：`<>:"/\|?*` → 连字符
- 限制总长度 ≤ 200 字符
- URL 编码下载头

---

## 5. 数据模型设计

### 5.1 ER 图

```
┌──────────────┐       ┌──────────────────┐
│   Recording  │1─────n│ TranscriptSegment│
│              │       │                  │
│ id (PK)      │       │ id (PK)          │
│ filename     │       │ recording_id (FK)│
│ original_path│       │ start_time       │
│ normalized_  │       │ end_time         │
│   path       │       │ speaker          │
│ duration_    │       │ text             │
│   seconds    │       │ sequence         │
│ file_size    │       └──────────────────┘
│ format       │
│ content_hash │       ┌──────────────────┐
│ source_type  │1─────n│     Summary      │
│ source_path  │       │                  │
│ tags         │       │ id (PK)          │
│ status       │       │ recording_id (FK)│
│ error_message│       │ mode             │
│ created_at   │       │ content          │
│ updated_at   │       │ created_at       │
└──────────────┘       └──────────────────┘
       │
       │ 1─────n
       ▼
┌──────────────┐       ┌──────────────────┐
│     Task     │       │   WatchEvent     │
│              │       │                  │
│ id (PK)      │       │ id (PK)          │
│ recording_id │       │ file_path        │
│ task_type    │       │ filename         │
│ status       │       │ status           │
│ progress     │       │ reason           │
│ error_message│       │ recording_id     │
│ result_path  │       │ content_hash     │
│ created_at   │       │ file_size        │
│ updated_at   │       │ file_mtime       │
│ started_at   │       │ created_at       │
│ completed_at │       └──────────────────┘
└──────────────┘
```

### 5.2 录音状态流转

```
uploaded ─→ normalizing ─→ transcribing ─→ transcribed ─→ completed
   │              │               │                            │
   └──────────────┴───────────────┴──────── error ─────────────┘
                                                   │
                                              cancelled
```

### 5.3 数据库选型

- **数据库**: SQLite（文件：`data/app.db`）
- **ORM**: SQLModel（Pydantic + SQLAlchemy）
- **迁移策略**: 手写增量 SQL（`ALTER TABLE ADD COLUMN`），启动时自动执行
- **升级路径**: 未来计划引入 Alembic 做规范化迁移

### 5.4 数据存储策略

| 数据类型 | 存储位置 | 格式 |
| --- | --- | --- |
| 元数据 | SQLite `app.db` | 结构化表 |
| 原始音频 | 用户指定的录音目录 | 原始格式 |
| 归一化音频 | `data/normalized/` | 16kHz mono wav |
| 转写结果 | `data/transcripts/{id}.json` | JSON |
| 摘要结果 | `data/summaries/{name}-{template}-{date}.md` | Markdown |
| ASR 模型 | `models/funasr/` | PyTorch 模型文件 |
| 运行日志 | `logs/` | 文本日志 |

---

## 6. API 接口设计

### 6.1 API 全景

| 模块 | 方法 | 路径 | 描述 | 状态 |
| --- | --- | --- | --- | --- |
| **健康检查** | GET | `/health` | 系统状态、依赖检测、最近错误 | ✅ |
| **录音** | GET | `/api/recordings` | 录音列表（支持搜索和标签过滤） | ✅ |
| | POST | `/api/recordings` | 上传录音 | ✅ |
| | GET | `/api/recordings/{id}` | 录音详情（含转写、摘要、任务） | ✅ |
| | DELETE | `/api/recordings/{id}` | 删除录音（级联清理数据） | ✅ |
| | POST | `/api/recordings/batch-delete` | 批量删除 | ✅ |
| | PATCH | `/api/recordings/{id}/tags` | 更新标签 | ✅ |
| | PATCH | `/api/recordings/{id}/segments/{sid}` | 编辑转写片段 | ✅ |
| | GET | `/api/recordings/{id}/audio` | 音频流（HTTP Range） | ✅ |
| | GET | `/api/recordings/{id}/exports/transcript` | 导出转写 | ✅ |
| **转写** | POST | `/api/transcribe/{id}` | 开始转写 | ✅ |
| | POST | `/api/transcribe/batch` | 批量转写 | ✅ |
| **摘要** | GET | `/api/summary/templates` | 摘要模板列表 | ✅ |
| | POST | `/api/summary/{id}` | 生成摘要 | ✅ |
| | POST | `/api/summary/batch` | 批量摘要 | ✅ |
| | GET | `/api/summaries/{id}/export` | 导出摘要 | ✅ |
| | DELETE | `/api/summaries/{id}` | 删除摘要 | ✅ |
| **任务** | GET | `/api/tasks/{id}` | 任务状态 | ✅ |
| | POST | `/api/tasks/{id}/cancel` | 取消任务 | ✅ |
| **设置** | GET/PUT | `/api/settings/llm` | LLM 配置读写 | ✅ |
| | POST | `/api/settings/llm/test` | LLM 连通性测试 | ✅ |
| | GET/PUT | `/api/settings/watch` | 目录监控配置 | ✅ |
| | GET/PUT | `/api/settings/storage` | 存储路径配置 | ✅ |
| **监控** | GET | `/api/watch/events` | 监控事件列表 | ✅ |
| | POST | `/api/watch/scan` | 手动触发扫描 | ✅ |

### 6.2 API 设计原则

1. **RESTful 风格**：资源化 URL，标准 HTTP 方法
2. **统一错误响应**：HTTP 状态码 + JSON 错误详情
3. **安全第一**：音频接口路径校验，API Key 不暴露
4. **Range 支持**：音频端点支持 HTTP Range，适配播放器
5. **查询参数扩展**：搜索、标签、导出格式通过 Query 参数
6. **批量操作**：批量接口使用数组传参，独立任务执行

### 6.3 关键接口行为说明

**上传查重**
- 文件内容 SHA-256 哈希已存在 → 返回已有录音，不重复存储

**转写复用**
- 同一录音已有运行中的转写任务 → 直接返回该任务，不创建新的

**摘要依赖**
- 摘要有转写前置依赖 → 无转写时返回 400 错误

**设置持久化**
- PUT 操作直接写入 `backend/.env` 文件 → 清除配置缓存 → 返回最新值

---

## 7. 前端交互设计

### 7.1 页面结构

```
┌──────────────────────────────────────────────────────┐
│  NavBar (左侧导航栏)                                  │
│  ┌──────────────┐                                    │
│  │ 🎙 录音库    │   ← 默认页 / 主工作区               │
│  │ ⚙ 设置       │   ← LLM / 存储 / 监控配置          │
│  │ 📁 目录监控   │   ← 扫描配置与事件日志              │
│  │ 💚 健康检查   │   ← 系统状态与依赖检测              │
│  └──────────────┘                                    │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │              主内容区 (LibraryPage)              │  │
│  │  ┌────────────┐  ┌───────────────────────────┐ │  │
│  │  │  录音列表   │  │     录音详情面板           │ │  │
│  │  │            │  │  ┌───────────────────────┐ │ │  │
│  │  │ ┌────────┐ │  │  │ 元数据 / 标签         │ │ │  │
│  │  │ │ 录音1  │ │  │  ├───────────────────────┤ │ │  │
│  │  │ ├────────┤ │  │  │ 音频播放器            │ │ │  │
│  │  │ │ 录音2  │ │  │  ├───────────────────────┤ │ │  │
│  │  │ ├────────┤ │  │  │ 转写内容(时间轴高亮)   │ │ │  │
│  │  │ │ 录音3  │ │  │  ├───────────────────────┤ │ │  │
│  │  │ └────────┘ │  │  │ 摘要结果             │ │ │  │
│  │  │            │  │  ├───────────────────────┤ │ │  │
│  │  │ 搜索/筛选  │  │  │ 任务进度             │ │ │  │
│  │  └────────────┘  │  └───────────────────────┘ │ │  │
│  │                  └───────────────────────────┘ │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### 7.2 组件树

```
main.tsx
└── App.tsx
    ├── NavBar.tsx                   (左侧导航)
    │   └── HealthPage.tsx           (健康状态内嵌)
    ├── LibraryPage.tsx              (录音库主页)
    │   ├── 搜索框 + 标签筛选
    │   ├── 录音列表（可多选）
    │   │   ├── StatusBadge.tsx      (状态标签)
    │   │   ├── 删除按钮
    │   │   └── MetricCard.tsx       (时间/大小指标)
    │   └── RecordingDetailPanel.tsx (右侧详情)
    │       ├── InfoCard.tsx         (元数据卡片)
    │       ├── <audio> 播放器
    │       ├── 转写片段列表（可点击/高亮/编辑）
    │       ├── SummaryCard.tsx      (摘要卡片)
    │       │   └── MarkdownView.tsx (Markdown 渲染)
    │       ├── 任务进度卡片
    │       └── ExportButtons.tsx    (导出按钮)
    ├── SettingsPage.tsx             (设置页)
    │   └── SettingsSection.tsx      (配置区块)
    ├── WatchPage.tsx                (目录监控页)
    └── ToastStack.tsx               (全局提示)
    └── ConfirmDialog.tsx            (确认对话框)
```

### 7.3 状态管理

- 当前：`App.tsx` 中使用多个 `useState` 管理状态，通过 props 下传
- 规划：向自定义 Hook 演进（`useRecordings`、`useActiveTask`、`useSettings`）
- 未来：React Context 或轻量状态库

### 7.4 样式系统

- 纯 CSS（`styles.css`），使用 CSS Variables 管理主题色
- CSS 变量定义：`--bg`、`--surface`、`--text`、`--primary` 等
- 设计为暗色模式兼容（CSS 变量切换即可实现）

### 7.5 交互反馈

| 场景 | 反馈方式 |
| --- | --- |
| 列表加载 | 默认无加载态 → 规划骨架屏 |
| 任务执行 | 1.5s 轮询刷新任务状态 / 进度条 |
| 操作成功 | ToastStack 顶部提示 |
| 危险操作 | ConfirmDialog 确认框 |
| 错误处理 | Error Boundary（规划中）/ Toast 错误提示 |

---

## 8. 技术架构决策

### 8.1 关键架构决策记录（ADR）

**ADR-001：3.0 任务保持进程内执行**
- **决策**：暂不引入 Celery/Redis，使用进程内 BackgroundTasks + 恢复守卫
- **理由**：产品定位为 Windows 单机应用，降低部署复杂度；通过启动恢复、任务复用、并发控制弥补可靠性
- **后果**：取消是协作式（阶段间检查），无法强杀运行中的 FunASR 调用

**ADR-002：音频通过 Range-Aware API 提供**
- **决策**：`GET /api/recordings/{id}/audio` 路径，支持 HTTP Range
- **理由**：不暴露本地文件系统路径，浏览器播放器原生支持 Range，不重复拷贝 watched 音频
- **后果**：端点信任数据库登记作为访问边界，未来多用户需加授权检查

### 8.2 技术选型理由

| 技术选型 | 理由 |
| --- | --- |
| FastAPI + Uvicorn | Windows 本地部署简单，async 支持，OpenAPI 自动文档 |
| 进程内任务队列 | 避免引入 Redis/Celery，降低 Windows 部署成本 |
| FunASR CPU | 当前机器无 CUDA GPU，CPU 路线最稳定 |
| SQLite + SQLModel | 单机应用最轻量方案，零配置，迁移成本低 |
| 本地文件系统 | 音频文件不进数据库，直接文件操作更高效 |
| React + Vite | 快速 HMR，TypeScript 支持，生产构建体积小 |
| ffmpeg + pydub | Windows 下音频格式转换最成熟方案 |
| OpenAI SDK | 统一 LLM 调用接口，DeepSeek/通义千问/MiMo 均兼容 |

### 8.3 明确不做的事

| 不做的事 | 原因 | 计划 |
| --- | --- | --- |
| Celery/Redis 后台任务 | 部署复杂度高 | 3.0 后评估 |
| Tailwind/shadcn 迁移 | 改动面大，不提升核心能力 | 3.2 考虑 |
| 默认说话人分离 | CPU 成本高，体验过慢 | 3.1 实验开关 |
| 多用户账号系统 | 个人本地工具定位 | 不做 |
| 全链路离线 LLM | 本地推理成本极高 | 不做 |
| WebSocket/SSE 推送 | 轮询当前可接受 | 3.1 引入 |

---

## 9. 部署与运维设计

### 9.1 运行环境要求

| 依赖 | 版本要求 | 用途 |
| --- | --- | --- |
| Windows | 10/11 64-bit | 操作系统 |
| Python | 3.10 ~ 3.12（推荐 3.12） | 后端运行时 |
| Node.js | 20+ LTS | 前端构建与开发 |
| ffmpeg | 任意版本 | 音频格式转换 |
| Git | 任意版本 | 克隆仓库 |

### 9.2 一键部署与启动

```powershell
# 首次安装
git clone <repo-url>
cd AI-workspace
.\setup.ps1        # 安装依赖 + 构建前端

# 日常启动
.\start-all.ps1    # 启动 FastAPI:8000，并托管前端构建产物
.\stop-all.ps1     # 停止所有服务
.\check.ps1        # 检查环境与运行状态
```

### 9.3 便携式打包

```powershell
.\package-portable.ps1              # 生成 release/AI Recorder Portable/
.\package-portable.ps1 -Zip         # 同时压缩
.\package-portable.ps1 -ExcludeData # 不含历史数据
.\package-portable.ps1 -ExcludeSecrets  # 不含 API Key
```

便携包包含：
- 所有运行时依赖（Python venv、Node.js 构建产物）
- ASR 模型缓存（可选排除以减小体积）
- 启动/停止/检查脚本
- 不包含 `.env` 中的 API Key（`-ExcludeSecrets`）

### 9.4 配置管理

| 配置方式 | 作用域 | 示例 |
| --- | --- | --- |
| `backend/.env` | 后端环境变量 | `LLM_PROVIDER=deepseek` |
| `backend/.env.example` | 配置模板 | 新用户复制参考 |
| `frontend/.env.local` | 前端构建变量 | `VITE_API_BASE_URL` |
| 网页设置面板 | 用户可见配置 | LLM 供应商、存储路径 |

### 9.5 监控与排障

- `GET /health`：Python 版本、ffmpeg 可用性、FunASR 安装、LLM 配置、最近错误
- `logs/` 目录：结构化日志，支持轮转
- `.\check.ps1`：检查依赖、端口、配置、服务状态
- `docs/troubleshooting.md`：故障排查手册

---

## 10. 产品路线图

### 10.1 版本规划

| 版本 | 目标 | 状态 |
| --- | --- | --- |
| **0.x / MVP** | 跑通核心链路：上传、转写、摘要、导出、监控 | ✅ 已交付 |
| **3.0 Phase 0** | 可靠性地基：任务恢复/取消/复用、并发限制、超时重试 | ✅ 已交付 |
| **3.0 Phase 1** | 播放和校对：音频播放、时间轴联动 | ✅ 已交付 |
| **3.0 Phase 1** | 播放和校对：转写编辑保存（进行中） | 🔄 进行中 |
| **3.0 Phase 2** | 录音库管理：全文搜索、标签筛选、批量操作 | 📋 计划中 |
| **3.0 Phase 3** | 输出和维护：导出扩展、日志、测试 | 📋 计划中 |
| **3.1** | 实时体验：WebSocket/SSE、说话人分离实验 | 📋 候选 |
| **3.2** | UI 升级：Tailwind/shadcn、设计系统、Alembic | 📋 候选 |

### 10.2 3.0 实施顺序

```
Phase 0 (可靠性) ──→ Phase 1 (校对) ──→ Phase 2 (管理) ──→ Phase 3 (输出)
     ├─ 任务恢复          ├─ 音频播放          ├─ 全文搜索          ├─ 导出扩展
     ├─ 并发限制          ├─ 时间轴联动        ├─ 标签管理          ├─ 结构化日志
     └─ 超时/重试/取消    └─ 转写编辑          └─ 批量操作          └─ 测试覆盖
```

### 10.3 暂缓方向

| 方向 | 暂缓原因 | 预计版本 |
| --- | --- | --- |
| WebSocket/SSE 实时推送 | 轮询目前可用，任务持久化更优先 | 3.1 |
| Tailwind/shadcn | UI 栈迁移扩大改动面 | 3.2 |
| 完整路由系统 | 有体验价值，低于播放和校对 | 3.1 |
| Alembic 数据库迁移 | 当前手写 SQL 迁移可控 | 3.1/3.2 |
| 默认说话人分离 | CPU 成本高，先做兼容 | 3.1 |
| i18n 国际化 | 当前中文优先 | 后续国际版 |

---

## 11. 安全与隐私设计

### 11.1 数据本地化

| 数据 | 存储位置 | 网络传输 |
| --- | --- | --- |
| 原始音频 | 本地文件系统 | 不上传云端 |
| 转写结果 | 本地 SQLite + JSON | 不上传云端（转写离线完成） |
| 转写文本（供摘要） | 本地内存 → LLM API | **仅摘要时发送到云端 LLM** |
| API Key | `backend/.env` | 仅用于 LLM API 认证 |
| 运行日志 | 本地 `logs/` | 不上传 |

### 11.2 安全措施

- **API Key 保护**：仅存 `backend/.env`，不出现在前端产物和日志中
- **音频路径隔离**：音频端点仅读取数据库登记路径，拒绝路径穿越
- **上传大小限制**：单文件 500MB，防止内存溢出
- **CORS 限制**：仅保留本机开发来源；公网 Android 访问使用 `X-API-Token`
- **环境变量脱敏**：API Key 读取时返回掩码版本（`sk-a1...b2c3`）
- **便携包安全**：`-ExcludeSecrets` 选项防止 API Key 泄露

### 11.3 隐私边界

- 转写：100% 本地离线完成，不需要任何网络连接
- 摘要：需要用户自行配置 API Key，转写文本发送到所选供应商
- 用户控制：摘要功能和模板由用户主动触发，不做自动摘要
- 数据所有权：所有数据存储在用户本地磁盘，删除录音时级联清理

---

## 12. 非功能需求

### 12.1 性能指标

| 指标 | 目标 |
| --- | --- |
| 5 分钟音频转写 | ≤ 3 分钟 |
| 30 分钟音频转写 | ≤ 12 分钟 |
| 1 小时音频转写 | ≤ 25 分钟 |
| 摘要生成 | ≤ 60 秒（取决于 LLM API） |
| 前端首屏加载 | ≤ 2 秒 |
| 录音列表（50+ 条）渲染 | ≤ 1 秒 |
| 音频拖动/播放延迟 | ≤ 500ms |

### 12.2 可靠性

| 指标 | 目标 |
| --- | --- |
| 后端重启后任务恢复 | 遗留任务自动标记中断，可重试 |
| 重复提交防护 | 同一录音同类型活动任务复用 |
| ffmpeg 超时处理 | 600 秒超时，明确报错 |
| LLM 重试 | 最多 3 次，指数退避 |
| ASR 并发控制 | 遵守 `ASR_MAX_CONCURRENCY`（默认 1） |
| 文件系统错误 | 数据库事务优先，文件删除失败不影响 |

### 12.3 可维护性

| 措施 | 状态 |
| --- | --- |
| 结构化日志 | ✅ `logs/` 目录，关键链路记录 |
| 健康检查端点 | ✅ `/health` 含依赖状态和最近错误 |
| 环境检查脚本 | ✅ `check.ps1` |
| 后端单元测试 | ✅ Task Service + Audio API |
| 前端构建验证 | ✅ `npm run build` |
| ADR 决策记录 | ✅ 2 条 |
| CHANGELOG | ✅ 按阶段更新 |
| 故障排查文档 | ✅ `docs/troubleshooting.md` |

### 12.4 可扩展性

| 扩展点 | 当前设计 | 未来方向 |
| --- | --- | --- |
| 新 LLM 供应商 | 预设模式 + 自定义 base_url/model | 增加供应商预设 |
| 新摘要模板 | `SUMMARY_TEMPLATES` 字典 | 用户自定义模板 |
| 新导出格式 | 格式枚举 + 对应处理分支 | 插件化格式处理器 |
| 多语种支持 | SenseVoice-Small 模型预留 | 语言选择 + i18n |
| 说话人分离 | 配置开关 + 数据模型兼容 | SOND 引擎集成 |
| 实时转写 | - | WebSocket + 流式 ASR |
| 多用户 | - | 账号系统 + 权限控制 |

---

## 13. 附录

### 13.1 完整 API 接口清单

```
GET    /health                                   系统健康检查
GET    /api/recordings                           录音列表
POST   /api/recordings                           上传录音
GET    /api/recordings/{id}                      录音详情
DELETE /api/recordings/{id}                      删除录音
POST   /api/recordings/batch-delete              批量删除
PATCH  /api/recordings/{id}/tags                 更新标签
PATCH  /api/recordings/{id}/segments/{sid}       编辑转写片段
GET    /api/recordings/{id}/audio                音频流
GET    /api/recordings/{id}/exports/transcript   导出转写
POST   /api/transcribe/{id}                      开始转写
POST   /api/transcribe/batch                     批量转写
GET    /api/summary/templates                    摘要模板列表
POST   /api/summary/{id}                         生成摘要
POST   /api/summary/batch                        批量摘要
GET    /api/summaries/{id}/export                导出摘要
DELETE /api/summaries/{id}                       删除摘要
GET    /api/tasks/{id}                           任务状态
POST   /api/tasks/{id}/cancel                    取消任务
GET    /api/settings/llm                         LLM 配置读取
PUT    /api/settings/llm                         LLM 配置写入
POST   /api/settings/llm/test                    LLM 连通性测试
GET    /api/settings/watch                       目录监控配置读取
PUT    /api/settings/watch                       目录监控配置写入
GET    /api/settings/storage                     存储路径配置读取
PUT    /api/settings/storage                     存储路径配置写入
GET    /api/watch/events                         监控事件列表
POST   /api/watch/scan                           手动扫描
```

### 13.2 环境变量完整清单

```env
APP_ENV=local                   # 运行环境
APP_HOST=127.0.0.1              # 监听地址
APP_PORT=8000                   # 监听端口

DATA_DIR=../data                # 数据根目录
MODEL_DIR=../models/funasr      # 模型缓存目录
LOG_DIR=../logs                 # 日志目录
TRANSCRIPT_DIR=../data/transcripts  # 转写导出目录
SUMMARY_DIR=../data/summaries       # 摘要导出目录
FFMPEG_BIN=ffmpeg               # ffmpeg 可执行文件路径
FFMPEG_TIMEOUT_SECONDS=600      # ffmpeg 超时秒数

ASR_DEVICE=cpu                  # ASR 推理设备
ASR_MODEL=paraformer-zh         # ASR 主模型
ASR_VAD_MODEL=fsmn-vad          # VAD 模型
ASR_PUNC_MODEL=ct-punc          # 标点模型
ASR_TIMESTAMP_MODEL=fa-zh       # 时间戳模型
ASR_ENABLE_DIARIZATION=false    # 说话人分离开关
ASR_MAX_CONCURRENCY=1           # 最大并发转写数

LLM_PROVIDER=deepseek           # LLM 供应商
LLM_API_KEY=                    # LLM API Key
LLM_BASE_URL=                   # LLM 自定义 Base URL
LLM_MODEL=                      # LLM 自定义模型
LLM_MAX_COMPLETION_TOKENS=2048  # 最大输出 token
LLM_TIMEOUT_SECONDS=60          # LLM 超时秒数
LLM_RETRY_ATTEMPTS=3            # LLM 重试次数
LLM_TEMPERATURE=                # 温度（留空用供应商默认）
LLM_TOP_P=                      # TOP-P
MIMO_API_KEY=                   # 小米 MiMo API Key
MIMO_THINKING=disabled          # MiMo 深度思考开关

WATCH_ENABLED=false             # 目录监控开关
WATCH_DIR=                      # 监控目录
WATCH_RECURSIVE=true            # 递归扫描
WATCH_INTERVAL_SECONDS=10       # 扫描间隔

CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173  # 本机开发 CORS 白名单
```

### 13.3 支持的文件格式

| 格式 | 扩展名 | 上传 | 目录监控 | 转写 |
| --- | --- | --- | --- | --- |
| WAV | .wav | ✅ | ✅ | ✅ |
| MP3 | .mp3 | ✅ | ✅ | ✅ |
| M4A | .m4a | ✅ | ✅ | ✅ |
| FLAC | .flac | ✅ | ✅ | ✅ |
| AAC | .aac | ✅ | ✅ | ✅ |
| OGG | .ogg | ✅ | ✅ | ✅ |

### 13.4 项目文件结构

```
AI-workspace/
├── README.md                    # 用户使用手册
├── CHANGELOG.md                 # 版本变更记录
├── docs/deployment-plan.md      # 部署方案文档
├── setup.ps1                    # 首次安装脚本
├── start-all.ps1                # 一键启动
├── stop-all.ps1                 # 停止服务
├── check.ps1                    # 环境检查
├── package-portable.ps1         # 便携打包
├── backend/
│   ├── app/
│   │   ├── main.py              # FastAPI 应用入口
│   │   ├── config.py            # 配置管理（Pydantic Settings）
│   │   ├── api/                 # API 路由
│   │   │   ├── health.py        #   健康检查
│   │   │   ├── recordings.py    #   录音 CRUD + 导出 + 音频
│   │   │   ├── transcribe.py    #   转写触发
│   │   │   ├── summary.py       #   摘要 + 导出
│   │   │   ├── tasks.py         #   任务查询/取消
│   │   │   ├── settings.py      #   设置读写 + LLM 测试
│   │   │   └── watch.py         #   目录监控事件
│   │   ├── db/
│   │   │   └── database.py      #   数据库引擎 + 迁移
│   │   ├── models/              # 数据模型
│   │   │   ├── recording.py     #   录音
│   │   │   ├── transcript.py    #   转写片段
│   │   │   ├── summary.py       #   摘要
│   │   │   ├── task.py          #   任务
│   │   │   └── watch_event.py   #   监控事件
│   │   ├── pipeline/
│   │   │   └── workflow.py      #   任务流水线
│   │   └── services/            # 业务服务
│   │       ├── asr_service.py   #   ASR 引擎
│   │       ├── audio_service.py #   音频处理
│   │       ├── summary_service.py#  摘要生成
│   │       ├── task_service.py  #   任务管理
│   │       ├── watch_service.py #   目录监控
│   │       ├── file_service.py  #   文件工具
│   │       ├── export_names.py  #   导出文件名
│   │       ├── docx_export.py   #   DOCX 生成
│   │       └── runtime_log.py   #   日志配置
│   ├── requirements.txt         # Python 依赖
│   ├── .env.example             # 环境变量模板
│   └── .env                     # 实际配置（不提交）
├── frontend/
│   ├── src/
│   │   ├── main.tsx             # React 入口
│   │   ├── App.tsx              # 主应用组件
│   │   ├── lib/
│   │   │   ├── api.ts           #   API 客户端
│   │   │   ├── types.ts         #   类型定义
│   │   │   ├── format.ts        #   格式化工具
│   │   │   └── viewTypes.ts     #   视图类型
│   │   ├── pages/
│   │   │   ├── LibraryPage.tsx   #   录音库页面
│   │   │   ├── SettingsPage.tsx  #   设置页面
│   │   │   ├── WatchPage.tsx     #   目录监控页面
│   │   │   └── HealthPage.tsx    #   健康检查页面
│   │   └── components/
│   │       ├── layout/NavBar.tsx          # 导航栏
│   │       ├── ui/StatusBadge.tsx         # 状态标签
│   │       ├── ui/InfoCard.tsx            # 信息卡片
│   │       ├── ui/ExportButtons.tsx       # 导出按钮
│   │       ├── ui/SettingsSection.tsx     # 设置区块
│   │       ├── recording/MetricCard.tsx   # 指标卡片
│   │       ├── recording/SummaryCard.tsx  # 摘要卡片
│   │       ├── recording/RecordingDetailPanel.tsx  # 录音详情
│   │       ├── markdown/MarkdownView.tsx  # Markdown 渲染
│   │       ├── feedback/ToastStack.tsx    # 提示栈
│   │       └── feedback/ConfirmDialog.tsx # 确认框
│   ├── vite.config.ts
│   └── package.json
├── data/                        # 运行时数据
│   ├── app.db                   #   SQLite 数据库
│   ├── recordings/              #   原始音频（上传来源）
│   ├── normalized/              #   归一化音频
│   ├── transcripts/             #   转写 JSON
│   └── summaries/               #   摘要 MD
├── models/
│   └── funasr/                  #   ASR 模型缓存
├── logs/                        #   运行日志
├── scripts/                     #   辅助脚本
└── docs/                        #   文档中心
    ├── product/                 #     产品规划
    │   ├── roadmap.md
    │   ├── versions/3.0/
    │   └── backlog/
    ├── decisions/               #     架构决策记录
    └── *.md                     #     各类说明文档
```

---

> **文档版本**: 1.0
> **生成日期**: 2026-05-23
> **基于代码版本**: 3.0 Phase 0 + Phase 1（部分）
> **分析方法**: 源码分析 + 文档分析 + 架构决策反推
