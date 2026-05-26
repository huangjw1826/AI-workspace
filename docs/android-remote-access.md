# Android 远程访问完整配置

这份说明对应 `docs/android-app-prd.md`，包含：PC 后端公网安全访问配置和 Android 客户端的完整使用指南。

## 目标

- PC 后端通过 Cloudflare Tunnel 或 IPv6 公网安全访问
- 原生 Android 客户端通过公网域名和 API Token 访问 PC 录音库
- 手机上传音频到 PC 端录音目录，由 PC 负责转写和摘要
- 完整的系统健康监控和状态管理

---

## 第一部分：PC 后端配置

### 1. 生成 API Token

在 PowerShell 中生成 40 位随机 Token：

```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 40 | ForEach-Object { [char]$_ })
```

把结果写入 `backend/.env`：

```env
API_TOKEN=your_40_char_random_token
```

**重要提示**：`backend/.env` 不要提交、截图或发送给别人。

### 2. 配置允许来源

本机开发可保留默认值：

```env
CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

当前远程访问只面向 Android 客户端，公网域名不需要加入浏览器 CORS 白名单。修改 `.env` 后需要重启后端。

### 3. 配置 Cloudflare Tunnel（推荐）

#### 快速临时测试

使用项目本地的 `.tools\cloudflared.exe` 开一个随机 `trycloudflare.com` 地址：

```powershell
# 确保先启动后端
.\start-all.ps1

# 启动临时隧道（新开一个 PowerShell 窗口）
.\tools\cloudflared.exe tunnel --url http://localhost:8000
```

这个地址适合手机实测，但每次重启都可能变化。

#### 正式使用：绑定域名

1. 在 [Cloudflare](https://dash.cloudflare.com) 注册账号并添加域名
2. 安装 cloudflared：

```powershell
winget install --id Cloudflare.cloudflared
```

3. 登录并创建隧道：

```powershell
cloudflared tunnel login
cloudflared tunnel create ai-recorder
cloudflared tunnel route dns ai-recorder recorder.your-domain.com
```

4. 创建配置文件 `C:\Users\<用户名>\.cloudflared\config.yml`：

```yaml
tunnel: <tunnel-uuid>
credentials-file: C:\Users\<用户名>\.cloudflared\<tunnel-uuid>.json

ingress:
  - hostname: recorder.your-domain.com
    service: http://localhost:8000
  - service: http_status:404
```

5. 安装为 Windows 服务：

```powershell
cloudflared service install
```

**优点**：
- 无需公网 IP
- 自动 HTTPS
- PC 重启后自动恢复
- 免费稳定

### 3b. 阿里云域名 + 动态 IPv6 直连（备选）

如果域名在阿里云，且本机有公网 IPv6：

```text
https://recorder.weizziwong.top
```

推荐使用子域名，不直接占用根域名。

#### 脚本说明

项目已提供这些脚本：

```powershell
.\scripts\get-public-ipv6.ps1
.\scripts\get-public-ipv6.ps1 -All
.\scripts\update-aliyun-ddns.ps1 -DomainName weizziwong.top -RR recorder
.\enable-recorder-firewall-admin.bat
.\scripts\start-caddy-recorder.ps1 -HostName recorder.weizziwong.top
.\scripts\check-domain-access.ps1 -HostName recorder.weizziwong.top
.\scripts\register-aliyun-ddns-task.ps1 -DomainName weizziwong.top -RR recorder
```

#### 快速配置步骤

1. 安装并配置阿里云 CLI
2. 准备 `caddy.exe` 到 `.tools\` 目录
3. 双击 `enable-recorder-firewall-admin.bat` 放行防火墙
4. 在路由器和光猫放行 IPv6 TCP 80/443
5. 按顺序执行：

```powershell
.\start-all.ps1
.\scripts\start-caddy-recorder.ps1 -HostName recorder.weizziwong.top
.\scripts\check-domain-access.ps1 -HostName recorder.weizziwong.top
```

### 4. 验证后端访问

本机验证：

```powershell
.\scripts\check-remote-access.ps1 -BaseUrl http://127.0.0.1:8000 -Token "your_40_char_random_token"
```

公网验证：

```powershell
# Cloudflare Tunnel
.\scripts\check-remote-access.ps1 -BaseUrl https://recorder.your-domain.com -Token "your_40_char_random_token"

