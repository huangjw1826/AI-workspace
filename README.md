# AI Recorder

> 面向 Windows 的本地录音整理工作台 — 离线转写 + LLM 智能摘要 + Android 远程访问

**当前版本：v3.2.0** | [变更日志](./CHANGELOG.md) | [项目结构](./PROJECT_STRUCTURE.md) | [代码维基](./CODE_WIKI.md)

---

## 目录

- [项目简介](#项目简介)
- [架构概览](#架构概览)
- [核心特性](#核心特性)
- [功能矩阵](#功能矩阵)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [详细安装指南](#详细安装指南)
- [使用流程](#使用流程)
- [页面功能说明](#页面功能说明)
- [配置参考](#配置参考)
- [完整 API 接口](#完整-api-接口)
- [数据库设计](#数据库设计)
- [任务生命周期](#任务生命周期)
- [摘要模板详解](#摘要模板详解)
- [导出格式说明](#导出格式说明)
- [目录监控机制](#目录监控机制)
- [Android 客户端](#android-客户端)
- [远程访问配置](#远程访问配置)
- [数据隐私与安全](#数据隐私与安全)
- [性能参考](#性能参考)
- [开发指南](#开发指南)
- [故障排查](#故障排查)
- [FAQ](#faq)
- [项目结构](#项目结构)
- [版本历史](#版本历史)
- [相关文档](#相关文档)
- [许可证](#许可证)

---

## 项目简介

AI Recorder 是一款运行在 Windows 本机上的录音整理工具。它深度整合了**本地语音转写**、**大模型智能摘要**、**多格式导出**和**目录自动监控**四大能力，将所有数据（音频、转写、摘要）保存在本地，同时通过原生 Android 客户端提供安全的远程访问。

**核心设计理念：**

> 🏠 **数据主权** — 录音不离开本机，转写完全离线，只有摘要可选择性调用云端 LLM
> 🔒 **隐私优先** — 所有数据存储在本地 SQLite，无需注册账号、不依赖第三方云存储
> 🎯 **任务可靠** — 转写/摘要任务可取消、可恢复、可复用，应用重启后自动恢复中断任务
> 📱 **多端协同** — PC 端负责 AI 处理，Android 端提供移动端远程访问

### 适用场景

| 场景 | 典型用例 |
|------|---------|
| 🏢 **工作会议** | 录制会议 → 自动转写 → AI 生成会议纪要 → 导出 DOCX 归档 |
| 🎓 **课堂讲座** | 录制课程 → 转写 → 规整口语化内容 → 导出为笔记 |
| 🎙️ **访谈调研** | 录制访谈 → 转写 → 提取待办事项和关键结论 |
| 📝 **个人备忘** | 语音记录想法 → 转写 → AI 结构化整理 |
| 👨‍⚕️ **专业场景** | 问诊/咨询录音 → 本地转写（隐私保护）→ 管理层简报 |

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PC 端 (Windows)                               │
│                                                                         │
│  ┌──────────────────┐        HTTP REST + SSE          ┌───────────────┐│
│  │   Frontend       │ ◄─────────────────────────────► │   Backend     ││
│  │                  │                                 │               ││
│  │  React 19 + TS   │   /api/recordings   (CRUD)      │  FastAPI      ││
│  │  Vite 构建       │   /api/transcribe   (转写)      │  Uvicorn      ││
│  │  Zustand 状态    │   /api/summary      (摘要)      │  SQLModel     ││
│  │  SSE 实时推送    │   /api/watch        (监控)      │  FunASR       ││
│  │                  │   /api/settings     (设置)      │  FFmpeg       ││
│  └──────────────────┘   /api/tokens       (认证)      │  OpenAI SDK   ││
│                          /api/events       (SSE)      └───────┬───────┘│
│                                                               │        │
│                                               ┌───────────────┴──────┐ │
│                                               │  Cloudflare Tunnel   │ │
│                                               │  (可选, 远程访问)     │ │
│                                               └───────────┬──────────┘ │
└───────────────────────────────────────────────────────────┼────────────┘
                                                            │
                                          ┌─────────────────┴────────────┐
                                          │     Android 客户端            │
                                          │                              │
                                          │  Kotlin + Jetpack Compose     │
                                          │  Retrofit + OkHttp            │
                                          │  Hilt DI + DataStore          │
                                          │  Material 3 Design            │
                                          └──────────────────────────────┘
```

### 数据流

```
录音文件 (.mp3/.wav/.m4a/...)
    │
    ├──→ 上传 / 目录监控发现
    │         │
    │         ▼
    │   入库 (recording 表, SHA-256 去重)
    │         │
    │         ▼
    │   音频归一化 (FFmpeg: → 16kHz 单声道 WAV)
    │         │
    │         ▼
    │   FunASR 离线转写 (CPU, 生成带时间戳的片段)
    │         │
    │         ▼
    │   转写校对 (人工编辑, 存入 DB)
    │         │
    │         ├──→ 导出转写 (MD / TXT / JSON / SRT / DOCX)
    │         │
    │         ▼
    │   LLM 摘要生成 (DeepSeek / 通义千问 / MiMo)
    │         │
    │         ▼
    │   导出摘要 (MD / TXT / DOCX)
```

---

## 核心特性

| # | 特性 | 技术实现 | 说明 |
|---|------|---------|------|
| 🎙️ | **本地转写** | FunASR (paraformer-zh 模型) | CPU 离线运行，无需 GPU，无需网络，数据不泄露 |
| 🤖 | **智能摘要** | DeepSeek / 通义千问 / 小米 MiMo | OpenAI 兼容接口，6 种专业模板可选 |
| 📱 | **远程访问** | Cloudflare Tunnel + API Token | 原生 Android 客户端，端到端加密隧道 |
| 🎧 | **音频播放** | HTML5 Audio + Range 请求 | 点击时间戳跳转播放，当前片段高亮 |
| ✏️ | **转写校对** | 内联编辑 + 实时保存 | 可视化编辑转写片段，自动用于后续摘要和导出 |
| 🔍 | **智能搜索** | SQLite LIKE + 后端全文匹配 | 搜索文件名、标签、转写文本、摘要内容，命中高亮 |
| 📤 | **多格式导出** | python-docx + 自定义渲染 | 转写 5 格式 + 摘要 3 格式，文件名自动规范 |
| 👁️ | **目录监控** | 定时轮询 + 文件稳定性检测 | 自动发现新音频入库，SHA-256 去重 |
| ⚡ | **实时推送** | Server-Sent Events (SSE) | 任务进度、系统状态实时推送，前端零轮询 |
| 🎯 | **任务可靠** | 状态机 + 条件 UPDATE | 原子认领、可取消、可恢复，重启自动标记中断 |
| 🔐 | **安全认证** | API Token + 本地免认证 | HMAC 时序安全比较，设备级 Token 管理 |

---

## 功能矩阵

### PC 端 (Web 界面)

| 页面 | 路由 | 核心功能 |
|------|------|---------|
| **录音库** | `/` | 录音列表展示 (表格/卡片)、全文搜索、标签筛选 (状态/来源/标签)、多字段排序、多选批量操作、录音详情侧边面板 |
| **目录监控** | `/watch` | 监控目录配置、手动触发扫描、监控事件日志 (导入/去重/跳过/错误)、扫描状态实时更新 |
| **设置** | `/settings` | LLM 服务商配置+连通性测试、存储路径配置+数据迁移 (含合并模式)、API Token 管理 (创建/启禁用/删除)、访问日志查看 |
| **系统状态** | `/health` | CPU/内存/磁盘使用率、Python/FFmpeg/FunASR 版本状态、Cloudflare Tunnel 连接状态、最近错误日志 |

### 录音库操作详情

| 操作 | 说明 |
|------|------|
| **上传** | 支持 wav/mp3/m4a/flac/aac/ogg，单文件 ≤500MB，自动 SHA-256 去重 |
| **转写** | 单文件/批量发起 FunASR 离线转写，并发数可控 (默认 1) |
| **摘要** | 选择 6 种模板之一生成摘要，同一条录音可保留多份摘要 |
| **播放** | HTML5 原生播放器，Range 请求支持任意位置拖动，转写片段点击 → 跳转播放 |
| **校对** | 内联编辑每个转写片段的文本，保存后立即更新导出和摘要数据源 |
| **标签** | 自由标签系统，逗号分隔，支持标签筛选和批量添加 |
| **导出** | 转写: MD/TXT/JSON/SRT/DOCX；摘要: MD/TXT/DOCX |
| **删除** | 单条/批量删除，级联清理关联的转写、摘要、任务和生成文件 |

### Android 端

| 页面 | 功能 |
|------|------|
| **录音库** | 查看 PC 端录音列表、全文搜索、标签筛选、上传音频文件到 PC |
| **详情 - 播放** | 流式音频播放 (ExoPlayer)、转写片段列表、时间戳联动跳转 |
| **详情 - 摘要** | 查看已生成的摘要、摘要详情 Markdown 渲染 |
| **详情 - 任务** | 查看录音关联的任务状态和进度 |
| **详情 - 信息** | 文件属性 (大小/时长/格式/来源/哈希) |
| **健康面板** | CPU/内存/磁盘使用率、Python/FFmpeg/FunASR 状态、隧道连接状态 |
| **设置** | 服务器地址 (支持域名/IP:端口)、API Token 配置、连接测试 |

---

## 技术栈

### 后端 (Python ~5,000 行)

| 技术 | 版本要求 | 用途 |
|------|---------|------|
| Python | 3.10 ~ 3.12 | 编程语言 (3.13+ 不兼容 torch) |
| FastAPI | latest | REST API 框架，自动 OpenAPI 文档生成 |
| Uvicorn | latest | ASGI 服务器，支持异步并发 |
| SQLModel | latest | ORM (SQLAlchemy 2.0 + Pydantic v2) |
| SQLite | 3.x (内置) | 本地数据库，零配置，无需额外安装 |
| FunASR | latest | 阿里达摩院语音识别框架 |
| Modelscope | latest | FunASR 模型下载和管理 |
| PyTorch | CPU 版本 | 深度学习推理框架 |
| FFmpeg | 任意版本 | 音频格式转换/归一化 (系统级依赖) |
| pydub | latest | 音频处理 Python 封装 |
| OpenAI Python SDK | latest | LLM API 调用 (兼容 DeepSeek/通义千问/MiMo) |
| python-docx | latest | DOCX 格式导出 |
| pydantic-settings | latest | 环境变量 + .env 文件配置管理 |
| python-multipart | latest | 文件上传 Multipart 解析 |
| httpx | latest | 异步 HTTP 客户端 (健康检查自探测) |

### 前端 (TypeScript ~2,200 行)

| 技术 | 版本 | 用途 |
|------|------|------|
| React | ^19.2.5 | UI 框架，函数组件 + Hooks |
| TypeScript | ^6.0.3 | 类型安全，20+ 接口定义 |
| Vite | ^8.0.10 | 构建工具，HMR 热更新 |
| Zustand | latest | 轻量状态管理 (2 stores，< 100 行) |
| Lucide React | ^0.487.0 | 图标库，Tree-shakeable |
| React Router | v6 | SPA 客户端路由 |
| 纯 CSS | — | 无 UI 框架依赖，CSS 变量主题 |

### Android (Kotlin ~6,500 行)

| 技术 | 用途 |
|------|------|
| Kotlin | 编程语言 |
| Jetpack Compose | 声明式 UI 框架 |
| Material Design 3 | Google 最新设计系统，动态主题 |
| Retrofit 2 + OkHttp | 类型安全 HTTP 客户端 + 拦截器 |
| Hilt | 依赖注入 (基于 Dagger) |
| DataStore Preferences | 键值存储 (替代 SharedPreferences) |
| Kotlin Coroutines + Flow | 异步编程 + 响应式数据流 |
| Gradle Kotlin DSL | 构建系统 |

---

## 快速开始

### 环境要求

| 组件 | 最低版本 | 推荐版本 | 检查命令 |
|------|---------|---------|---------|
| Windows | 10 / 11 | 11 | `winver` |
| Python | 3.10 | 3.11 / 3.12 | `python --version` |
| Node.js | 20 LTS | 22 LTS | `node --version` |
| FFmpeg | 任意版本 | 6.0+ | `ffmpeg -version` |
| Git | 2.30+ | latest | `git --version` |

> ⚠️ **Python 版本限制**：PyTorch CPU 版本目前不支持 Python 3.13+，请使用 3.10 ~ 3.12。

### 一键安装和启动

```powershell
# 1. 克隆项目
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace

# 2. 安装所有依赖并构建前端（首次运行，约 5-10 分钟）
.\setup.ps1

# 3. 启动服务
.\start.ps1

# 4. 浏览器自动打开 → http://127.0.0.1:8000
```

### 管理命令

```powershell
.\setup.ps1           # 安装 Python venv + pip 依赖 + Node 依赖 + 构建前端
.\start.ps1          # 一键启动：后端 (uvicorn) + 浏览器自动打开
.\stop.ps1           # 停止所有服务
.\check.ps1           # 全面环境检查：依赖/端口/配置/服务状态
```

### 启动过程详解

`start.ps1` 执行以下步骤：

```
[1/3] 启动后端
      ├── 查找 Python venv (backend\.venv → 根 .venv)
      ├── 以隐藏窗口启动 uvicorn (无终端窗口干扰)
      └── 监听 http://127.0.0.1:8000
[2/3] 启动 Cloudflare Tunnel (如已配置 cloudflared)
      └── 监听 %USERPROFILE%\.cloudflared\ 配置
[3/3] 等待后端就绪 (轮询 /health 最多 20s)
      └── 就绪后打开浏览器 → http://127.0.0.1:8000
```

---

## 详细安装指南

### 1. 安装 Python

```powershell
# 推荐方式：Microsoft Store
# 搜索 "Python 3.12" → 安装

# 或从官网下载
# https://www.python.org/downloads/
# 安装时务必勾选 "Add Python to PATH"

# 验证安装
python --version   # 应输出 Python 3.12.x
pip --version      # 应输出 pip 24.x
```

### 2. 安装 Node.js

```powershell
# 推荐使用 nvm-windows 管理版本
# https://github.com/coreybutler/nvm-windows

nvm install 22
nvm use 22

# 或直接从官网下载 LTS
# https://nodejs.org/

# 验证安装
node --version     # 应输出 v22.x.x
npm --version      # 应输出 10.x.x
```

### 3. 安装 FFmpeg

```powershell
# 方式一：winget (推荐)
winget install ffmpeg

# 方式二：chocolatey
choco install ffmpeg

# 方式三：手动下载
# https://ffmpeg.org/download.html
# 解压后将 bin 目录加入系统 PATH

# 验证安装
ffmpeg -version    # 应输出版权信息和版本号
```

### 4. 运行 setup.ps1

```powershell
.\setup.ps1

# 脚本自动执行：
# ① 在 backend\.venv 创建 Python 虚拟环境
# ② pip install -r backend/requirements.txt (约 2-5 分钟)
# ③ npm install (frontend/) (约 1-2 分钟)
# ④ npm run build (构建生产版本到 frontend/dist/)
# ⑤ 首次启动时 FunASR 会自动下载模型到 models/ 目录 (~1-3 分钟)
```

### 5. 首次启动

```powershell
.\start.ps1

# 首次启动时 FunASR 自动下载以下模型 (约 500MB):
# - paraformer-zh: 语音识别模型 (~200MB)
# - fsmn-vad: 语音活动检测 (~5MB)
# - ct-punc: 标点恢复 (~5MB)
# - fa-zh: 时间戳模型 (~5MB)
# 模型缓存在 models/funasr/，仅首次下载
```

---

## 使用流程

### 典型工作流

```
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│  1. 导入    │ → │  2. 转写    │ → │  3. 校对    │ → │  4. 摘要    │ → │  5. 导出    │
│  音频文件   │    │  FunASR    │    │  编辑片段   │    │  选择模板   │    │  多格式     │
└────────────┘    └────────────┘    └────────────┘    └────────────┘    └────────────┘
```

### 详细步骤

#### 第一步：导入音频

**方式 A — 手动上传：**
1. 打开 `http://127.0.0.1:8000`
2. 点击导航栏「上传」按钮
3. 选择音频文件 (wav/mp3/m4a/flac/aac/ogg, ≤500MB)
4. 上传后自动出现在录音列表

**方式 B — 目录监控 (推荐批量场景)：**
1. 进入「设置」→ 配置「数据总目录」
2. 进入「目录监控」→ 设置监控目录路径
3. 开启监控开关 → 定时扫描 (默认每 10 秒)
4. 新音频自动入库 (去重 + 稳定检测)

#### 第二步：转写

1. 在录音列表点击一条录音 → 右侧弹出详情面板
2. 点击「转写」按钮
3. 等待 FunASR 完成 (可同时做其他操作)
4. 进度通过 SSE 实时推送，导航栏显示任务状态

#### 第三步：校对转写 (可选)

1. 转写完成后，在详情面板的「转写」Tab 查看所有片段
2. 点击任意片段文本即可编辑
3. 编辑后自动保存 (用于后续摘要和导出)

#### 第四步：生成摘要

1. 切换到详情面板的「摘要」Tab
2. 下拉选择摘要模板 (6 种可选)
3. 点击「生成摘要」
4. 等待 LLM 返回结果 (通常 5-30 秒)
5. 同一条录音可多次生成不同模板的摘要

#### 第五步：导出

1. 在转写 Tab → 点击导出按钮 → 选择格式 (MD/TXT/JSON/SRT/DOCX)
2. 在摘要 Tab → 点击摘要卡片的导出按钮 → 选择格式 (MD/TXT/DOCX)
3. 文件自动下载到浏览器默认下载目录

> **设计原则**：目录监控只负责发现并入库新音频，不会自动触发转写或摘要，避免误处理大量文件。

---

## 页面功能说明

### 录音库页面 (LibraryPage)

```
┌──────────────────────────────────────────────────────────────┐
│  [搜索框: 搜索文件名/标签/转写/摘要内容...]  [标签筛选]        │
│                                                              │
│  [状态筛选 ▾] [来源筛选 ▾] [排序 ▾]      [批量转写] [批量删除] │
│                                                              │
│  ┌───────────────────────┬──────────────────────────────────┐│
│  │   录音列表 (左侧)       │   录音详情面板 (右侧, 可关闭)     ││
│  │                       │                                  ││
│  │  □ 会议录音_2026.mp3  │  ┌─────┬─────┬─────┬─────┐      ││
│  │    已摘要 · 45:30     │  │转写 │摘要 │任务 │信息 │      ││
│  │                       │  ├─────┴─────┴─────┴─────┤      ││
│  │  □ 访谈_客户A.mp3     │  │                        │      ││
│  │    已转写 · 23:15     │  │  [音频播放器]          │      ││
│  │                       │  │  00:00 ─●──── 45:30   │      ││
│  │  □ 讲座_AI未来.wav    │  │                        │      ││
│  │    待转写 · 90:00     │  │  [转写片段列表]        │      ││
│  │                       │  │  00:00 大家好...      │      ││
│  │                       │  │  00:15 今天我们来...   │      ││
│  │                       │  │  00:32 关于AI的发展... │      ││
│  └───────────────────────┴──────────────────────────────────┘│
└──────────────────────────────────────────────────────────────┘
```

### 设置页面 (SettingsPage)

| 设置分区 | 配置项 | 说明 |
|---------|--------|------|
| **大模型** | 提供商、API Key、接口地址、模型、Temperature、Top-P、MiMo Thinking | 支持连通性测试 (POST 验证) |
| **存储** | 数据总目录、转写目录、摘要目录 | 支持跨目录数据迁移/合并 |
| **目录监控** | 开关、扫描目录、递归扫描、扫描间隔、稳定检测次数 | 配置后实时生效 |
| **API Token** | 创建、列表、启禁用、删除 | 设备级访问控制 |

### 系统状态页面 (HealthPage)

| 信息区 | 展示内容 |
|--------|---------|
| **系统资源** | CPU 使用率 %、内存使用量/总量、磁盘使用率 % |
| **组件状态** | Python 版本、FFmpeg ✅/❌、FunASR ✅/❌、ASR 模型名称 |
| **LLM 状态** | 提供商、接口地址、模型名称、API Key 配置状态 |
| **隧道状态** | Cloudflare Tunnel 运行状态、连接延迟 |
| **最近错误** | 应用最近 10 条错误日志 (供诊断) |

---

## 配置参考

### 环境变量完整列表

配置文件位于 `backend/.env`，从 `backend/.env.example` 复制并修改：

#### 应用基础配置

```env
# 运行模式: local (本地) / production (生产, 禁用 API 文档)
APP_ENV=local

# 监听地址和端口
APP_HOST=127.0.0.1
APP_PORT=8000
```

#### 数据存储路径

```env
# 所有路径支持相对路径 (相对于 backend/) 或绝对路径
DATA_DIR=../data
MODEL_DIR=../models/funasr
LOG_DIR=../logs
TRANSCRIPT_DIR=../data/transcripts
SUMMARY_DIR=../data/summaries
```

#### ASR 转写配置

```env
# 推理设备: cpu (推荐, 兼容性最好) / cuda (需要 NVIDIA GPU + CUDA)
ASR_DEVICE=cpu

# 模型选择 (一般无需修改)
ASR_MODEL=paraformer-zh          # 语音识别模型
ASR_VAD_MODEL=fsmn-vad           # 语音活动检测
ASR_PUNC_MODEL=ct-punc           # 标点恢复
ASR_TIMESTAMP_MODEL=fa-zh        # 时间戳模型

# 说话人分离 (CPU 环境性能影响大, 默认关闭)
ASR_ENABLE_DIARIZATION=false

# 最大并发转写数 (CPU 建议 1, GPU 可适当提高)
ASR_MAX_CONCURRENCY=1
```

#### LLM 大模型配置

```env
# 提供商选择: deepseek / tongyi / qwen / mimo
LLM_PROVIDER=deepseek
LLM_API_KEY=sk-your-api-key-here

# 可选：自定义接口地址和模型 (留空使用提供商默认值)
LLM_BASE_URL=
LLM_MODEL=

# 生成参数
LLM_MAX_COMPLETION_TOKENS=2048
LLM_TEMPERATURE=0.2
LLM_TOP_P=

# 超时和重试
LLM_TIMEOUT_SECONDS=60
LLM_RETRY_ATTEMPTS=3
```

#### 远程访问配置

```env
# API Token (远程访问必需, 本地回环免认证)
API_TOKEN=your-secure-token-here

# 远程访问开关
REMOTE_ACCESS_ENABLED=false
REMOTE_ACCESS_HOSTNAME=

# 跨域配置 (生产环境应限制为实际域名)
CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

### LLM 提供商详细配置

| Provider | 默认 Base URL | 默认 Model | 需要 API Key |
|----------|--------------|-----------|-------------|
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` | [DeepSeek API Key](https://platform.deepseek.com/api_keys) |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | [阿里云百炼 API Key](https://bailian.console.aliyun.com/) |
| `mimo` | `https://token-plan-cn.xiaomimimo.com/v1` | `mimo-v2.5` | 小米 MiMo API Key |

**在 Web 界面配置更简单：** 进入「设置」→「大模型」，选择提供商、填入 API Key、点击「测试连通性」。

---

## 完整 API 接口

> 所有接口前缀: `http://127.0.0.1:8000`

### 录音管理

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|----------|------|
| `GET` | `/api/recordings` | `?query=&tag=` → `SearchResult` | 录音列表，支持搜索和标签筛选 |
| `POST` | `/api/recordings` | `multipart/form-data {file}` → `Recording` | 上传音频 (≤500MB) |
| `GET` | `/api/recordings/{id}` | → `RecordingDetail` | 录音详情 (含转写、摘要、任务) |
| `DELETE` | `/api/recordings/{id}` | → `{message}` | 删除录音 (级联删除关联数据) |
| `POST` | `/api/recordings/batch-delete` | `{recording_ids: []}` → `{deleted, missing}` | 批量删除 |
| `PATCH` | `/api/recordings/{id}/tags` | `{tags: ["标签1","标签2"]}` → `Recording` | 更新标签 |
| `PATCH` | `/api/recordings/{id}/segments/{sid}` | `{text: "修正文本"}` → `Segment` | 编辑转写片段 |
| `GET` | `/api/recordings/{id}/audio` | → `audio/wav` 流 | 音频播放 (支持 Range 请求) |
| `GET` | `/api/recordings/{id}/exports/transcript` | `?format=md\|txt\|json\|srt\|docx` → 文件 | 导出转写 |

### 转写接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/transcribe/{id}` | 发起单个转写任务 → `Task` |
| `POST` | `/api/transcribe/batch` | 批量转写 `{recording_ids: []}` → `Task[]` |

### 摘要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/summary/templates` | 获取 6 种摘要模板列表 → `SummaryTemplate[]` |
| `POST` | `/api/summary/{id}` | 发起摘要任务 `?mode=meeting_minutes` → `Task` |
| `POST` | `/api/summary/batch` | 批量摘要 `?mode=meeting_minutes` → `Task[]` |
| `GET` | `/api/summaries/{id}/export` | 导出摘要 `?format=md\|txt\|docx` → 文件 |
| `DELETE` | `/api/summaries/{id}` | 删除单条摘要 |

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/tasks/{id}` | 查询任务状态和进度 |
| `POST` | `/api/tasks/{id}/cancel` | 取消运行中的任务 |

### 设置接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` / `PUT` | `/api/settings/llm` | LLM 设置 |
| `POST` | `/api/settings/llm/test` | LLM 连通性测试 |
| `GET` / `PUT` | `/api/settings/watch` | 目录监控设置 |
| `GET` / `PUT` | `/api/settings/storage` | 存储路径设置 |
| `GET` | `/api/settings/storage/migration-preview` | 预览数据迁移差异 |
| `POST` | `/api/settings/storage/migrate` | 执行数据迁移/合并 |

### 目录监控

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/watch/events` | 监控事件列表 |
| `POST` | `/api/watch/scan` | 手动触发扫描 |

### API Token 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/tokens` | Token 列表 (已掩码) |
| `POST` | `/api/tokens` | 创建 Token `{name, device_info?}` → `ApiToken` (返回完整 Token 仅一次) |
| `PATCH` | `/api/tokens/{id}` | 更新 Token (名称/启禁用) |
| `DELETE` | `/api/tokens/{id}` | 撤销/删除 Token |

### 系统

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/health` | 健康检查 (系统状态/组件版本/LLM 配置/隧道状态) |
| `GET` | `/api/events` | SSE 事件流 (任务进度实时推送) |
| `POST` | `/api/pick-folder` | Windows 原生文件夹选择对话框 |

### SSE 事件类型

| Event Type | 触发时机 | 数据 |
|-----------|---------|------|
| `task.started` | 任务开始执行 | `{task_id, recording_id, message}` |
| `task.progress` | 任务进度更新 | `{task_id, recording_id, progress: 0-100, message}` |
| `task.completed` | 任务成功完成 | `{task_id, recording_id, result_path, message}` |
| `task.failed` | 任务执行失败 | `{task_id, recording_id, error_message}` |

---

## 数据库设计

### ER 关系

```
recording (录音)               task (任务)
┌──────────────────┐          ┌──────────────────┐
│ id (PK)          │←────────│ recording_id (FK)│
│ filename         │   1:N    │ task_type        │
│ original_path    │          │ status           │
│ normalized_path  │          │ progress         │
│ duration_seconds │          │ created_at       │
│ file_size_bytes  │          └──────────────────┘
│ format           │
│ content_hash     │          transcript_segment (转写片段)
│ source_type      │          ┌──────────────────┐
│ tags             │←────────│ recording_id (FK)│
│ status           │   1:N    │ start_time       │
│ created_at       │          │ end_time         │
└──────────────────┘          │ text             │
         │                    │ sequence         │
         │ 1:N                └──────────────────┘
         │
         │                    summary (摘要)
         │          ┌──────────────────┐
         ├─────────│ recording_id (FK)│
         │   1:N    │ mode             │
         │          │ content          │
         │          │ created_at       │
         │          └──────────────────┘
         │
         │ 1:N      watch_event (监控事件)
         │          ┌──────────────────┐
         └─────────│ recording_id (FK)│
                    │ file_path        │
                    │ status           │
                    │ content_hash     │
                    └──────────────────┘

api_token (API Token)          access_log (访问日志)
┌──────────────────┐          ┌──────────────────┐
│ id (PK)          │←────────│ token_id (FK)    │
│ token (hashed)   │   1:N    │ method           │
│ name             │          │ path             │
│ is_active        │          │ status_code      │
│ last_used_at     │          │ ip_address       │
└──────────────────┘          │ created_at       │
                              └──────────────────┘
```

### 表结构详解

#### recording — 录音记录 (核心实体)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `filename` | TEXT | 原始文件名 |
| `original_path` | TEXT | 原始文件绝对路径 |
| `normalized_path` | TEXT | 归一化后 16kHz WAV 路径 |
| `duration_seconds` | REAL | 音频时长 (秒) |
| `file_size_bytes` | INTEGER | 文件大小 (字节) |
| `source_mtime` | REAL | 源文件修改时间戳 |
| `format` | TEXT | 文件格式 (wav/mp3/m4a/flac/aac/ogg) |
| `content_hash` | TEXT | SHA-256 内容哈希 (去重) |
| `source_type` | TEXT | 来源: `upload` (手动上传) / `watch` (目录监控) |
| `tags` | TEXT | 逗号分隔标签 |
| `status` | TEXT | 处理状态 (见下方状态机) |
| `error_message` | TEXT | 处理错误信息 |
| `created_at` | DATETIME | 创建时间 (UTC) |
| `updated_at` | DATETIME | 最后更新时间 |

#### task — 任务记录

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `recording_id` | UUID FK | 关联录音 |
| `task_type` | TEXT | `transcription` 或 `summary:{mode}` |
| `status` | TEXT | `queued` / `running` / `completed` / `error` / `cancelled` |
| `progress` | INTEGER | 0-100 百分比 |
| `error_message` | TEXT | 错误详情 |
| `result_path` | TEXT | 结果文件路径 |
| `started_at` | DATETIME | 开始执行时间 |
| `completed_at` | DATETIME | 完成时间 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

#### transcript_segment — 转写片段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `recording_id` | UUID FK | 关联录音 |
| `start_time` | REAL | 开始时间 (秒) |
| `end_time` | REAL | 结束时间 (秒) |
| `speaker` | TEXT | 说话人标识 (默认 "SPK0") |
| `text` | TEXT | 转写文本内容 |
| `sequence` | INTEGER | 排序序号 |

#### summary — 摘要记录

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `recording_id` | UUID FK | 关联录音 |
| `mode` | TEXT | 模板 ID (如 `meeting_minutes`) |
| `content` | TEXT | Markdown 格式摘要内容 |
| `created_at` | DATETIME | 生成时间 |

---

## 任务生命周期

### 转写任务状态机

```
                  POST /api/transcribe/{id}
                        │
                        ▼
  ┌─────────────────────────────────────────────────────────┐
  │                      queued                             │
  │              (等待执行, 可被新任务复用)                    │
  └───────────────────────┬─────────────────────────────────┘
                          │ 条件 UPDATE: status='queued' → 'running'
                          │ (原子认领, 防止多终端抢占)
                          ▼
  ┌─────────────────────────────────────────────────────────┐
  │                     running                             │
  │  ┌──────────┐    ┌──────────┐    ┌──────────────────┐  │
  │  │normalizing│ → │transcribing│ → │ 保存片段到 DB    │  │
  │  │ (10%)     │    │ (35-70%)   │    │                  │  │
  │  └──────────┘    └──────────┘    └──────────────────┘  │
  └────────┬───────────────┬────────────────┬──────────────┘
           │               │                │
     用户取消            执行异常         全部完成
           ▼               ▼                ▼
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │  cancelled   │  │    error     │  │  completed   │
  │ (恢复录音状态)│  │ (保留错误信息)│  │  (100%)      │
  └──────────────┘  └──────────────┘  └──────────────┘
```

### 录音状态与任务的联动

```
uploaded → queued → normalizing → transcribing → transcribed → completed
   │         │          │             │               │            │
   │         │          │             │               │            │
   └─────────┴──────────┴─────────────┴───────────────┴────────────┘
                                    │
                               error (任何阶段出错)
```

| 状态 | 含义 | 可执行操作 |
|------|------|-----------|
| `uploaded` | 已入库，待处理 | 转写、编辑标签、删除 |
| `queued` | 转写任务已排队 | 取消任务 |
| `normalizing` | 正在归一化音频 | 取消任务 |
| `transcribing` | FunASR 转写中 | 取消任务 |
| `transcribed` | 转写完成，未摘要 | 校对转写、生成摘要、导出转写、删除 |
| `completed` | 已生成摘要 | 查看摘要、导出、生成更多摘要 |
| `error` | 处理失败 | 查看错误信息、重新转写 |

### 任务恢复机制

应用启动时自动执行 `recover_interrupted_tasks()`：
- 扫描状态为 `running` 的任务 → 标记为 `error` (进程已消失)
- 扫描状态为 `queued` 的任务 → 维持排队，等待下次触发
- 录音状态与任务状态联动恢复

---

## 摘要模板详解

| 模板 ID | 名称 | 输出格式 | 适用场景 |
|---------|------|---------|---------|
| `meeting_minutes` | 会议纪要 | 议题 → 讨论要点 → 结论 → 行动计划 (含责任人) | 正式工作会议 |
| `structured_summary` | 结构化摘要 | 背景 → 核心主题 → 关键发现 → 后续事项 | 通用场景 |
| `action_items` | 待办事项 | 序号列表：事项、负责人、截止时间、优先级 | 任务跟进 |
| `decisions_risks` | 决策与风险 | 已做决策 → 风险评估 → 阻塞点 → 待决问题 | 决策记录 |
| `executive_brief` | 管理层简报 | 一句话概览 → 3-5 条关键结论 → 建议 | 向上汇报 |
| `polished_transcript` | 转写内容规整 | 修正口语冗余 (嗯/啊/重复)，保持原意、优化表达 | 发布/归档 |

---

## 导出格式说明

### 转写导出

| 格式 | 文件扩展名 | 内容说明 |
|------|-----------|---------|
| **Markdown** | `.md` | 带时间戳的格式化文本，片段间有空行 |
| **纯文本** | `.txt` | 纯文本，按说话人分行，带时间戳 |
| **JSON** | `.json` | 结构化数据，含所有元数据 (时间/说话人/文本) |
| **SRT 字幕** | `.srt` | 标准 SubRip 字幕格式，可用于视频播放器 |
| **DOCX** | `.docx` | Word 文档，含样式表格 (时间/说话人/文本) |

### 摘要导出

| 格式 | 文件扩展名 | 内容说明 |
|------|-----------|---------|
| **Markdown** | `.md` | LLM 原始输出 (Markdown 格式) |
| **纯文本** | `.txt` | 去除 Markdown 标记的纯文本 |
| **DOCX** | `.docx` | Word 文档，保留标题/列表/粗体等格式 |

### 文件名规则

```
{录音文件名}_转写_{时间戳}.{扩展名}
{录音文件名}_摘要_{模板名}_{时间戳}.{扩展名}

示例:
  会议录音_20260528_转写_20260528-143052.md
  会议录音_20260528_摘要_会议纪要_20260528-143530.docx
```

---

## 目录监控机制

### 工作流程

```
定时器触发 (默认 10s)
    │
    ▼
扫描监控目录 (支持递归)
    │
    ▼
过滤音频文件 (wav/mp3/m4a/flac/aac/ogg)
    │
    ▼
文件稳定性检测 (大小和 mtime 连续 N 次扫描不变)
    │
    ▼
SHA-256 哈希计算
    │
    ├── 已存在 → 跳过 (duplicate_skipped)
    │
    └── 新文件 → 自动入库 → 创建 WatchEvent (imported)
```

### 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `WATCH_ENABLED` | `false` | 是否启用 |
| `WATCH_DIR` | (空) | 监控目录绝对路径 |
| `WATCH_RECURSIVE` | `true` | 是否递归扫描子目录 |
| `WATCH_INTERVAL_SECONDS` | `10` | 扫描间隔 |
| `WATCH_STABLE_COUNT` | `2` | 连续稳定次数 (防复制中断文件) |

### 监控事件类型

| 状态 | 含义 |
|------|------|
| `imported` | 新文件已入库 |
| `duplicate_skipped` | 内容哈希重复，已跳过 |
| `skipped` | 不支持的文件格式 |
| `error` | 处理出错 (如文件读取失败) |

---

## Android 客户端

### 环境要求

| 组件 | 要求 |
|------|------|
| JDK | 17+ |
| Android SDK | API 29+ (Android 10+) |
| Android Studio | Hedgehog (2023.1.1) 或更新 |
| Gradle | 8.x (Wrapper 自动下载) |

### 快速开始

1. 用 Android Studio 打开 `android/` 目录
2. 等待 Gradle Sync 完成 (首次约 3-5 分钟)
3. 在 PC 端「设置」→「API Token」创建一个 Token
4. 打开 App → 设置 → 填入 PC 端地址和 Token
5. 点击「测试连接」验证连通性
6. 连接设备或启动模拟器 → Run

### 功能覆盖

| PC 端功能 | Android 端 | 说明 |
|----------|-----------|------|
| 录音列表/搜索/筛选 | ✅ | 与 PC 端数据同步 |
| 音频播放 | ✅ | ExoPlayer 流式播放，支持时间戳跳转 |
| 上传音频 | ✅ | 从手机上传到 PC 端 |
| 查看转写/摘要 | ✅ | 只读查看，含 Markdown 渲染 |
| 健康监控 | ✅ | 系统状态/组件版本/隧道状态 |
| 发起转写 | ❌ | 需在 PC 端操作 |
| 发起摘要 | ❌ | 需在 PC 端操作 |
| 转写校对 | ❌ | 需在 PC 端操作 |
| 导出 | ❌ | 需在 PC 端操作 |
| 标签编辑 | ❌ | 需在 PC 端操作 |

---

## 远程访问配置

### 架构

```
┌──────────────┐         ┌──────────────────┐         ┌──────────────┐
│   Android    │  HTTPS  │  Cloudflare       │  local  │   PC 端       │
│   手机/平板   │ ◄─────► │  Tunnel           │ ◄─────► │  localhost    │
│              │         │  (cloudflared)    │         │  :8000        │
└──────────────┘         └──────────────────┘         └──────────────┘
       WAN                      Cloudflare Edge               LAN
```

### 配置步骤

#### 1. 安装 cloudflared

```powershell
# 下载 cloudflared (Windows amd64)
# https://github.com/cloudflare/cloudflared/releases
# 放到 C:\Program Files (x86)\cloudflared\cloudflared.exe
# 或使用 winget:
winget install Cloudflare.cloudflared
```

#### 2. 登录和创建隧道

```powershell
cloudflared tunnel login
# 浏览器会打开 Cloudflare 授权页面

cloudflared tunnel create ai-recorder
# 创建隧道，获得 tunnel ID 和 credentials JSON
```

#### 3. 配置 DNS

在 Cloudflare Dashboard 中添加 CNAME 记录：
```
your-domain.example.com → {tunnel-id}.cfargotunnel.com
```

#### 4. 配置后端

```env
# backend/.env
API_TOKEN=your-strong-random-token
REMOTE_ACCESS_ENABLED=true
REMOTE_ACCESS_HOSTNAME=your-domain.example.com
```

#### 5. 启动

```powershell
.\start.ps1
# 自动启动 cloudflared tunnel run
```

#### 6. Android 端连接

在 App 设置中填入：
- 服务器地址：`https://your-domain.example.com`
- API Token：`your-strong-random-token`

### 安全措施

| 层级 | 措施 |
|------|------|
| **传输层** | Cloudflare Tunnel 端到端 TLS 加密 |
| **认证层** | API Token (HMAC compare_digest 防时序攻击) |
| **授权层** | 本地回环 (127.0.0.1/::1) 免 Token，远程强制验证 |
| **设备层** | 支持多 Token，每设备独立，可随时撤销 |
| **审计层** | access_log 表记录所有远程请求 |

---

## 数据隐私与安全

### 数据存储位置

以下目录和文件**完全存储在本机，不提交 Git**：

| 路径 | 内容 | 敏感性 |
|------|------|--------|
| `backend/.env` | API Key、Token 等密钥 | 🔴 高 — 不要分享 |
| `data/app.db` | SQLite 数据库 (所有业务数据) | 🟡 中 — 含转写/摘要内容 |
| `data/recordings/` | 原始音频文件 | 🟡 中 — 你的录音 |
| `data/normalized/` | 归一化音频 (16kHz WAV) | 🟡 中 — 处理中间文件 |
| `data/transcripts/` | 转写结果 JSON 备份 | 🟡 中 — 转写内容 |
| `data/summaries/` | 摘要 Markdown 备份 | 🟡 中 — 摘要内容 |
| `models/` | FunASR 模型缓存 | 🟢 低 — 公开模型 |
| `logs/` | 应用运行日志 | 🟢 低 — 无敏感数据 |
| `backend/.venv/` | Python 虚拟环境 | 🟢 低 — 可重建 |
| `frontend/node_modules/` | Node 依赖 | 🟢 低 — 可重建 |
| `frontend/dist/` | 前端构建产物 | 🟢 低 — 可重建 |

### 网络安全

| 措施 | 说明 |
|------|------|
| **默认绑定 127.0.0.1** | 仅本机可访问，不暴露到局域网 |
| **CORS 白名单** | 默认只允许 Vite 开发服务器 (localhost:5173) |
| **安全响应头** | X-Content-Type-Options, X-Frame-Options, CSP, Referrer-Policy, Permissions-Policy |
| **路径遍历防护** | resolve() + is_relative_to() 双重校验文件路径 |
| **文件类型白名单** | 上传仅允许 6 种音频格式 |
| **文件大小限制** | 单文件 ≤500MB |
| **SHA-256 去重** | 防止重复上传相同文件 |
| **API 文档保护** | 生产环境 (APP_ENV=production) 自动禁用 /docs 和 /redoc |

### 建议备份

定期备份以下两个位置即可完整恢复：

```powershell
# 最小备份
data/              # 数据库 + 所有音频/转写/摘要
backend/.env       # 配置和 API Key
```

---

## 性能参考

### 转写速度 (CPU)

测试环境：Intel Core i7-12700H, Windows 11, FunASR paraformer-zh

| 音频时长 | 转写耗时 | 换算比例 |
|---------|---------|---------|
| 5 分钟 | 约 1-3 分钟 | ~0.2-0.6x 实时 |
| 15 分钟 | 约 3-8 分钟 | ~0.2-0.5x 实时 |
| 30 分钟 | 约 5-15 分钟 | ~0.17-0.5x 实时 |
| 60 分钟 | 约 10-30 分钟 | ~0.17-0.5x 实时 |
| 120 分钟 | 约 20-60 分钟 | ~0.17-0.5x 实时 |

> 影响因素：CPU 核心数、主频、当前系统负载、音频采样率

### 磁盘占用

| 项目 | 典型大小 |
|------|---------|
| FunASR 模型 (首次下载) | ~500 MB |
| Python venv + 依赖 | ~2-3 GB (含 PyTorch) |
| Node.js 依赖 | ~300 MB |
| data/ 数据库 | ~10-50 MB (取决于录音数量) |
| 归一化音频 | ~10 MB/分钟 (16kHz mono WAV) |
| 原始音频 | ~1-15 MB/分钟 (取决于原始格式和码率) |

---

## 开发指南

### 项目结构

```
AI-workspace/
├── backend/                  # FastAPI 后端 (Python)
│   ├── app/
│   │   ├── api/             # 12 个路由模块
│   │   ├── services/        # 10 个业务服务
│   │   ├── models/          # 7 个 SQLModel 数据模型
│   │   ├── pipeline/        # 转写/摘要工作流编排
│   │   ├── middleware/      # 异常处理、安全头
│   │   ├── db/              # 数据库连接和迁移
│   │   ├── exceptions/      # 自定义异常类
│   │   ├── config.py        # pydantic-settings 配置
│   │   └── main.py          # FastAPI 应用入口
│   ├── tests/               # 单元测试 (23 用例)
│   └── requirements.txt
├── frontend/                 # React 前端 (TypeScript)
│   └── src/
│       ├── pages/           # 4 个页面组件
│       ├── components/      # 12 个可复用组件 (5 子目录)
│       ├── hooks/           # 3 个自定义 Hooks
│       ├── lib/             # API 客户端、类型定义、SSE、工具
│       └── stores/          # 2 个 Zustand stores
├── android/                  # Android 原生客户端 (Kotlin)
├── docs/                     # 项目文档
├── scripts/                  # 辅助脚本
├── start.ps1                # 一键启动服务
└── stop.ps1                 # 停止服务
```

### 本地开发

```powershell
# 后端开发模式 (带热重载)
cd backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000

# 前端开发模式 (带 HMR 热更新, 端口 5173)
cd frontend
npm run dev

# 前端会自动代理 API 请求到 localhost:8000
```

### 运行测试

```powershell
cd backend
.\.venv\Scripts\python.exe -m pytest tests/ -v

# 或 unittest 风格
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

### 代码组织约定

| 层级 | 职责 | 不应做的事情 |
|------|------|-------------|
| `api/` | HTTP 请求/响应处理、参数校验 | 不包含业务逻辑 |
| `services/` | 业务逻辑实现 | 不直接操作 HTTP 请求/响应 |
| `models/` | 数据库表定义 | 不包含业务方法 |
| `pipeline/` | 工作流编排 | 不直接调用外部 API (通过 services) |
| `middleware/` | 请求/响应拦截 | 不包含业务逻辑 |

---

## 故障排查

### 快速诊断

```powershell
.\check.ps1
# 自动检查: Python/Node/FFmpeg/Git 是否安装
#           端口 8000 是否可用
#           配置文件是否完整
#           服务是否运行中
```

### 常见问题速查表

| 现象 | 可能原因 | 解决步骤 |
|------|---------|---------|
| 浏览器打不开 | 服务未启动或端口冲突 | ① `.\stop.ps1` 再 `.\start.ps1` ② 检查是否用了 HTTPS |
| `python`/`node`/`ffmpeg` 命令找不到 | 未加入系统 PATH | 重新安装并确认安装时勾选 "Add to PATH" |
| 上传后按钮无反应 | 文件过大 (>500MB) 或格式不支持 | 检查文件格式 (仅 wav/mp3/m4a/flac/aac/ogg) |
| 摘要按钮灰色不可用 | LLM API Key 未配置 | 进入「设置」→「大模型」→ 填写 API Key |
| 首次转写很慢 (>3 分钟未开始) | FunASR 模型正在下载 (~500MB) | 等待模型下载完成，后续转写正常速度 |
| 转写失败 | 模型文件损坏 | 删除 `models/funasr/` 目录，重启应用自动重新下载 |
| 端口 8000 被占用 | 上次退出未正常关闭 | `.\stop.ps1` → `.\start.ps1` |
| 前端构建报 Access denied | Windows 进程权限限制 | 确认 `frontend/dist/index.html` 存在后直接启动 |
| Android 无法连接 | Token 或地址配置错误 | ① 确认 PC 端 start.ps1 运行中 ② 测试隧道连通性 ③ 检查 Token 是否启用 |
| 摘要生成超时 | LLM 接口响应慢 | 增加 `LLM_TIMEOUT_SECONDS` (默认 60s) |

更多详情：[docs/troubleshooting.md](./docs/troubleshooting.md)

---

## FAQ

### Q: 转写需要联网吗？
**A:** 不需要。FunASR 是完全离线的，模型下载一次后永久可用。只有摘要功能需要网络访问 LLM API。

### Q: 支持哪些语言？
**A:** 当前仅支持中文 (普通话)。FunASR paraformer-zh 模型针对中文优化。

### Q: 支持 GPU 加速吗？
**A:** 理论支持。设置 `ASR_DEVICE=cuda` 并安装 CUDA 版 PyTorch。但项目默认 CPU 模式以获得最佳兼容性。

### Q: 数据库在哪里？可以备份吗？
**A:** `data/app.db` (SQLite 文件)。直接复制该文件即可备份。建议同时备份 `data/recordings/` 目录。

### Q: 可以多台电脑共享数据吗？
**A:** 可以。将 `data/` 放到同步盘目录 (如 OneDrive、Syncthing)，在设置中修改「数据总目录」指向该位置。注意不要同时运行多个实例。

### Q: 目录监控会自动转写吗？
**A:** 不会。监控只负责发现和入库，不会自动触发转写。这是有意设计，避免批量误处理。

### Q: 删除录音会删除原始文件吗？
**A:** 对于上传的文件，会同时删除录音记录和应用生成文件。对于目录监控发现的文件，只会删除录音记录和生成文件，源文件保留。

### Q: 支持其他 LLM 提供商吗？
**A:** 理论上支持任何 OpenAI 兼容接口。在设置中自定义 `LLM_BASE_URL` 和 `LLM_MODEL` 即可。

---

## 项目结构

详细目录树和模块职责说明见 [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)。
API/模型/服务的开发参考见 [CODE_WIKI.md](./CODE_WIKI.md)。

---

## 版本历史

| 版本 | 日期 | 重大更新 |
|------|------|---------|
| **v3.2** | 2026-06 | 前端全面视觉重构：松石绿设计系统、弹性列表布局、交错动画、音频波形、毛玻璃导航 |
| **v3.1** | 2026-06 | 项目全面简化重构：代码精简 800+ 行、SSE 函数化、Pipeline 通用模板、脚本统一、冗余清理 |
| **v3.0** | 2026-05 | 任务可靠性（恢复/复用/取消）、音频播放+时间轴联动、转写校对、全文搜索、批量操作、多格式导出、API Token 多设备管理、安全头中间件 |
| **v2.0** | 2026-04 | 原生 Android 客户端、Cloudflare Tunnel 远程访问、SSE 实时推送 |
| **v1.0** | 早期 | 基础录音管理：上传、转写、摘要、导出、目录监控 |

完整更新历史：[CHANGELOG.md](./CHANGELOG.md)

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md) | 完整项目结构、模块职责、数据存储、启动脚本 |
| [CODE_WIKI.md](./CODE_WIKI.md) | 代码维基：API 参考、模型定义、服务层、配置项 |
| [CHANGELOG.md](./CHANGELOG.md) | 所有版本的详细变更日志 |
| [security_audit_report.md](./security_audit_report.md) | 安全审计报告 (v3.0) |
| [docs/TECH_STACK.md](./docs/TECH_STACK.md) | 技术栈详解 (版本/选型理由/替代方案) |
| [docs/troubleshooting.md](./docs/troubleshooting.md) | 故障排查指南 (按现象排查) |
| [docs/cloud-llm-providers.md](./docs/cloud-llm-providers.md) | LLM 服务商详细配置 |
| [docs/android-remote-access.md](./docs/android-remote-access.md) | Android 远程访问完整配置 |
| [docs/decisions/](./docs/decisions/) | 架构决策记录 (ADR) |
| [docs/product/roadmap.md](./docs/product/roadmap.md) | 产品路线图和未来规划 |
| [android/README.md](./android/README.md) | Android 端独立开发指南 |

---

## 许可证

MIT License

Copyright (c) 2025-2026 AI Recorder Contributors
