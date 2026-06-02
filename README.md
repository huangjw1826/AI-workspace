# AI Recorder

> 面向 Windows 的本地录音整理工作台 — 离线转写 + LLM 智能摘要 + Android 远程访问

**当前版本：v3.1.0** | [变更日志](./CHANGELOG.md) | [项目结构](./PROJECT_STRUCTURE.md) | [代码维基](./CODE_WIKI.md)

---

## 项目简介

AI Recorder 是一款运行在 Windows 本机上的录音整理工具。它深度整合了**本地语音转写**、**大模型智能摘要**、**多格式导出**和**目录自动监控**四大能力，将所有数据（音频、转写、摘要）保存在本地，同时通过原生 Android 客户端提供安全的远程访问。

**核心设计理念**：录音不离开本机，转写不依赖云端，只有摘要可选择性调用 LLM。

---

## 核心特性

| 特性 | 说明 |
|------|------|
| 🎙️ **本地转写** | FunASR CPU 离线转写，无需网络，数据不泄露 |
| 🤖 **智能摘要** | 支持 DeepSeek / 通义千问 / 小米 MiMo 等 OpenAI 兼容接口 |
| 📱 **远程访问** | 原生 Android 客户端通过 Cloudflare Tunnel 安全访问录音库 |
| 🎧 **音频播放** | 点击转写时间戳跳转播放，播放时高亮当前片段 |
| ✏️ **转写校对** | 可视化编辑转写片段，实时保存并用于后续摘要和导出 |
| 🔍 **智能搜索** | 全文搜索文件名、标签、转写和摘要内容，搜索命中高亮 |
| 📤 **多格式导出** | 转写支持 MD / TXT / JSON / SRT / DOCX；摘要支持 MD / TXT / DOCX |
| 👁️ **目录监控** | 定时扫描指定目录，新音频自动入库（含文件稳定性检测和去重） |
| ⚡ **实时推送** | SSE 实时推送任务进度和系统状态 |
| 🎯 **任务可靠** | 任务可复用、可取消、可恢复，重启后自动标记中断任务 |

---

## 功能矩阵

### PC 端

| 模块 | 核心功能 |
|------|---------|
| **录音库** | 上传 (≤500MB)、列表/搜索/标签筛选、详情查看、批量操作 |
| **音频播放** | HTML5 原生播放器、Range 请求支持拖动、转写时间戳联动 |
| **转写** | FunASR 离线转写、并发控制、任务恢复、转写校对 |
| **摘要** | 6 种模板（会议纪要/结构化摘要/待办事项/决策风险/管理层简报/转写规整）、多轮摘要 |
| **导出** | 转写 5 种格式 + 摘要 3 种格式，文件名自动生成 |
| **监控** | 定时扫描 + 文件稳定检测 + SHA-256 去重 + 监控事件日志 |
| **设置** | LLM 服务商配置、存储路径、监控目录、API Token 管理 |

### Android 端

| 模块 | 功能 |
|------|------|
| **录音库** | 查看 PC 端录音列表、搜索、上传音频 |
| **详情页** | 播放 Tab (流式播放 + 转写联动)、摘要 Tab、任务 Tab、信息 Tab |
| **健康面板** | 系统状态、资源使用、隧道状态 |
| **设置** | 服务器地址、API Token 配置、连接测试 |

---

## 技术栈

### 后端 (Python)

| 技术 | 用途 |
|------|------|
| FastAPI + Uvicorn | Web 框架和 ASGI 服务器 |
| SQLModel + SQLite | ORM 和本地数据库 |
| FunASR (Modelscope) | 语音转写引擎 |
| FFmpeg + pydub | 音频格式转换 |
| OpenAI SDK | LLM API 调用（兼容接口） |
| pydantic-settings | 环境变量配置管理 |

### 前端 (TypeScript)

