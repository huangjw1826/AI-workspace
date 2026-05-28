# AI Recorder

AI Recorder 是一个面向 Windows 本机的录音整理工具，集成音频入库、本地离线转写、云端大模型摘要、多格式导出和目录监控功能。同时提供原生 Android 客户端，支持远程访问 PC 端录音库。

**当前版本：3.0** | [更新日志](./CHANGELOG.md) | [产品路线图](./docs/product/roadmap.md)

## 核心特性

- 🎙️ **本地转写**：使用 FunASR 在本机完成中文语音转文字，隐私安全
- 🤖 **智能摘要**：支持 DeepSeek、通义千问、小米 MiMo 等多种大模型
- 📱 **远程访问**：原生 Android 客户端可远程访问 PC 端录音库
- 📤 **多格式导出**：支持 Markdown、TXT、JSON、SRT、DOCX 等格式
- 🔍 **智能搜索**：支持文件名、标签、转写内容全文搜索
- 👁️ **目录监控**：自动发现并入库新音频文件
- ⚡ **实时同步**：SSE 实时推送任务进度和系统状态
- 🎯 **可靠任务**：任务可复用、取消和恢复，避免重复处理
- 🎧 **音频播放**：点击时间戳跳转播放位置，播放时高亮当前片段
- ✏️ **转写校对**：可视化编辑转写片段，保存后用于摘要和导出

## 功能概览

| 模块 | 功能 |
|------|------|
| **录音库管理** | 集中管理音频文件、时长、来源、创建时间和处理状态 |
| **文件上传** | 支持 `wav`、`mp3`、`m4a`、`flac`、`aac`、`ogg` 等格式（最大 500MB） |
| **音频播放** | 原生播放器，支持点击转写时间戳跳转播放位置 |
| **转写校对** | 可视化编辑转写片段，支持实时保存 |
| **本地转写** | FunASR 离线转写，无需上传云端 |
| **智能摘要** | 多种模板（会议纪要、结构化摘要、待办事项等） |
| **多份摘要** | 同一条录音可保留多次摘要结果 |
| **批量操作** | 批量转写、批量摘要、批量删除 |
| **目录监控** | 定时扫描指定目录，新音频自动入库 |
| **Android 访问** | 通过公网域名 + API Token 远程访问录音库 |
| **实时状态** | SSE 实时推送任务进度和系统状态 |

## 项目结构

```
AI-workspace/
├── backend/              # FastAPI 后端服务
│   ├── app/
│   │   ├── api/        # API 路由（认证、录音、转写、摘要、任务等）
│   │   ├── services/    # 业务服务（ASR、音频、摘要、任务、监控等）
│   │   ├── models/      # 数据模型（SQLModel）
│   │   ├── pipeline/    # 工作流编排
│   │   └── db/          # 数据库层
│   ├── tests/           # 单元测试
│   └── requirements.txt
├── frontend/             # React 前端应用
│   ├── src/
│   │   ├── pages/      # 页面组件（录音库、监控、设置、健康）
│   │   ├── components/ # 可复用组件
│   │   ├── hooks/      # 自定义 Hooks
│   │   ├── lib/        # 工具函数和 API 客户端
│   │   └── stores/      # 状态管理
│   └── package.json
├── android/              # Android 原生客户端
│   ├── app/src/main/java/com/airecorder/android/
│   │   ├── data/       # 数据层（Repository、ApiService、DataStore）
│   │   ├── di/         # 依赖注入（Hilt）
│   │   └── ui/         # UI 层（Compose、ViewModel）
│   └── build.gradle.kts
├── data/                 # 数据库、音频、转写和摘要数据
├── models/               # FunASR 离线模型缓存
├── logs/                 # 运行日志
├── docs/                 # 项目文档
│   ├── product/         # 产品路线图和版本规划
│   ├── decisions/       # 架构决策记录（ADR）
│   └── *.md            # 各类说明文档
├── scripts/              # 辅助脚本
├── PROJECT_STRUCTURE.md  # 整体项目结构说明
├── CODE_WIKI.md          # 代码维基
├── CHANGELOG.md          # 变更日志
└── README.md             # 本文件
```

## 快速开始

### 环境要求

- Python 3.10 ~ 3.12
- Node.js 20+
- FFmpeg（需加入系统 PATH）
- Git

### 启动步骤

```powershell
# 1. 克隆项目
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace

# 2. 安装依赖并构建前端
.\setup.ps1

# 3. 启动服务
.\start-all.ps1

# 4. 访问应用
# 打开浏览器：http://127.0.0.1:8000
```

### 常用命令

```powershell
.\setup.ps1          # 安装依赖并构建前端
.\start-all.ps1      # 启动后端和网页服务
.\stop-all.ps1       # 停止服务
.\check.ps1          # 检查依赖、端口、配置和服务状态
```

## 首次使用流程

