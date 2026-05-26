# AI Recorder Code Wiki

## 1. 项目概述

AI Recorder 是一个面向 Windows 本机使用的录音整理工具。它将音频入库、本地离线转写、云端大模型摘要、导出和目录监控整合到一个网页应用中，适合整理会议录音、访谈、课堂笔记和日常语音记录。

### 1.1 核心功能

| 功能 | 描述 |
|------|------|
| 录音库管理 | 集中管理音频文件、时长、来源、创建时间和处理状态 |
| 文件上传 | 支持 wav、mp3、m4a、flac、aac、ogg 等格式 |
| 音频播放 | 网页内直接播放，支持时间戳跳转 |
| 转写校对 | 编辑转写片段，保存后用于后续摘要和导出 |
| 本地转写 | 使用 FunASR 在本机 CPU 上完成中文语音转文字 |
| 智能摘要 | 支持 DeepSeek、通义千问、小米 MiMo 等 OpenAI 兼容接口 |
| 多份摘要 | 同一条录音可保留多次摘要结果 |
| 搜索功能 | 支持内容搜索和标签筛选 |
| 导出功能 | 转写可导出为 Markdown、TXT、JSON、SRT、DOCX |
| 目录监控 | 定时扫描录音目录，新音频自动入库 |

### 1.2 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | FastAPI | - |
| 数据库 | SQLite + SQLModel | - |
| 语音转写 | FunASR | - |
| 大模型接口 | OpenAI Python SDK | - |
| 前端框架 | React + TypeScript | - |
| 前端构建 | Vite | - |
| 音频处理 | ffmpeg | - |

---

## 2. 项目架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (React)                            │
│  ┌─────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │
│  │ Library │  │   Watch    │  │  Settings  │  │   Health   │  │
│  │  Page   │  │   Page     │  │   Page     │  │   Page     │  │
│  └────┬────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  │
└───────┼─────────────┼───────────────┼───────────────┼─────────┘
        │             │               │               │
        ▼             ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        API 网关 (FastAPI)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────┐ │
│  │recordings│ │transcribe│ │ summary  │ │  tasks   │ │watch  │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬───┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                   │    │
│  │ settings │ │filesystem│ │  health  │                   │    │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘                   │    │
└───────┼────────────┼────────────┼───────────────────────────┼───┘
        │            │            │                           │
        ▼            ▼            ▼                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                        服务层 (Services)                       │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐          │
│  │   ASRService │ │AudioService  │ │SummaryService│          │
│  │   (转写)     │ │  (音频处理)  │ │   (摘要)     │          │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘          │
│  ┌──────────────┐ ┌──────────────┐ ┌───────┴──────────┐      │
│  │ TaskService  │ │WatchService  │ │    FileService   │      │
│  │  (任务管理)  │ │ (目录监控)   │ │    (文件操作)    │      │
│  └──────┬───────┘ └──────┬───────┘ └──────────────────┘      │
└───────┬──────────────────┼────────────────────────────────────┘
        │                  │
        ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     数据层 (SQLite)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────┐      │
│  │Recording │ │   Task   │ │TranscriptSeg │ │ Summary  │      │
│  └──────────┘ └──────────┘ └──────────────┘ └──────────┘      │
│  ┌──────────┐                                                  │
│  │WatchEvent│                                                  │
│  └──────────┘                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责

| 模块 | 路径 | 职责描述 |
|------|------|----------|
| **API 层** | `backend/app/api/` | RESTful API 端点定义，处理 HTTP 请求/响应 |
| **数据模型** | `backend/app/models/` | 数据库表定义和数据结构 |
| **数据库** | `backend/app/db/` | 数据库连接和初始化 |
| **服务层** | `backend/app/services/` | 核心业务逻辑实现 |
| **工作流** | `backend/app/pipeline/` | 转写和摘要任务的编排执行 |
| **配置** | `backend/app/config.py` | 应用配置管理 |

---

## 3. 目录结构

