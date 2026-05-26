# AI Recorder 后端项目结构说明

## 📋 目录

- [项目概述](#项目概述)
- [目录结构](#目录结构)
- [核心模块说明](#核心模块说明)
- [数据模型](#数据模型)
- [API 接口](#api-接口)
- [服务层](#服务层)
- [配置说明](#配置说明)

---

## 项目概述

AI Recorder 后端是一个 FastAPI 应用，负责：
- 录音文件管理
- 语音转写（本地 FunASR）
- 智能摘要（云端 LLM）
- 文件导出
- 目录监控
- API 服务

---

## 目录结构

```
backend/
├── app/
│   ├── api/                          # API 路由层
│   │   ├── __init__.py
│   │   ├── auth.py                   # 认证中间件（API Token）
│   │   ├── filesystem.py             # 文件系统操作 API
│   │   ├── health.py                 # 健康检查 API
│   │   ├── recordings.py             # 录音管理 API
│   │   ├── settings.py               # 设置 API
│   │   ├── summary.py                # 摘要 API
│   │   ├── tasks.py                  # 任务管理 API
│   │   ├── transcribe.py             # 转写 API
│   │   └── watch.py                  # 目录监控 API
│   ├── db/                           # 数据库层
│   │   ├── __init__.py
│   │   └── database.py               # SQLite 数据库连接和初始化
│   ├── models/                       # 数据模型层
│   │   ├── __init__.py
│   │   ├── recording.py              # Recording 模型（录音记录）
│   │   ├── summary.py                # Summary 模型（摘要）
│   │   ├── task.py                   # Task 模型（任务）
│   │   ├── transcript.py             # TranscriptSegment 模型（转写片段）
│   │   └── watch_event.py            # WatchEvent 模型（监控事件）
│   ├── pipeline/                     # 工作流管道
│   │   ├── __init__.py
│   │   └── workflow.py               # 转写/摘要任务编排执行
│   ├── services/                     # 业务服务层
│   │   ├── __init__.py
│   │   ├── asr_service.py            # ASR 转写服务（FunASR）
│   │   ├── audio_service.py          # 音频处理服务（FFmpeg）
│   │   ├── docx_export.py            # DOCX 导出服务
│   │   ├── export_names.py           # 导出文件名生成
│   │   ├── file_service.py           # 文件操作服务
│   │   ├── runtime_log.py            # 运行时日志服务
│   │   ├── summary_service.py        # 摘要服务（大模型）
│   │   ├── task_service.py           # 任务管理服务
│   │   └── watch_service.py          # 目录监控服务
│   ├── config.py                     # 配置管理（环境变量）
│   └── main.py                       # FastAPI 应用入口
├── tests/                            # 单元测试
│   ├── test_api_auth.py              # 认证测试
│   ├── test_recording_audio.py       # 录音音频测试
│   ├── test_recording_management.py  # 录音管理测试
│   └── test_task_service.py          # 任务服务测试
├── .env.example                      # 环境变量示例
├── requirements.txt                  # Python 依赖
└── start.ps1                         # 后端启动脚本
```

---

## 核心模块说明

### 1. API 路由层 (`api/`)

| 文件 | 功能 |
|------|------|
| **auth.py** | 认证中间件，验证 `X-API-Token` 请求头 |
| **health.py** | 健康检查，返回系统状态、资源使用 |
| **recordings.py** | 录音管理（列表、上传、详情、删除、编辑、导出） |
| **settings.py** | 设置管理（获取/更新设置） |
| **summary.py** | 摘要相关（触发摘要、获取模板、导出） |
| **tasks.py** | 任务管理（获取状态、取消任务） |
| **transcribe.py** | 转写相关（触发转写） |
| **watch.py** | 目录监控（获取事件、手动扫描） |
| **filesystem.py** | 文件系统操作（选择文件夹） |

---

### 2. 数据库层 (`db/`)

#### database.py
- 数据库连接配置
- SQLModel 引擎创建
- 数据库表创建
- Session 管理

---

### 3. 数据模型层 (`models/`)

| 模型 | 说明 |
|------|------|
| **Recording** | 录音记录，包含文件信息、状态、时长等 |
| **TranscriptSegment** | 转写片段，包含时间戳、说话人、文本 |
| **Summary** | 摘要，包含内容、创建时间 |
| **Task** | 任务，包含类型、状态、进度、错误 |
| **WatchEvent** | 目录监控事件，包含文件、状态、原因 |

---

### 4. 工作流管道 (`pipeline/`)

#### workflow.py
- `run_transcription_task()`：执行转写任务
  1. 归一化音频
  2. ASR 转写
  3. 保存到数据库和文件
  4. 更新任务状态
- `run_summary_task()`：执行摘要任务
  1. 读取转写片段
  2. 调用 LLM 生成摘要
  3. 保存到数据库和文件
  4. 更新任务状态

---

### 5. 业务服务层 (`services/`)

详见 [服务层](#服务层)

---

## 数据模型

### Recording（录音）
```python
class Recording(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()), primary_key=True)
    filename: str
    original_path: Optional[str] = None
    normalized_path: Optional[str] = None
    duration_seconds: Optional[float] = None
    file_size_bytes: Optional[int] = None
    source_mtime: Optional[float] = None
    format: Optional[str] = None
    content_hash: Optional[str] = None
    source_type: str = "upload"  # upload / watch
    source_path: Optional[str] = None
    tags: Optional[str] = None
    status: str = "uploaded"  # uploaded / queued / normalizing / transcribing / transcribed / completed / error
    error_message: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
```

### Task（任务）
```python
class Task(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()), primary_key=True)
    recording_id: str = Field(foreign_key="recording.id")
    task_type: str  # transcription / summary:xxx
    status: str = "queued"  # queued / running / completed / error / cancelled
    progress: int = 0
    error_message: Optional[str] = None
    result_path: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
```

---

## API 接口

### 录音管理
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/recordings` | 获取录音列表（支持搜索、标签、状态） |
| POST | `/api/recordings` | 上传录音文件 |
| GET | `/api/recordings/{id}` | 获取单个录音详情 |
| DELETE | `/api/recordings/{id}` | 删除录音（级联删除关联数据） |
| PATCH | `/api/recordings/{id}/tags` | 更新录音标签 |
| PATCH | `/api/recordings/{id}/segments/{segment_id}` | 编辑转写片段 |
| GET | `/api/recordings/{id}/audio` | 播放音频（支持 Range） |
| GET | `/api/recordings/{id}/exports/transcript` | 导出转写（md/txt/json/srt/docx） |
| POST | `/api/recordings/batch-delete` | 批量删除 |

### 转写
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/transcribe/{id}` | 触发转写任务 |
| POST | `/api/transcribe/batch` | 批量转写 |

### 摘要
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/summary/templates` | 获取摘要模板列表 |
| POST | `/api/summary/{id}` | 触发摘要任务 |
| POST | `/api/summary/batch` | 批量摘要 |
| GET | `/api/summaries/{id}/export` | 导出摘要（md/txt/docx） |
| DELETE | `/api/summaries/{id}` | 删除摘要 |

### 任务
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks/{id}` | 获取任务状态 |
| POST | `/api/tasks/{id}/cancel` | 取消任务 |

### 其他
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/api/settings` | 获取设置 |
| POST | `/api/settings` | 更新设置 |
| GET | `/api/watch/events` | 获取监控事件 |
| POST | `/api/watch/scan` | 手动扫描 |

---

## 服务层

### ASRService（转写服务）
- `package_available()`：检查 FunASR 是否可用
- `transcribe(audio_path)`：执行转写，返回 Segment 列表
- 并发控制：使用 Semaphore 限制同时运行的转写任务数

### SummaryService（摘要服务）
- `configured()`：检查 LLM 是否配置正确
- `generate(transcript, mode)`：生成摘要，mode 指定模板
- 支持的模板：
  - `structured_summary`：结构化摘要
  - `meeting_minutes`：会议纪要
  - `action_items`：待办事项
  - `decisions_risks`：决策与风险
  - `executive_brief`：管理层简报
  - `polished_transcript`：转写内容规整
- 支持的 LLM：DeepSeek、通义千问、小米 MiMo

### AudioService（音频处理服务）
- `normalize(source, recording_id)`：将音频转换为单声道 16kHz WAV
- `duration_seconds(source)`：获取音频时长
- `ffmpeg_available()`：检查 ffmpeg 是否可用

### TaskService（任务管理服务）
- `create_or_get_task(session, recording, task_type)`：创建或获取已有任务
- `recover_interrupted_tasks(session)`：恢复中断的任务
- `cancel_task(session, task_id)`：取消任务

### WatchService（目录监控服务）
- `start()`：启动监控任务
- `stop()`：停止监控任务
- `scan_once(force_stable)`：执行一次目录扫描
- 监控逻辑：
  1. 定时扫描配置的目录
  2. 文件稳定检测（大小和修改时间不变）
  3. 去重检测（通过 content_hash）
  4. 自动入库

---

## 配置说明

### 环境变量 (.env)
```env
# 应用
APP_ENV=local
APP_HOST=127.0.0.1
APP_PORT=8000

# 数据目录
DATA_DIR=../data
MODEL_DIR=../models/funasr
LOG_DIR=../logs
TRANSCRIPT_DIR=../data/transcripts
SUMMARY_DIR=../data/summaries

# ASR
ASR_DEVICE=cpu
ASR_MODEL=paraformer-zh
ASR_VAD_MODEL=fsmn-vad
ASR_PUNC_MODEL=ct-punc
ASR_TIMESTAMP_MODEL=fa-zh
ASR_ENABLE_DIARIZATION=false
ASR_MAX_CONCURRENCY=1

# LLM
LLM_PROVIDER=deepseek
LLM_API_KEY=your_api_key
LLM_BASE_URL=
LLM_MODEL=
LLM_MAX_COMPLETION_TOKENS=2048
LLM_TIMEOUT_SECONDS=60
LLM_RETRY_ATTEMPTS=3
LLM_TEMPERATURE=0.2
LLM_TOP_P=

# MiMo 专用
MIMO_API_KEY=
MIMO_THINKING=disabled

# 目录监控
WATCH_ENABLED=false
WATCH_DIR=
WATCH_RECURSIVE=true
WATCH_INTERVAL_SECONDS=10

# 安全
API_TOKEN=
CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

---

## 相关文档

- [../PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) - 整体项目结构
- [../CODE_WIKI.md](../CODE_WIKI.md) - 代码维基
- [.env.example](.env.example) - 环境变量示例