| 技术 | 版本 | 用途 |
|------|------|------|
| React | ^19.2.5 | UI 框架 |
| TypeScript | ^6.0.3 | 类型安全 |
| Vite | ^8.0.10 | 构建工具 |
| Lucide React | ^0.487.0 | 图标库 |
| React Query | latest | 数据获取和缓存 |
| Zustand | latest | 轻量状态管理 |

### Android (Kotlin)

| 技术 | 用途 |
|------|------|
| Jetpack Compose + Material 3 | UI 框架和设计系统 |
| Retrofit + OkHttp | HTTP 网络请求 |
| Hilt | 依赖注入 |
| DataStore Preferences | 本地键值存储 |
| Kotlin Coroutines + Flow | 异步和响应式编程 |

---

## 快速开始

### 环境要求

| 组件 | 要求 | 检查方式 |
|------|------|---------|
| Python | 3.10 ~ 3.12 | `python --version` |
| Node.js | 20+ | `node --version` |
| FFmpeg | 任意版本 | `ffmpeg -version` |
| Git | 任意版本 | `git --version` |

### 安装和启动

```powershell
# 1. 克隆项目
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace

# 2. 安装依赖并构建前端
.\setup.ps1

# 3. 一键启动
.\start-all.ps1

# 4. 访问应用
# 打开浏览器：http://127.0.0.1:8000
```

### 常用命令

```powershell
.\setup.ps1          # 安装 Python/Node 依赖并构建前端
.\start-all.ps1      # 启动后端 + 托管前端静态文件
.\stop-all.ps1       # 停止所有服务
.\check.ps1          # 环境检查：依赖/端口/配置/服务状态
```

---

## 使用流程

1. 打开 `http://127.0.0.1:8000`
2. 进入「**设置**」→ 确认存储目录配置
3. 如需摘要功能 → 配置大模型服务商和 API Key
4. 在「**录音库**」上传音频，或在「**目录监控**」配置扫描目录
5. 对录音点击「**转写**」→ 等待 FunASR 完成
6. 转写完成后 → 校对转写片段 → 选择模板生成「**摘要**」
7. 维护标签、批量操作或导出文件

> **注意**：目录监控只负责发现并入库新音频，不会自动触发转写或摘要，避免误处理大量文件。

---

## 配置参考

### 大模型配置

在网页「设置」中配置，或直接编辑 `backend/.env`：

```env
# 选择提供商：deepseek / tongyi / qwen / mimo
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-your-api-key-here

# 可选：自定义接口地址和模型
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat

# 超时和重试
LLM_TIMEOUT_SECONDS=60
LLM_RETRY_ATTEMPTS=3
```

### 支持的服务商

| Provider | 默认接口 | 默认模型 |
|----------|----------|----------|
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| `mimo` | `https://token-plan-cn.xiaomimimo.com/v1` | `mimo-v2.5` |

### 远程访问

配置 Android 客户端远程访问 PC 端：

```env
# 开启远程访问
API_TOKEN=your-secure-token
REMOTE_ACCESS_ENABLED=true
REMOTE_ACCESS_HOSTNAME=your-domain.example.com
```

---

## 项目结构

```
AI-workspace/
├── backend/          # FastAPI 后端 (Python, ~5,500 行)
│   ├── app/api/      # 12 个 API 路由模块
│   ├── app/services/ # 10 个业务服务
│   ├── app/models/   # 7 个数据模型 (SQLModel)
│   └── app/pipeline/ # 转写/摘要工作流
├── frontend/         # React 前端 (TypeScript, ~3,100 行)
│   └── src/
│       ├── pages/    # 4 个页面 (录音库/监控/设置/健康)
│       ├── components/ # 12 个可复用组件
│       └── lib/      # API 客户端 + 类型定义
├── android/          # Android 原生客户端 (Kotlin, ~6,500 行)
│   └── app/src/main/java/com/airecorder/android/
│       ├── data/     # 数据层 (Repository + API + DataStore)
│       ├── di/       # Hilt 依赖注入
│       └── ui/       # Compose UI (17 组件 + 5 页面)
├── data/             # 数据库/音频/转写/摘要 (不提交 Git)
├── models/           # FunASR 模型缓存 (不提交 Git)
├── logs/             # 运行日志 (不提交 Git)
├── docs/             # 项目文档 (26 篇)
└── *.ps1 / *.bat     # 启动/停止/检查脚本
```