1. 打开 `http://127.0.0.1:8000`
2. 进入「设置」，确认存储目录配置
3. 如需摘要功能，配置大模型服务商和 API Key
4. 在「录音库」上传音频，或在「目录监控」配置扫描目录
5. 对录音点击「转写」
6. 转写完成后选择摘要模板生成摘要
7. 校对转写片段，维护标签或进行批量处理
8. 下载导出文件（支持 Markdown/TXT/JSON/SRT/DOCX）

> **注意**：目录监控只负责发现并入库新音频，不会自动转写或摘要，避免误处理大量文件。

## 配置大模型摘要

转写不需要 API Key，摘要功能需要配置。

### 配置方式

在网页「设置」中配置，或直接编辑：

```text
backend/.env
```

### 常用配置项

```env
API_TOKEN=your-secure-token
LLM_PROVIDER=deepseek
LLM_API_KEY=your-api-key
LLM_TIMEOUT_SECONDS=60
```

### 支持的服务商

| Provider | 默认接口 | 默认模型 |
|----------|----------|----------|
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| `mimo` | OpenAI 兼容接口 | `MiMo-V2.5` |

更多说明见 [docs/cloud-llm-providers.md](docs/cloud-llm-providers.md)

## 技术栈

### PC 端 - 后端

| 技术 | 说明 |
|------|------|
| Python 3.10+ | 编程语言 |
| FastAPI | Web 框架 |
| SQLModel | ORM |
| SQLite | 数据库 |
| FunASR | 语音转写 |
| FFmpeg | 音频处理 |
| OpenAI SDK | LLM 调用 |

### PC 端 - 前端

| 技术 | 说明 |
|------|------|
| React 19 | UI 框架 |
| TypeScript | 类型安全 |
| Vite | 构建工具 |
| Lucide React | 图标库 |

### Android 端

| 技术 | 说明 |
|------|------|
| Kotlin | 编程语言 |
| Jetpack Compose | UI 框架 |
| Material Design 3 | 设计系统 |
| Retrofit + OkHttp | 网络请求 |
| Hilt | 依赖注入 |
| DataStore | 本地存储 |
| Kotlin Coroutines | 异步编程 |

详细技术栈说明见 [docs/TECH_STACK.md](docs/TECH_STACK.md)

## 本地数据和隐私

以下目录不会上传到 Git 仓库：

```
backend/.env            # 环境变量（含 API Key）
backend/.venv/          # Python 虚拟环境
frontend/node_modules/  # Node.js 依赖
frontend/dist/          # 前端构建产物
data/                   # 数据库、录音、转写和摘要
models/                 # FunASR 模型缓存
logs/                   # 运行日志
```

**建议备份**：
- `data/` - 数据库、录音、转写和摘要结果
- `backend/.env` - 配置文件

## Android 客户端

Android 客户端支持远程访问 PC 端录音库：

**功能**：
- 查看录音列表和详情
- 查看转写和摘要内容
- 上传音频到 PC 端
- 系统健康状态监控
- 删除录音
- 实时任务状态推送（SSE）

**配置方式**：
1. 使用 Android Studio 打开 `android/` 目录
2. 配置服务器地址和 API Token
3. 构建并安装到设备

详细说明见 [android/README.md](android/README.md) 和 [docs/android-remote-access.md](docs/android-remote-access.md)

## 故障排查

先运行健康检查：

```powershell
.\check.ps1
```

**常见问题**：

| 问题 | 解决方案 |
|------|----------|
| `python`/`node`/`ffmpeg` 找不到 | 重新安装并加入系统 PATH |
| 页面打不开 | 确认访问 `http://127.0.0.1:8000`（非 HTTPS） |
| 端口被占用 | 先运行 `.\stop-all.ps1` 再启动 |
| 摘要不可用 | 检查设置中的 API Key |
| 首次转写慢 | FunASR 模型正在下载初始化 |

更多说明见 [docs/troubleshooting.md](docs/troubleshooting.md)

## 相关文档

| 文档 | 说明 |
|------|------|
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | 整体项目结构 |
| [CODE_WIKI.md](CODE_WIKI.md) | 代码维基 |
| [CHANGELOG.md](CHANGELOG.md) | 变更日志 |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | 技术栈说明 |
| [docs/product/roadmap.md](docs/product/roadmap.md) | 产品路线图 |
| [backend/PROJECT_STRUCTURE.md](backend/PROJECT_STRUCTURE.md) | 后端详细结构 |
| [frontend/PROJECT_STRUCTURE.md](frontend/PROJECT_STRUCTURE.md) | 前端详细结构 |
| [android/PROJECT_STRUCTURE.md](android/PROJECT_STRUCTURE.md) | Android 端详细结构 |
| [docs/decisions/](docs/decisions/) | 架构决策记录 |

## 版本历史

- **v3.0** (2026-05) - 任务可靠性、音频播放、转写校对、搜索标签、批量操作
- **v2.0** (早期) - Android 远程访问支持
- **v1.0** (早期) - 基础录音管理功能

查看完整更新历史：[CHANGELOG.md](CHANGELOG.md)

## 许可证

MIT License
