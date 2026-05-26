# AI Recorder

AI Recorder 是一个面向 Windows 本机使用的录音整理工具。它把音频入库、本地离线转写、云端大模型摘要、导出和目录监控整合到一个网页应用里，适合整理会议录音、访谈、课堂笔记和日常语音记录。

转写默认在本机完成，不会把原始音频上传到云端；摘要功能需要你配置自己的大模型 API Key。

## 功能概览

- 录音库：集中管理音频文件、时长、来源、创建时间和处理状态。
- 文件上传：在网页里上传 `wav`、`mp3`、`m4a`、`flac`、`aac`、`ogg` 等音频。
- 音频播放：在录音详情页直接播放音频，并可点击转写时间戳跳转播放位置。
- 转写校对：可直接编辑转写片段，保存后用于后续摘要和导出。
- 本地转写：使用 FunASR 在本机 CPU 上完成中文语音转文字。
- 智能摘要：支持 DeepSeek、通义千问/Qwen、小米 MiMo 等 OpenAI 兼容接口。
- 多份摘要：同一条录音可以保留多次摘要结果，便于对比不同模板。
- 录音库管理：支持内容搜索、标签维护、多选、批量转写、批量摘要和批量删除。
- 导出：转写可下载为 Markdown、TXT、JSON、SRT、DOCX；摘要可下载为 Markdown、TXT、DOCX。
- 目录监控：定时扫描录音目录，新音频自动入库。
- Android 远程访问：原生 Android 客户端通过公网域名和 API Token 访问 PC 端录音库。

## 克隆后快速开始

当前项目主要面向 Windows 10/11。请先安装这些工具，并确认它们可以在 PowerShell 中直接运行：

- Python 3.10 到 3.12
- Node.js 20 或更高版本
- ffmpeg
- Git

克隆并启动：

```powershell
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace
.\setup.ps1
.\start-all.ps1
```

启动成功后打开：

```text
http://127.0.0.1:8000
```

使用结束后停止服务：

```powershell
.\stop-all.ps1
```

检查当前状态：

```powershell
.\check.ps1
```

## 首次使用流程

1. 打开 `http://127.0.0.1:8000`。
2. 进入“设置”，确认录音保存目录、转写保存目录和摘要保存目录。
3. 如果需要摘要，配置大模型服务商和 API Key。
4. 在“录音库”上传音频，或在“目录监控”里配置要扫描的录音目录。
5. 对录音点击“转写”。
6. 转写完成后选择摘要模板生成摘要。
7. 校对转写片段，必要时维护标签或批量处理多条录音。
8. 下载 Markdown/TXT/JSON/SRT/DOCX 文件，或把结果保存到你指定的目录。

目录监控只负责发现并入库新音频，不会自动转写或自动摘要。这样可以避免误处理大量文件。

## 配置大模型摘要

转写不需要 API Key，摘要需要。

你可以在网页“设置”里配置，也可以编辑：

```text
backend/.env
```

常用配置项：

```env
API_TOKEN=
LLM_PROVIDER=deepseek
LLM_API_KEY=your_api_key_here
LLM_MODEL=
LLM_BASE_URL=
LLM_TIMEOUT_SECONDS=60
LLM_RETRY_ATTEMPTS=3
```

支持的服务商预设：

| Provider | 默认接口 | 默认模型 | API Key |
| --- | --- | --- | --- |
| `deepseek` | `https://api.deepseek.com` | `deepseek-chat` | `LLM_API_KEY` |
| `tongyi` / `qwen` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | `LLM_API_KEY` |
| `mimo` | OpenAI 兼容接口 | `MiMo-V2.5` | `MIMO_API_KEY` 或 `LLM_API_KEY` |

更多说明见 [docs/cloud-llm-providers.md](docs/cloud-llm-providers.md)。

不要把 `backend/.env` 提交到 GitHub，也不要发给别人，它可能包含 API Key。

## 本地数据和隐私

这些目录不会上传到仓库：

```text
backend/.env
backend/.venv/
frontend/node_modules/
frontend/dist/
data/
models/
logs/
.tools/
release/
```

最值得备份的是：

```text
data/
backend/.env
```

`data/` 里通常包含数据库、录音、转写结果和摘要结果。`models/` 是 FunASR 模型缓存，体积较大，删除后可以重新下载，但首次转写会变慢。

## 项目结构

```text
backend/       FastAPI 后端，负责 API、数据库、转写、摘要和导出
frontend/      React 前端，构建产物在 frontend/dist
data/          本地数据库、音频、转写和摘要数据
models/        FunASR 离线模型缓存
logs/          后端和前端运行日志
scripts/       辅助脚本
docs/          部署方案、产品规划、ADR、排错和便携包说明
```

产品版本规划放在：

```text
docs/product/
```

当前 3.0 迭代资料见 [docs/product/versions/3.0/README.md](docs/product/versions/3.0/README.md)。
部署方案见 [docs/deployment-plan.md](docs/deployment-plan.md)，原型文件放在 [docs/product/prototypes/](docs/product/prototypes/)。
Android 远程访问阶段 0 配置见 [docs/android-remote-access.md](docs/android-remote-access.md)。
阿里云域名 + 动态 IPv6 直连也记录在同一文档中，默认子域名为 `recorder.weizziwong.top`。

## 常用命令

```powershell
.\setup.ps1          # 安装依赖并构建前端
.\start-all.ps1      # 启动后端和网页服务
.\stop-all.ps1       # 停止服务
.\check.ps1          # 检查依赖、端口、配置和服务状态
.\scripts\check-remote-access.ps1 -BaseUrl http://127.0.0.1:8000 -Token "你的API_TOKEN"
```

`setup.ps1` 会优先使用 Python 3.12、3.11 或 3.10 创建后端虚拟环境；如果系统只有 Python 3.13/3.14，请先安装受支持版本。

后端最小测试：

```powershell
cd backend
.\.venv\Scripts\python.exe -m unittest discover -s tests
```

## 故障排查

先运行：

```powershell
.\check.ps1
```

常见处理方式：

- `python`、`node`、`npm` 或 `ffmpeg` 找不到：重新安装对应工具，并确认它们已经加入系统 PATH。
- 页面打不开：确认访问的是 `http://127.0.0.1:8000`，不是 `https`。
- 端口被占用：先运行 `.\stop-all.ps1`，再运行 `.\start-all.ps1`。
- 摘要不可用：检查网页设置里的 API Key，转写本身不需要 API Key。
- 首次转写慢：FunASR 模型可能正在下载或初始化。
- 前端缺失：运行 `.\setup.ps1` 重新安装依赖并构建。

更多说明见 [docs/troubleshooting.md](docs/troubleshooting.md)。

## 克隆可用性说明

这个仓库不会提交依赖、模型、数据库、日志和密钥。新用户 clone 后需要执行 `.\setup.ps1` 安装依赖并构建前端；首次转写时 FunASR 会下载模型缓存。也就是说，“克隆即可用”的实际流程是：

```powershell
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace
.\setup.ps1
.\start-all.ps1
```

如果你的系统已经正确安装 Python、Node.js、ffmpeg，并且网络可以访问 Python/npm/模型下载源，这条流程可以成立。

## 许可证

当前仓库尚未包含许可证文件。公开发布前请添加合适的开源许可证。