# 或 IPv6
.\scripts\check-remote-access.ps1 -BaseUrl https://recorder.weizziwong.top -Token "your_40_char_random_token"
```

公网通过标准：
- `/health` 返回 200 并包含系统信息
- 未带 Token 访问 `/api/recordings` 返回 403
- 带正确 `X-API-Token` 访问 `/api/recordings` 返回 200

---

## 第二部分：Android 客户端使用

### 1. 项目结构

Android 项目位于 `android/` 目录：

```text
android/
├── app/
│   └── src/main/
│       ├── java/com/airecorder/android/
│       │   ├── data/          # 数据层（API、模型、存储）
│       │   ├── di/            # 依赖注入（Hilt）
│       │   ├── ui/            # UI 层（组件、页面、主题）
│       │   └── util/          # 工具类
│       ├── res/               # 资源文件
│       └── AndroidManifest.xml
├── build.gradle.kts
└── settings.gradle.kts
```

### 2. 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material Design 3
- **架构**：MVVM + Repository
- **网络**：Retrofit + OkHttp
- **依赖注入**：Hilt
- **存储**：DataStore Preferences
- **异步**：Coroutines + Flow

### 3. 编译与安装

#### 前置要求

- Android Studio Hedgehog 或更高版本
- JDK 17 或更高版本
- Android SDK 26 (Android 8.0) 或更高

#### 编译步骤

1. 使用 Android Studio 打开 `android/` 目录
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 或执行：

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

### 4. 客户端配置

#### 首次启动配置

1. 打开 AI Recorder Android 应用
2. 进入「设置」页面
3. 填写服务器地址：
   - Cloudflare Tunnel：`https://recorder.your-domain.com`
   - 或 IPv6：`https://recorder.weizziwong.top`
   - 或临时地址：`https://xxx.trycloudflare.com`
4. 填写 API Token（与 PC 端 `backend/.env` 中的一致）
5. 点击「测试连接」
6. 连接成功后会自动保存

#### 主要功能

##### 📚 录音库（首页）

- 查看 PC 端所有录音列表
- 按状态、时间或文件名搜索
- 点击录音查看详情
- 点击右下角「+」上传新录音

##### 📝 录音详情

- **转写 Tab**：查看并复制完整转写内容
- **摘要 Tab**：查看并复制摘要内容
- **信息 Tab**：查看录音元数据（时长、格式、创建时间等）
- 删除录音

##### 📤 上传录音

- 从手机选择音频文件
- 支持格式：wav、mp3、m4a、flac、aac、ogg
- 最大文件大小：500 MB
- 上传后自动在 PC 端处理

##### ⚙️ 设置

- 服务器地址配置
- API Token 配置
- 测试连接
- 查看 LLM 配置（只读）
- 版本信息

##### 🩺 健康面板

- FastAPI 后端状态
- Cloudflare 隧道状态
- FunASR 模型状态
- FFmpeg 状态
- LLM 连接状态
- 系统资源（CPU、内存、磁盘）
- 运行时长

---

## 第三部分：API 接口约定

### 认证

- 健康检查：`GET /health`，**不需要** Token
- 其他接口：请求头必须包含 `X-API-Token`

### 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 系统健康检查（含系统信息） |
| GET | `/api/settings` | 获取只读设置（LLM 配置） |
| GET | `/api/recordings` | 获取录音列表 |
| GET | `/api/recordings/{id}` | 获取单个录音详情 |
| GET | `/api/recordings/{id}/transcript` | 获取转写内容 |
| GET | `/api/recordings/{id}/summary/{summaryId}` | 获取摘要内容 |
| POST | `/api/recordings` | 上传音频（multipart/form-data，字段名 `file`） |
| DELETE | `/api/recordings/{id}` | 删除录音 |

### 安全建议

- Android 客户端不应在日志中输出：
  - API Token
  - 文件路径
  - 转写内容
  - 摘要内容
- 使用 HTTPS（Cloudflare Tunnel 或 Caddy 自动处理）
- 定期更换 API Token

---

## 第四部分：故障排查

### 客户端无法连接

1. 检查 PC 后端是否正在运行
2. 检查隧道是否正常运行
3. 在 Android 手机浏览器中访问 `/health` 测试
4. 检查 API Token 是否正确

### 上传失败

1. 检查网络连接
2. 确认文件格式支持
3. 检查文件大小是否超过 500 MB
4. 查看 PC 端后端日志

### 健康面板显示异常

1. 检查各服务是否正常运行
2. 点击刷新按钮重新获取状态
3. 查看 PC 端系统资源使用情况

### 查看日志

PC 端日志位于 `logs/` 目录：
- `logs/backend.log`：后端日志
- `logs/cloudflared.log`：隧道日志（如果使用）

---

## 第五部分：开发进阶

### 调试 Android 应用

```bash
# 查看日志
adb logcat -s AIRecorder:*

# 清理数据
adb shell pm clear com.airecorder.android
```

### 自定义构建

修改 `app/build.gradle.kts` 中的版本号和应用名称：

```kotlin
defaultConfig {
    applicationId = "com.airecorder.android"
    versionCode = 1
    versionName = "1.0.0"
}
```

### 架构说明

- **UI Layer**：Compose 组件 + ViewModel
- **Data Layer**：Repository + ApiService + DataStore
- **DI Layer**：Hilt 依赖注入

---

## 相关文档

- [产品设计文档](./AI-Recorder-产品设计文档.md)
- [Android PRD](./android-app-prd.md)
- [大模型配置](./cloud-llm-providers.md)
- [故障排查](./troubleshooting.md)