```
AI-workspace/
├── backend/                    # FastAPI 后端
│   ├── app/
│   │   ├── api/               # API 路由定义
│   │   │   ├── auth.py        # 认证中间件
│   │   │   ├── filesystem.py  # 文件系统 API
│   │   │   ├── health.py      # 健康检查
│   │   │   ├── recordings.py  # 录音管理 API
│   │   │   ├── settings.py    # 设置 API
│   │   │   ├── summary.py     # 摘要 API
│   │   │   ├── tasks.py       # 任务管理 API
│   │   │   ├── transcribe.py  # 转写 API
│   │   │   └── watch.py       # 目录监控 API
│   │   ├── db/                # 数据库模块
│   │   │   └── database.py    # 数据库连接和初始化
│   │   ├── models/            # 数据模型
│   │   │   ├── recording.py   # 录音模型
│   │   │   ├── summary.py     # 摘要模型
│   │   │   ├── task.py        # 任务模型
│   │   │   ├── transcript.py  # 转写片段模型
│   │   │   └── watch_event.py # 监控事件模型
│   │   ├── pipeline/          # 工作流管道
│   │   │   └── workflow.py    # 任务执行工作流
│   │   ├── services/          # 服务层
│   │   │   ├── asr_service.py     # ASR 转写服务
│   │   │   ├── audio_service.py   # 音频处理服务
│   │   │   ├── docx_export.py     # DOCX 导出服务
│   │   │   ├── export_names.py    # 导出文件名生成
│   │   │   ├── file_service.py    # 文件操作服务
│   │   │   ├── runtime_log.py     # 运行时日志
│   │   │   ├── summary_service.py # 摘要服务
│   │   │   ├── task_service.py    # 任务管理服务
│   │   │   └── watch_service.py   # 目录监控服务
│   │   ├── config.py          # 配置管理
│   │   └── main.py            # 应用入口
│   ├── tests/                 # 测试文件
│   ├── .env.example           # 环境变量示例
│   ├── requirements.txt       # Python 依赖
│   └── start.ps1              # 启动脚本
├── frontend/                  # React 前端
│   ├── src/
│   │   ├── components/        # 组件
│   │   ├── lib/               # 工具函数
│   │   ├── pages/             # 页面组件
│   │   ├── App.tsx            # 应用根组件
│   │   ├── main.tsx           # 入口文件
│   │   └── styles.css         # 全局样式
│   ├── index.html             # HTML 模板
│   ├── package.json           # Node 依赖
│   ├── tsconfig.json          # TypeScript 配置
│   └── vite.config.mjs        # Vite 配置
├── data/                      # 数据目录（数据库、录音、转写、摘要）
├── models/                    # FunASR 模型缓存
├── logs/                      # 日志目录
├── scripts/                   # 辅助脚本
├── docs/                      # 文档
└── README.md                  # 项目说明
```

---

## 4. 关键类与函数说明

### 4.1 数据模型

#### Recording
```python
class Recording(SQLModel, table=True):
    id: str                    # 主键，UUID
    filename: str              # 文件名
    original_path: str         # 原始文件路径
    normalized_path: str       # 归一化后路径（用于转写）
    duration_seconds: float    # 时长（秒）
    file_size_bytes: int       # 文件大小（字节）
    source_mtime: float        # 源文件修改时间
    format: str                # 文件格式（wav/mp3/m4a等）
    content_hash: str          # 文件内容哈希（去重）
    source_type: str           # 来源类型（upload/watch）
    source_path: str           # 来源路径
    tags: str                  # 标签（逗号分隔）
    status: str                # 状态（uploaded/queued/transcribing/transcribed/completed/error）
    error_message: str         # 错误信息
    created_at: datetime       # 创建时间
    updated_at: datetime       # 更新时间
```

**状态流转**:
- `uploaded` → `queued` → `normalizing` → `transcribing` → `transcribed` → `completed`
- 任何阶段出错 → `error`

#### Task
```python
class Task(SQLModel, table=True):
    id: str                    # 主键，UUID
    recording_id: str          # 关联的录音ID
    task_type: str             # 任务类型（transcription/summary:xxx）
    status: str                # 状态（queued/running/completed/error/cancelled）
    progress: int              # 进度（0-100）
    error_message: str         # 错误信息
    result_path: str           # 结果文件路径
    created_at: datetime       # 创建时间
    updated_at: datetime       # 更新时间
    started_at: datetime       # 开始时间
    completed_at: datetime     # 完成时间
```

