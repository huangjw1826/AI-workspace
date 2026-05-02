# AI Recorder

AI Recorder 是一个运行在本机 Windows 电脑上的录音整理工具。它把“录音文件入库、本地离线转写、云端大模型摘要、转写/摘要导出、目录监控”做成一个网页应用，日常使用不需要写代码。

当前推荐使用方式是：双击启动脚本，浏览器打开 `http://127.0.0.1:5173`，在网页里完成录音管理、转写、摘要和导出。

## 当前能力

- 录音库：集中展示录音文件、大小、时长、来源、创建时间和处理状态。
- 文件上传：上传音频后直接入库；上传目标统一使用用户配置的监控/保存目录，减少重复占用空间。
- 目录监控：可配置录音目录，系统按间隔扫描目录和子目录，发现新音频后入库。
- 去重识别：按文件内容 SHA-256 指纹判断是否已处理过，同一录音改名后不会重复入库。
- 本地转写：使用本地 FunASR 模型转写，不把原始音频上传到云端。
- 摘要模板：支持结构化摘要、会议纪要、待办事项、决策与风险、管理层简报、转写内容规整等模板。
- 多摘要管理：同一录音可保留多次摘要，支持展开/收起、下载和删除。
- 导出：转写内容和摘要支持 Markdown / TXT 下载，也可保存到用户指定目录。
- 设置页：可配置 LLM 服务、API Key、监控目录、转写保存目录、摘要保存目录。
- 便携打包：可生成一个可复制的 Windows 便携文件夹，方便换位置使用。

## 日常使用

### 双击方式

项目根目录有几个面向普通使用的入口：

```text
start-ai-recorder.bat  启动服务并打开浏览器
stop-ai-recorder.bat   停止服务
check-ai-recorder.bat  检查项目状态
setup-ai-recorder.bat  首次安装或修复依赖
```

最常用的是双击：

```text
start-ai-recorder.bat
```

启动成功后访问：

```text
http://127.0.0.1:5173
```

### PowerShell 方式

也可以在项目目录打开 PowerShell：

```powershell
.\start-all.ps1
```

用完后停止：

```powershell
.\stop-all.ps1
```

检查状态：

```powershell
.\check.ps1
```

首次安装或依赖损坏时：

```powershell
.\setup.ps1
```

## 支持的音频格式

```text
wav / mp3 / m4a / flac / aac / ogg
```

## 推荐工作流

1. 在“设置”中配置录音监控目录。
2. 把录音文件放进该目录，或在网页中上传录音。
3. 在“录音库”中选择录音，点击“转写”。
4. 转写完成后点击“摘要”，选择合适模板。
5. 在转写或摘要区域下载 `MD` / `TXT` 文件。
6. 不再需要的摘要可单独删除，不必删除整条录音。

第一版自动化策略比较稳妥：目录监控只负责发现和入库，不会自动转写或自动摘要。

## 目录监控与去重

目录监控可以在网页中配置：

- 是否启用监控
- 监控目录
- 是否包含子文件夹
- 扫描间隔

系统发现音频文件后，会等待文件大小和修改时间稳定，避免导入正在复制的文件。

去重以文件内容 SHA-256 为准，不以文件名或路径为准。因此同一录音即使改名再次出现，也会被识别为重复文件并跳过。

## 摘要模板

内置模板包括：

- 结构化摘要
- 会议纪要
- 待办事项
- 决策与风险
- 管理层简报
- 转写内容规整

摘要依赖云端大模型，转写本身可以本地离线完成。当前支持的摘要服务商：

- 小米 MiMo
- DeepSeek
- 通义千问 / Qwen

API Key 可以在网页“设置”中维护，也可以手动编辑：

```text
backend/.env
```

不要把 `backend/.env` 发给别人，因为里面可能包含 API Key。

## 导出与保存位置

网页中可以下载：

- 转写 Markdown
- 转写 TXT
- 摘要 Markdown
- 摘要 TXT

在“设置”中可以指定：

- 转写保存目录
- 摘要保存目录

如果使用目录监控，建议把转写和摘要目录放在录音目录下，便于归档。

## 便携式打包

生成便携文件夹：

```powershell
.\package-portable.ps1
```

默认输出：

```text
release\AI Recorder Portable
```

生成后可以把整个文件夹复制到其他位置，双击里面的：

```text
start-ai-recorder.bat
```

常用打包选项：

```powershell
.\package-portable.ps1 -Zip             # 额外生成 zip
.\package-portable.ps1 -ExcludeData     # 不带历史数据
.\package-portable.ps1 -ExcludeSecrets  # 不带 backend\.env 里的密钥
```

详细说明见：

```text
docs/portable.md
```

## 项目结构

```text
backend/       后端服务，负责 API、数据库、转写、摘要、导出
frontend/      React 网页界面，构建产物在 frontend/dist
data/          本地数据库、导出结果和应用数据，最需要备份
models/        FunASR 离线转写模型
logs/          运行日志
scripts/       辅助脚本
docs/          说明文档
.tools/        Node、ffmpeg 等本地工具
```

## 重要文件

最需要备份：

```text
data/
backend/.env
```

体积较大但可重建：

```text
backend/.venv/
frontend/node_modules/
frontend/dist/
models/
.tools/
logs/
release/
```

## 清理策略

可以安全清理的通常是：

- `release/` 生成的便携包
- `logs/` 中历史诊断日志
- `.workbuddy/` 等外部工具工作痕迹
- `frontend/node_modules/.vite-temp`
- 模型下载失败后残留的 `.lock` 或 `._____temp`
- 已解压后不再需要的工具 zip

不建议手动删除：

- `data/app.db`
- `backend/.env`
- 正式的 `models/funasr/models/...` 模型目录
- `backend/.venv/`
- `.tools/ffmpeg/ffmpeg.exe`
- `.tools/node-v24.14.0-win-x64/`

## 故障排查

先运行：

```powershell
.\check.ps1
```

如果端口被占用或页面打不开：

```powershell
.\stop-all.ps1
.\start-all.ps1
```

如果依赖缺失：

```powershell
.\setup.ps1
```

更多排障说明：

```text
docs/troubleshooting.md
```
