# AI Recorder 排障指南

这份文档按“你看到的现象”来排查，不需要懂代码。

## 先做一次体检

运行：

```powershell
.\check.ps1
```

如果出现 `[FAIL]`，优先处理失败项。如果只是 `[WARN] 服务当前可能未启动`，通常运行 `.\start.ps1` 即可。

## 浏览器打不开页面

确认你访问的是：

```text
http://127.0.0.1:8000
```

不是 `https`。

然后按顺序运行：

```powershell
.\stop.ps1 然后 .\start.ps1
```

如果仍然打不开，查看：

```text
logs/frontend.err.log
logs/backend.err.log
```

## 提示端口 8000 被占用

通常是上次服务没有正常退出。运行：

```powershell
.\stop.ps1 然后 .\start.ps1
```

`stop.ps1` + `start.ps1` 会先清理旧进程再重新启动。

## 上传后没有自动转写

这是正常设计。上传只负责保存音频，转写需要你点右侧的“转写”按钮。

## 摘要按钮不能用或提示 API Key 问题

转写功能不需要 API Key，摘要功能需要。

检查网页左侧“设置”，或者运行：

```powershell
.\check.ps1
```

如果看到“摘要 API Key 未配置”，就在网页设置里填写 DeepSeek、通义千问或小米 MiMo 的 API Key。

## 转写很慢

当前项目使用 CPU 进行本地转写。音频越长，等待越久。

大致预期：

- 5 分钟音频：约 1 到 3 分钟
- 30 分钟音频：约 5 到 12 分钟
- 1 小时音频：约 10 到 25 分钟

如果电脑同时开了很多程序，速度会更慢。

## 前端构建报 Access is denied 或 spawn EPERM

这通常是 Windows 当前环境对某些 Node/Vite 子进程的执行限制，不一定是代码坏了。

优先处理方式：

1. 确认已有 `frontend/dist/index.html`
2. 直接运行 `.\start.ps1`
3. 如果必须重新构建，再运行 `.\setup.ps1`

如果 `setup.ps1` 仍然失败，保留报错内容，再让开发助手继续处理。

## 需要备份哪些东西

最重要的是：

```text
data/
backend/.env
```

`data/` 是你的录音、转写结果、摘要和数据库。

`backend/.env` 可能包含 API Key，不要发给别人。

## 可以删除哪些东西来节省空间

不建议随便删。如果确实需要清理，优先确认你已经备份 `data/`。

一般可重建的目录：

```text
backend/.venv/
frontend/node_modules/
frontend/dist/
logs/
```

删除后通常需要重新运行：

```powershell
.\setup.ps1
```

`models/` 体积较大，但删除后首次转写可能需要重新下载模型。