详细说明见 [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) 和 [CODE_WIKI.md](./CODE_WIKI.md)。

---

## Android 客户端

原生 Android 应用，通过 Cloudflare Tunnel + API Token 远程访问 PC 端录音库。

**环境要求**：JDK 17+ / Android SDK API 29+ / Android Studio Hedgehog+

**快速开始**：
1. Android Studio 打开 `android/` 目录
2. 等待 Gradle 同步完成
3. 配置服务器地址和 API Token
4. 连接设备或模拟器运行

详细说明见 [android/README.md](./android/README.md) 和 [docs/android-remote-access.md](./docs/android-remote-access.md)。

---

## 数据隐私

以下目录和文件**不提交到 Git**，且完全存储在本机：

| 目录/文件 | 内容 |
|-----------|------|
| `backend/.env` | 环境变量（含 API Key） |
| `backend/.venv/` | Python 虚拟环境 |
| `frontend/node_modules/` | Node.js 依赖 |
| `frontend/dist/` | 前端构建产物 |
| `data/` | 数据库、录音、转写、摘要 |
| `models/` | FunASR 模型缓存 |
| `logs/` | 运行日志 |

**建议备份**：`data/` 和 `backend/.env`

---

## 故障排查

先运行健康检查：

```powershell
.\check.ps1
```

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| `python`/`node`/`ffmpeg` 找不到 | 未加入系统 PATH | 重新安装并确认 PATH |
| 页面打不开 | 端口被占用 | 先 `.\stop-all.ps1` 再启动 |
| 端口问题 | 确认访问 `http://127.0.0.1:8000` | 注意不是 HTTPS |
| 摘要不可用 | LLM API Key 未配置 | 在设置中配置 API Key |
| 首次转写慢 | FunASR 模型下载中 | 等待模型初始化完成（一般 1-3 分钟） |
| 转写失败 | 模型损坏或不兼容 | 删除 `models/funasr/` 重新下载 |

更多详情：[docs/troubleshooting.md](./docs/troubleshooting.md)

---

## 版本历史

| 版本 | 时间 | 重大更新 |
|------|------|---------|
| **v3.0** | 2026-05 | 任务可靠性（恢复/复用/取消）、音频播放+时间轴联动、转写校对、全文搜索、批量操作、多格式导出 |
| **v2.0** | 2026-04 | 原生 Android 客户端、Cloudflare Tunnel 远程访问 |
| **v1.0** | 早期 | 基础录音管理：上传、转写、摘要、导出、目录监控 |

完整更新历史：[CHANGELOG.md](./CHANGELOG.md)

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) | 完整项目结构说明 |
| [CODE_WIKI.md](./CODE_WIKI.md) | 代码维基（API/模型/服务详细说明） |
| [CHANGELOG.md](./CHANGELOG.md) | 变更日志 |
| [docs/project-analysis.md](./docs/project-analysis.md) | 项目现状分析 |
| [docs/TECH_STACK.md](./docs/TECH_STACK.md) | 技术栈详细说明 |
| [docs/product/roadmap.md](./docs/product/roadmap.md) | 产品路线图 |
| [docs/troubleshooting.md](./docs/troubleshooting.md) | 故障排查指南 |
| [docs/decisions/](./docs/decisions/) | 架构决策记录 (ADR) |
| [docs/android-remote-access.md](./docs/android-remote-access.md) | Android 远程访问配置 |
| [backend/PROJECT_STRUCTURE.md](./backend/PROJECT_STRUCTURE.md) | 后端详细结构 |
| [frontend/PROJECT_STRUCTURE.md](./frontend/PROJECT_STRUCTURE.md) | 前端详细结构 |
| [android/README.md](./android/README.md) | Android 端快速开始 |

---

## 许可证

MIT License