#### TranscriptSegment
```python
class TranscriptSegment(SQLModel, table=True):
    id: str                    # 主键，UUID
    recording_id: str          # 关联的录音ID
    start_time: float          # 开始时间（秒）
    end_time: float            # 结束时间（秒）
    speaker: str               # 说话人标识
    text: str                  # 转写文本
    sequence: int              # 序号（用于排序）
```

#### Summary
```python
class Summary(SQLModel, table=True):
    id: str                    # 主键，UUID
    recording_id: str          # 关联的录音ID
    mode: str                  # 摘要模板类型
    content: str               # 摘要内容
    created_at: datetime       # 创建时间
```

#### WatchEvent
```python
class WatchEvent(SQLModel, table=True):
    id: str                    # 主键，UUID
    file_path: str             # 文件路径
    filename: str              # 文件名
    status: str                # 状态（imported/duplicate_skipped/skipped/error）
    reason: str                # 原因说明
    recording_id: str          # 关联的录音ID（如有）
    duplicate_of_id: str       # 重复文件关联ID
    content_hash: str          # 文件内容哈希
    file_size: int             # 文件大小
    file_mtime: float          # 修改时间
    created_at: datetime       # 创建时间
```

### 4.2 服务层

#### ASRService
**文件**: `backend/app/services/asr_service.py`

| 方法 | 功能 |
|------|------|
| `package_available()` | 检查 FunASR 是否可用 |
| `transcribe(audio_path)` | 执行语音转写，返回 Segment 列表 |

**核心实现**:
```python
def transcribe(self, audio_path: Path) -> list[Segment]:
    # 使用 FunASR AutoModel 进行转写
    model = self._load_model()
    result = model.generate(input=str(audio_path), batch_size_s=300)
    # 解析结果并转换为 Segment 列表
    segments = self._segments_from_text_and_timestamps(text, timestamps)
    return segments
```

**并发控制**: 使用 `threading.BoundedSemaphore` 限制同时运行的转写任务数，默认值由 `asr_max_concurrency` 配置。

#### SummaryService
**文件**: `backend/app/services/summary_service.py`

| 方法 | 功能 |
|------|------|
| `configured()` | 检查 LLM 是否配置正确 |
| `generate(transcript, mode)` | 生成摘要，mode 指定模板类型 |

**支持的摘要模板**:

| 模板 ID | 名称 | 描述 |
|---------|------|------|
| `structured_summary` | 结构化摘要 | 背景、主题、关键结论、后续事项 |
| `meeting_minutes` | 会议纪要 | 议题、结论、风险、责任人 |
| `action_items` | 待办事项 | 负责人、事项、截止时间、优先级 |
| `decisions_risks` | 决策与风险 | 决策、风险、阻塞点、未决问题 |
| `executive_brief` | 管理层简报 | 一句话概览、关键结论 |
| `polished_transcript` | 转写内容规整 | 修正口语冗余，保持原意 |

**LLM 提供商支持**:
- `deepseek`: DeepSeek API
- `tongyi`/`qwen`: 通义千问
- `mimo`: 小米 MiMo

#### AudioService
**文件**: `backend/app/services/audio_service.py`

| 方法 | 功能 |
|------|------|
| `normalize(source, recording_id)` | 将音频转换为单声道 16kHz WAV |
| `duration_seconds(source)` | 获取音频时长 |
| `ffmpeg_available()` | 检查 ffmpeg 是否可用 |

**归一化命令**:
```bash
ffmpeg -y -i input.mp3 -ac 1 -ar 16000 output.wav
```

#### TaskService
**文件**: `backend/app/services/task_service.py`

| 方法 | 功能 |
|------|------|
| `create_or_get_task(session, recording, task_type)` | 创建或获取已有任务 |
| `recover_interrupted_tasks(session)` | 恢复中断的任务（应用重启后） |
| `cancel_task(session, task_id)` | 取消任务 |

**任务状态**: `queued` → `running` → `completed` / `error` / `cancelled`

#### DirectoryWatcher
**文件**: `backend/app/services/watch_service.py`

| 方法 | 功能 |
|------|------|
| `start()` | 启动监控任务 |
| `stop()` | 停止监控任务 |
| `scan_once(force_stable)` | 执行一次目录扫描 |

**监控逻辑**:
1. 定时扫描配置的监控目录（默认 10 秒间隔）
2. 文件稳定检测：大小和修改时间连续两次扫描一致视为稳定
3. 跳过已处理过的文件（通过 content_hash 判断）
4. 跳过不支持的文件格式
5. 自动将新音频入库

