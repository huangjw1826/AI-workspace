# AI Recorder 项目结构说明

## 📚 目录

- [项目概述](#项目概述)
- [整体架构](#整体架构)
- [PC 端结构](#pc-端结构)
  - [后端](#后端)
  - [前端](#前端)
- [Android 端结构](#android-端结构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)

---

## 项目概述

AI Recorder 是一个面向 Windows 本机的录音整理工具，包含：
- **PC 端**：负责音频处理、转写、摘要、存储和 Web 界面
- **Android 端**：提供移动端访问录音库、上传音频的能力
- **远程访问**：支持通过公网域名 + API Token 安全访问

---

## 整体架构

```
AI-workspace/
├── backend/              # FastAPI 后端服务
├── frontend/             # React 前端应用
├── android/              # Android 原生客户端
├── docs/                 # 项目文档
├── scripts/              # 辅助脚本
├── data/                 # 数据目录（数据库、音频、转写、摘要）
├── models/               # FunASR 模型缓存
├── logs/                 # 运行日志
├── setup.ps1             # 环境安装脚本
├── start-all.ps1         # 启动服务脚本
├── stop-all.ps1          # 停止服务脚本
└── check.ps1             # 健康检查脚本
```

---

## PC 端结构

### 后端

```
backend/
├── app/
│   ├── api/                    # API 路由层
│   │   ├── auth.py            # 认证中间件
│   │   ├── events.py           # 事件订阅 API (SSE)
│   │   ├── filesystem.py       # 文件系统操作 API
│   │   ├── health.py           # 健康检查 API
│   │   ├── recordings.py       # 录音管理 API
│   │   ├── settings.py         # 设置 API
│   │   ├── summary.py          # 摘要 API
│   │   ├── tasks.py            # 任务管理 API
│   │   ├── transcribe.py       # 转写 API
│   │   └── watch.py            # 目录监控 API
│   ├── db/                     # 数据库层
│   │   └── database.py         # SQLite 数据库连接和初始化
│   ├── models/                 # 数据模型层
│   │   ├── recording.py       # Recording 模型（录音记录）
│   │   ├── summary.py         # Summary 模型（摘要）
│   │   ├── task.py            # Task 模型（任务）
│   │   ├── transcript.py      # TranscriptSegment 模型（转写片段）
│   │   └── watch_event.py    # WatchEvent 模型（监控事件）
│   ├── pipeline/               # 工作流管道
│   │   └── workflow.py        # 转写/摘要任务编排
│   ├── services/               # 业务服务层
│   │   ├── asr_service.py     # ASR 转写服务（FunASR）
│   │   ├── audio_service.py   # 音频处理服务（FFmpeg）
│   │   ├── docx_export.py     # DOCX 导出服务
│   │   ├── export_names.py    # 导出文件名生成
│   │   ├── file_service.py    # 文件操作服务
│   │   ├── runtime_log.py     # 运行时日志服务
│   │   ├── summary_service.py # 摘要服务（大模型）
│   │   ├── task_service.py    # 任务管理服务
│   │   └── watch_service.py   # 目录监控服务
│   ├── config.py              # 配置管理
│   └── main.py                # FastAPI 应用入口
├── tests/                     # 单元测试
│   ├── test_api_auth.py
│   ├── test_recording_audio.py
│   ├── test_recording_management.py
│   └── test_task_service.py
├── .env.example               # 环境变量示例
├── requirements.txt           # Python 依赖
└── start.ps1                  # 后端启动脚本
```

#### 后端核心模块说明

| 模块 | 职责 |
|------|------|
| **api/** | RESTful API 端点定义，处理 HTTP 请求/响应 |
| **models/** | SQLModel 数据模型，对应数据库表结构 |
| **services/** | 核心业务逻辑实现（转写、摘要、音频处理等） |
| **pipeline/** | 任务执行流程编排 |
| **db/** | 数据库连接和会话管理 |

---

### 前端

```
frontend/
├── src/
│   ├── components/              # 可复用组件
│   │   ├── feedback/           # 反馈组件（Toast、ConfirmDialog）
│   │   ├── layout/             # 布局组件（NavBar）
│   │   ├── markdown/           # Markdown 渲染组件
│   │   ├── recording/          # 录音相关组件
│   │   └── ui/                 # UI 基础组件
│   ├── lib/                    # 工具函数和库
│   │   ├── api.ts            # API 客户端
│   │   ├── format.ts         # 格式化工具
│   │   ├── types.ts          # TypeScript 类型定义
│   │   └── viewTypes.ts      # 视图类型定义
│   ├── pages/                  # 页面组件
│   │   ├── HealthPage.tsx    # 健康检查页面
│   │   ├── LibraryPage.tsx   # 录音库页面
│   │   ├── SettingsPage.tsx  # 设置页面
│   │   └── WatchPage.tsx     # 目录监控页面
│   ├── stores/                 # 状态管理
│   │   ├── appStore.ts       # 应用全局状态
│   │   └── taskStore.ts      # 任务状态管理
│   ├── App.tsx               # 应用主组件
│   ├── main.tsx              # 应用入口
│   └── styles.css            # 全局样式
├── index.html                # HTML 模板
├── package.json              # 项目依赖
├── tsconfig.json             # TypeScript 配置
├── vite.config.mjs           # Vite 构建配置
└── build.ps1                 # 前端构建脚本
```

#### 前端页面说明

| 页面 | 功能 |
|------|------|
| **LibraryPage** | 录音库：查看、搜索、管理录音 |
| **WatchPage** | 目录监控：配置监控目录、查看监控事件 |
| **SettingsPage** | 设置：配置存储路径、大模型等 |
| **HealthPage** | 健康面板：系统状态检查 |

---

## Android 端结构

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/airecorder/android/
│   │   │   ├── data/                    # 数据层
│   │   │   │   ├── local/              # 本地存储
│   │   │   │   │   └── PreferencesManager.kt  # DataStore 配置管理
│   │   │   │   ├── model/              # 数据模型
│   │   │   │   │   └── Models.kt       # 所有数据模型定义
│   │   │   │   ├── remote/             # 远程 API
│   │   │   │   │   ├── ApiService.kt   # Retrofit API 接口
│   │   │   │   │   └── AuthInterceptor.kt # Token 认证拦截器
│   │   │   │   └── repository/         # 仓库层
│   │   │   │       ├── RecordingRepository.kt  # 录音数据仓库
│   │   │   │       └── SettingsRepository.kt   # 设置数据仓库
│   │   │   ├── di/                      # 依赖注入层
│   │   │   │   └── AppModule.kt         # Hilt 模块配置
│   │   │   ├── ui/                      # UI 层
│   │   │   │   ├── components/          # 可复用组件
│   │   │   │   │   ├── BottomNavigationBar.kt
│   │   │   │   │   └── RecordingItem.kt
│   │   │   │   ├── navigation/          # 导航配置
│   │   │   │   │   └── NavDestinations.kt
│   │   │   │   ├── screens/             # 页面
│   │   │   │   │   ├── DetailScreen.kt          # 录音详情页
│   │   │   │   │   ├── DetailViewModel.kt
│   │   │   │   │   ├── HealthScreen.kt          # 健康面板页
│   │   │   │   │   ├── LibraryScreen.kt         # 录音库页
│   │   │   │   │   ├── LibraryViewModel.kt
│   │   │   │   │   ├── SettingsScreen.kt        # 设置页
│   │   │   │   │   └── UploadBottomSheet.kt     # 上传底部弹窗
│   │   │   │   ├── theme/               # 主题配置
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   └── AIRecorderApp.kt     # 应用入口 Composable
│   │   │   ├── util/                    # 工具类
│   │   │   │   └── FormatUtils.kt
│   │   │   ├── MainActivity.kt          # 主 Activity
│   │   │   └── AIRecorderApplication.kt # Application 类
│   │   ├── res/                        # 资源文件
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts               # 应用级 Gradle 配置
├── build.gradle.kts                   # 项目级 Gradle 配置
├── settings.gradle.kts                # Gradle 设置
├── gradle.properties
├── gradlew / gradlew.bat
└── README.md                          # Android 端快速开始指南
```

#### Android 端架构说明

| 层级 | 职责 |
|------|------|
| **UI Layer** | Jetpack Compose 组件 + ViewModel |
| **Data Layer** | Repository + ApiService + DataStore |
| **DI Layer** | Hilt 依赖注入 |

#### Android 端页面说明

| 页面 | 功能 |
|------|------|
| **LibraryScreen** | 录音库：查看 PC 端所有录音、搜索、上传 |
| **DetailScreen** | 录音详情：查看转写、摘要、录音信息、删除 |
| **HealthScreen** | 健康面板：系统状态、资源使用 |
| **SettingsScreen** | 设置：配置服务器地址、Token |

---

## 技术栈

### PC 端 - 后端

| 技术 | 版本/说明 |
|------|-----------|
| **语言** | Python 3.10+ |
| **Web 框架** | FastAPI |
| **ORM** | SQLModel |
| **数据库** | SQLite |
| **语音转写** | FunASR |
| **大模型** | OpenAI SDK（支持 DeepSeek、通义千问、小米 MiMo） |
| **音频处理** | FFmpeg |
| **异步** | asyncio |

### PC 端 - 前端

| 技术 | 版本/说明 |
|------|-----------|
| **语言** | TypeScript |
| **UI 框架** | React 19 |
| **构建工具** | Vite |
| **图标库** | Lucide React |

### Android 端

| 技术 | 版本/说明 |
|------|-----------|
| **语言** | Kotlin |
| **最低 SDK** | API 26 (Android 8.0) |
| **目标 SDK** | API 36 |
| **UI** | Jetpack Compose + Material Design 3 |
| **架构** | MVVM + Repository |
| **网络** | Retrofit + OkHttp |
| **依赖注入** | Hilt |
| **存储** | DataStore Preferences |
| **异步** | Coroutines + Flow |
| **序列化** | KotlinX Serialization |
| **图片加载** | Coil |
| **Markdown** | Compose Richtext |

---

## 快速开始

### PC 端

```powershell
# 1. 克隆项目
git clone https://github.com/huangjw1826/AI-workspace.git
cd AI-workspace

# 2. 安装依赖
.\setup.ps1

# 3. 启动服务
.\start-all.ps1

# 4. 访问应用
# 打开浏览器：http://127.0.0.1:8000
```

### Android 端

1. 使用 Android Studio 打开 `android/` 目录
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 或执行：
   ```bash
   cd android
   ./gradlew installDebug
   ```
5. 配置服务器地址和 API Token（详见 [android/README.md](android/README.md)）

---

## 相关文档

- [README.md](README.md) - 主项目说明
- [CODE_WIKI.md](CODE_WIKI.md) - 代码维基
- [CHANGELOG.md](CHANGELOG.md) - 变更日志
- [android/README.md](android/README.md) - Android 端快速开始
- [docs/android-remote-access.md](docs/android-remote-access.md) - Android 远程访问配置
- [docs/troubleshooting.md](docs/troubleshooting.md) - 故障排查
