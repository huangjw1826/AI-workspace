# 便携式使用说明

## 推荐方式

运行项目根目录下的打包脚本：

```powershell
.\package-portable.ps1
```

生成目录：

```text
release\AI Recorder Portable
```

把这个文件夹整体复制到需要使用的位置，然后双击：

```text
start-ai-recorder.bat
```

页面会自动打开：

```text
http://127.0.0.1:8000
```

## 常用入口

```text
start-ai-recorder.bat  启动并打开浏览器
stop-ai-recorder.bat   停止服务
check-ai-recorder.bat  检查运行状态
setup-ai-recorder.bat  修复或重装依赖
```

## 打包选项

生成文件夹并额外压缩：

```powershell
.\package-portable.ps1 -Zip
```

生成不带历史数据的干净包：

```powershell
.\package-portable.ps1 -ExcludeData
```

生成可分享给别人的包，不带 `backend\.env` 里的密钥：

```powershell
.\package-portable.ps1 -ExcludeSecrets
```

## 注意

- 便携包适合个人 Windows 电脑使用。
- 如果要发给别人，建议使用 `-ExcludeSecrets`，避免把 API Key 一起发出去。
- `models` 和 `backend\.venv` 体积较大，但保留它们可以减少新电脑上的重新安装时间。