### 4.3 工作流

#### workflow.py
**文件**: `backend/app/pipeline/workflow.py`

| 函数 | 功能 |
|------|------|
| `run_transcription_task(task_id)` | 执行转写任务 |
| `run_summary_task(task_id, mode)` | 执行摘要任务 |

**转写任务流程**:
```
1. 更新任务状态为 running
2. 归一化音频（转换为单声道 16kHz WAV）
3. 更新录音时长
4. 使用 FunASR 进行转写
5. 保存转写片段到数据库和 JSON 文件
6. 更新任务状态为 completed
```

**摘要任务流程**:
```
1. 更新任务状态为 running
2. 从数据库读取转写片段
3. 拼接转写文本
4. 调用 SummaryService 生成摘要
5. 保存摘要到数据库和 Markdown 文件
6. 更新任务状态为 completed
```

---

## 5. API 接口

### 5.1 录音管理

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/recordings` | 列表录音（支持搜索和标签筛选） |
| POST | `/api/recordings` | 上传录音 |
| GET | `/api/recordings/{id}` | 获取单个录音详情 |
| DELETE | `/api/recordings/{id}` | 删除录音 |
| PATCH | `/api/recordings/{id}/tags` | 更新标签 |
| PATCH | `/api/recordings/{id}/segments/{segment_id}` | 更新转写片段 |
| POST | `/api/recordings/batch-delete` | 批量删除 |
| GET | `/api/recordings/{id}/audio` | 播放音频（支持 Range 请求） |
| GET | `/api/recordings/{id}/exports/transcript` | 导出转写（md/txt/json/srt/docx） |

### 5.2 转写接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/transcribe/{recording_id}` | 发起转写任务 |
| POST | `/api/transcribe/batch` | 批量转写 |

### 5.3 摘要接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/summary/templates` | 获取摘要模板列表 |
| POST | `/api/summary/{recording_id}` | 发起摘要任务 |
| POST | `/api/summary/batch` | 批量摘要 |
| GET | `/api/summaries/{id}/export` | 导出摘要（md/txt/docx） |
| DELETE | `/api/summaries/{id}` | 删除摘要 |

### 5.4 任务接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/tasks/{id}` | 获取任务状态 |
| DELETE | `/api/tasks/{id}` | 取消任务 |

### 5.5 设置接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/settings` | 获取设置 |
| POST | `/api/settings` | 更新设置 |

### 5.6 目录监控接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/watch/events` | 获取监控事件 |
| POST | `/api/watch/scan` | 手动触发扫描 |

### 5.7 健康检查

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/health` | 健康检查 |

---

## 6. 依赖关系

### 6.1 Python 依赖

| 依赖 | 用途 |
|------|------|
| `fastapi` | Web 框架 |
| `uvicorn` | ASGI 服务器 |
| `sqlmodel` | ORM |
| `pydantic-settings` | 配置管理 |
| `python-multipart` | 文件上传 |
| `httpx` | HTTP 客户端 |
| `openai` | OpenAI API 客户端 |
| `pydub` | 音频处理 |
| `soundfile` | 音频信息读取 |
| `numpy` | 数值计算 |
| `funasr` | 语音转写 |
| `modelscope` | 模型管理 |
| `torch` | PyTorch |
| `torchaudio` | 音频处理 |

### 6.2 外部工具

| 工具 | 用途 |
|------|------|
| `ffmpeg` | 音频格式转换 |

---

## 7. 配置说明

### 7.1 配置文件

配置文件位于 `backend/.env`，支持以下环境变量：

#### 应用配置
```env
APP_ENV=local
APP_HOST=127.0.0.1
APP_PORT=8000
```

#### 数据目录
```env
DATA_DIR=../data
MODEL_DIR=../models/funasr
LOG_DIR=../logs
TRANSCRIPT_DIR=../data/transcripts
SUMMARY_DIR=../data/summaries
```

#### ASR 配置
```env
ASR_DEVICE=cpu
ASR_MODEL=paraformer-zh
ASR_VAD_MODEL=fsmn-vad
ASR_PUNC_MODEL=ct-punc
ASR_TIMESTAMP_MODEL=fa-zh
ASR_ENABLE_DIARIZATION=false
ASR_MAX_CONCURRENCY=1
```

#### LLM 配置
```env
LLM_PROVIDER=deepseek
LLM_API_KEY=your_api_key
LLM_BASE_URL=
LLM_MODEL=
LLM_MAX_COMPLETION_TOKENS=2048
LLM_TIMEOUT_SECONDS=60
LLM_RETRY_ATTEMPTS=3
LLM_TEMPERATURE=0.2
LLM_TOP_P=
```

#### MiMo 专用配置
```env
MIMO_API_KEY=
MIMO_THINKING=disabled
```

#### 目录监控配置
```env
WATCH_ENABLED=false
WATCH_DIR=
WATCH_RECURSIVE=true
WATCH_INTERVAL_SECONDS=10
```

#### 安全配置
```env
API_TOKEN=
CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

