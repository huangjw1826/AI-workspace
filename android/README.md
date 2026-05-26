# AI Recorder Android 客户端 - 快速开始指南

## 📋 环境要求

| 组件 | 要求 | 当前状态 |
|------|------|----------|
| Java | JDK 17+ | ✅ JDK 21 (Android Studio JBR) |
| Android SDK | API 29+ | ✅ API 36 |
| Gradle | 8.5 | ✅ 已配置 |
| Android Studio | Hedgehog+ | ✅ 已安装 |

---

## 🚀 构建和运行步骤

### 1. 打开项目

1. 启动 Android Studio
2. 选择 **File** → **Open**
3. 浏览到 `C:\Users\13318\Documents\AI workspace\android`
4. 点击 **OK**

### 2. 等待 Gradle 同步

首次打开时，Android Studio 会下载依赖并同步项目：

```
等待显示: "Gradle sync completed"
```

⚠️ 如果同步失败，检查网络连接并重试。

### 3. 配置运行设备

**方式 A：使用物理设备**
1. 在手机上开启 **开发者选项** 和 **USB 调试**
2. 用 USB 连接手机到电脑
3. 手机上选择"允许 USB 调试"

**方式 B：使用模拟器**
1. 选择 **Tools** → **Device Manager**
2. 点击 **Create Device**
3. 选择设备类型（如 Pixel 7）
4. 选择系统镜像（API 36）
5. 创建并启动模拟器

### 4. 运行应用

1. 在顶部工具栏选择目标设备
2. 点击 ▶️ **Run** 按钮（或按 `Shift + F10`）
3. 等待应用安装并启动

---

## 🔧 首次配置

### 1. 配置服务器连接

应用启动后，进入 **设置** 页面：

**服务器地址**：
- **正式地址**: `https://weizziwong.top`
- **或本地测试**: `http://192.168.x.x:8000`（确保 PC 和手机在同一网络）

**API Token**：
1. 在 PC 上打开 `backend/.env` 文件
2. 复制 `API_TOKEN=` 后面的值
3. 粘贴到 Android 应用的 Token 输入框

### 2. 测试连接

点击 **测试连接** 按钮：
- ✅ 成功：显示"连接成功"
- ❌ 失败：检查服务器地址和 Token 是否正确

---

## 📱 应用功能

### 🎵 录音库（首页）
- 查看 PC 端所有录音
- 搜索录音
- 点击录音查看详情

### 📄 录音详情
- **转写 Tab**：查看完整转写内容
- **摘要 Tab**：查看 AI 摘要
- **信息 Tab**：查看录音元数据
- **删除**：删除录音

### ⬆️ 上传录音
- 点击右下角 **+** 按钮
- 选择音频文件（支持 wav, mp3, m4a, flac, aac, ogg）
- 等待上传完成

### 🩺 健康面板
- 查看 FastAPI 后端状态
- 查看 Cloudflare 隧道状态
- 查看系统资源（CPU、内存、磁盘）

### ⚙️ 设置
- 修改服务器地址和 Token
- 查看 LLM 配置信息

---

## 🔍 故障排查

### 问题 1：Gradle Sync 失败

**解决方案**：
1. 点击 **File** → **Invalidate Caches**
2. 选择 **Invalidate and Restart**
3. 等待重新启动后再次同步

### 问题 2：无法连接服务器

**检查清单**：
- [ ] PC 后端是否运行（运行 `.\check.ps1`）
- [ ] Cloudflare 隧道是否连接
- [ ] 服务器地址是否正确
- [ ] API Token 是否正确
- [ ] 手机网络是否正常

**本地测试**：
在手机浏览器中访问 `http://PC的IP地址:8000/health`

### 问题 3：找不到设备

**解决方案**：
1. 检查 USB 驱动是否安装
2. 尝试更换 USB 端口
3. 重启 ADB 服务：

```powershell
adb kill-server
adb start-server
```

---

## 📂 项目结构

```
android/
├── app/
│   └── src/main/
│       ├── java/com/airecorder/android/
│       │   ├── data/              # 数据层
│       │   │   ├── local/         # 本地存储（DataStore）
│       │   │   ├── model/          # 数据模型
│       │   │   ├── remote/        # API 服务
│       │   │   └── repository/     # 仓库层
│       │   ├── di/                # 依赖注入（Hilt）
│       │   ├── ui/                # UI 层
│       │   │   ├── components/     # 可复用组件
│       │   │   ├── navigation/     # 导航配置
│       │   │   ├── screens/        # 页面
│       │   │   └── theme/          # 主题
│       │   └── util/               # 工具类
│       ├── res/                    # 资源文件
│       └── AndroidManifest.xml
├── build.gradle.kts                # 项目配置
└── settings.gradle.kts             # Gradle 设置
```

---

## 🔗 相关文档

- [Android 远程访问配置](../android-remote-access.md)
- [Android PRD](../android-app-prd.md)
- [后端 API 文档](../backend/README.md)