### 7.2 LLM 提供商预设

| Provider | 默认接口 | 默认模型 |
|----------|----------|----------|
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` |
| `tongyi`/`qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| `mimo` | `https://token-plan-cn.xiaomimimo.com/v1` | `mimo-v2.5` |

---

## 8. 项目运行

### 8.1 环境要求

- Python 3.10-3.12
- Node.js 20+
- ffmpeg
- Git

### 8.2 快速开始

```powershell
# 克隆仓库
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace

# 安装依赖并构建前端
.\setup.ps1

# 启动服务
.\start-all.ps1
```

### 8.3 访问地址

```
http://127.0.0.1:8000
```

### 8.4 常用命令

| 命令 | 功能 |
|------|------|
| `.\setup.ps1` | 安装依赖并构建前端 |
| `.\start-all.ps1` | 启动后端和网页服务 |
| `.\stop-all.ps1` | 停止服务 |
| `.\check.ps1` | 检查依赖、端口、配置和服务状态 |

### 8.5 测试

```powershell
cd backend
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

---

## 9. 数据存储

### 9.1 目录结构

```
data/
├── app.db                    # SQLite 数据库
├── recordings/               # 上传的录音文件
├── normalized/               # 归一化后的音频（用于转写）
├── transcripts/              # 转写结果 JSON
└── summaries/                # 摘要结果 Markdown
```

### 9.2 数据库表

| 表名 | 说明 |
|------|------|
| `recording` | 录音记录 |
| `task` | 任务记录 |
| `transcript_segment` | 转写片段 |
| `summary` | 摘要记录 |
| `watch_event` | 目录监控事件 |

---

## 10. 安全注意事项

1. **API Token**: 配置 `API_TOKEN` 后，所有请求需要在 Header 中携带 `Authorization: Bearer <token>`
2. **敏感配置**: `backend/.env` 包含 API Key，不应提交到版本控制
3. **文件路径安全**: 使用 `resolve()` 和 `relative_to()` 防止路径遍历攻击
4. **CORS**: 默认只允许本地开发地址，生产环境应配置正确的 CORS 来源
5. **文件上传限制**: 单文件最大 500MB，防止恶意上传

---

## 11. 故障排查

### 11.1 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Python/node/ffmpeg 找不到 | 未加入系统 PATH | 重新安装并确认 PATH |
| 页面打不开 | 端口被占用或服务未启动 | 检查端口，重启服务 |
| 转写失败 | FunASR 模型未下载或损坏 | 删除 models/funasr 重新下载 |
| 摘要不可用 | LLM API Key 未配置 | 在设置中配置 API Key |
| 首次转写慢 | FunASR 模型正在下载 | 等待模型下载完成 |

### 11.2 日志查看

日志文件位于 `logs/` 目录，包含后端运行日志和错误信息。

---

## 附录：状态机

### 录音状态流转

```
uploaded ──转写──→ queued ──开始──→ normalizing ──完成──→ transcribing ──完成──→ transcribed ──摘要──→ completed
     │              │                    │                   │                   │                   │
     │              │                    │                   │                   │                   │
     └──────────────┴────────────────────┴───────────────────┴───────────────────┴───────────────────┘
                                         ↓
                                      error
```

### 任务状态流转

```
queued ──开始──→ running ──完成──→ completed
  │                 │                  │
  │                 │                  ↓
  │                 └──────────────→ error
  ↓
cancelled
```
