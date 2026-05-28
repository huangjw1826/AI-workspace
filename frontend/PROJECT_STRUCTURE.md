# AI Recorder 前端项目结构说明

## 📋 目录

- [项目概述](#项目概述)
- [目录结构](#目录结构)
- [核心模块说明](#核心模块说明)
- [页面组件](#页面组件)
- [API 客户端](#api-客户端)
- [技术栈](#技术栈)

---

## 项目概述

AI Recorder 前端是一个 React + TypeScript + Vite 应用，提供：
- 录音库管理
- 音频播放与时间轴联动
- 转写与摘要查看/编辑
- 文件导出
- 目录监控配置
- 系统设置

---

## 目录结构

```
frontend/
├── src/
│   ├── components/                  # 可复用组件
│   │   ├── feedback/               # 反馈组件
│   │   │   ├── ToastStack.tsx      # Toast 消息栈
│   │   │   └── ConfirmDialog.tsx   # 确认对话框
│   │   ├── layout/                 # 布局组件
│   │   │   └── NavBar.tsx          # 导航栏
│   │   ├── markdown/               # Markdown 组件
│   │   │   └── MarkdownView.tsx    # Markdown 渲染
│   │   ├── recording/              # 录音相关组件
│   │   │   ├── RecordingDetailPanel.tsx  # 录音详情面板
│   │   │   ├── SummaryCard.tsx     # 摘要卡片
│   │   │   └── MetricCard.tsx      # 指标卡片
│   │   ├── ui/                     # UI 基础组件
│   │   │   ├── FolderPicker.tsx    # 文件夹选择器
│   │   │   ├── ExportButtons.tsx   # 导出按钮
│   │   │   ├── SettingsSection.tsx # 设置区块
│   │   │   ├── InfoCard.tsx        # 信息卡片
│   │   │   └── StatusBadge.tsx     # 状态徽章
│   ├── hooks/                       # 自定义 Hooks
│   │   ├── useRecordings.ts        # 录音数据管理 Hook
│   │   └── useSSE.ts               # SSE 事件订阅 Hook
│   ├── lib/                        # 工具函数和库
│   │   ├── api.ts                 # API 客户端
│   │   ├── types.ts               # TypeScript 类型定义
│   │   ├── viewTypes.ts           # 视图类型定义
│   │   └── format.ts              # 格式化工具
│   ├── pages/                      # 页面组件
│   │   ├── HealthPage.tsx         # 健康检查页面
│   │   ├── LibraryPage.tsx        # 录音库页面
│   │   ├── SettingsPage.tsx       # 设置页面
│   │   └── WatchPage.tsx          # 目录监控页面
│   ├── stores/                     # 状态管理
│   │   ├── appStore.ts            # 应用全局状态
│   │   └── taskStore.ts           # 任务状态管理
│   ├── App.tsx                    # 应用主组件
│   ├── main.tsx                   # 应用入口
│   └── styles.css                 # 全局样式
├── index.html                     # HTML 模板
├── package.json                   # 项目依赖
├── tsconfig.json                  # TypeScript 配置
├── vite.config.mjs                # Vite 构建配置
└── build.ps1                      # 前端构建脚本
```

---

## 核心模块说明

### 1. 页面组件 (`pages/`)

| 页面 | 功能 |
|------|------|
| **LibraryPage** | 录音库首页，显示录音列表、搜索、批量操作 |
| **WatchPage** | 目录监控页面，配置监控目录、查看监控事件 |
| **SettingsPage** | 设置页面，配置存储路径、LLM、转写等 |
| **HealthPage** | 健康检查页面，显示系统状态、日志 |

---

### 2. 可复用组件 (`components/`)

#### feedback/（反馈组件）
- **ToastStack.tsx**：管理 Toast 消息队列
- **ConfirmDialog.tsx**：通用确认对话框

#### layout/（布局组件）
- **NavBar.tsx**：顶部导航栏，包含应用标题

#### markdown/（Markdown 组件）
- **MarkdownView.tsx**：Markdown 内容渲染

#### recording/（录音组件）
- **RecordingDetailPanel.tsx**：录音详情侧边栏
  - 音频播放器
  - 转写片段列表（可点击跳转播放位置）
  - 转写编辑
  - 摘要列表
  - 录音元数据
  - 操作按钮
- **SummaryCard.tsx**：单个摘要卡片
- **MetricCard.tsx**：指标统计卡片

#### ui/（UI 组件）
- **FolderPicker.tsx**：文件夹选择器
- **ExportButtons.tsx**：导出按钮组
- **SettingsSection.tsx**：设置区块组件
- **InfoCard.tsx**：信息卡片
- **StatusBadge.tsx**：状态徽章

---

### 3. 工具库 (`lib/`)

#### api.ts
API 客户端，封装所有后端 API 调用：
```typescript
// 录音
getRecordings()
getRecording()
uploadRecording()
deleteRecording()
updateRecordingTags()
updateTranscriptSegment()
exportTranscript()
exportSummary()

// 转写/摘要
startTranscription()
startSummary()
getSummaryTemplates()
getTask()
cancelTask()

// 批量操作
batchDelete()
batchTranscribe()
batchSummarize()

// 设置
getSettings()
updateSettings()
getLLMSettings()
getWatchSettings()
getStorageSettings()

// 监控
getWatchEvents()
triggerWatchScan()

// 健康
getHealth()

// 文件系统
pickFolder()
```

#### types.ts
TypeScript 类型定义：
- `Recording`：录音信息
- `TranscriptSegment`：转写片段
- `Summary`：摘要
- `Task`：任务
- `WatchEvent`：监控事件
- `Settings`：设置
- `HealthResponse`：健康检查响应
- 等等

#### format.ts
格式化工具函数：
- `formatDuration()`：格式化时长
- `formatFileSize()`：格式化文件大小
- `formatDate()`：格式化日期

---

## 页面组件详解

### LibraryPage（录音库）
主要功能：
1. 录音列表展示
2. 搜索与筛选
   - 文本搜索（文件名、标签、转写、摘要）
   - 状态筛选
   - 标签筛选
3. 批量操作
   - 批量选择
   - 批量转写
   - 批量摘要
   - 批量删除
4. 上传录音
5. 点击录音打开详情面板

### RecordingDetailPanel（录音详情）
主要功能：
1. 音频播放器
   - 原生 audio 元素
   - 播放/暂停、进度条
   - 音量控制
2. 转写 Tab
   - 转写片段列表
   - 时间戳显示
   - 点击跳转到对应播放位置
   - 编辑片段文本
   - 导出转写
3. 摘要 Tab
   - 摘要列表（支持多个）
   - 触发新摘要
   - 删除摘要
   - 导出摘要
4. 信息 Tab
   - 录音元数据
   - 文件信息
5. 任务进度（进行中时）

### SettingsPage（设置）
主要功能：
1. 存储设置
   - 录音保存目录
   - 转写保存目录
   - 摘要保存目录
2. 转写设置
   - ASR 模型选择
   - 并发数限制
3. LLM 设置
   - 提供商选择（DeepSeek / 通义千问 / MiMo）
   - API Key 配置
   - 模型选择
   - 其他参数
4. 目录监控设置
   - 启用/禁用
   - 监控目录
   - 扫描间隔

---

## 技术栈

| 技术 | 说明 |
|------|------|
| **React 19** | UI 框架 |
| **TypeScript** | 类型安全 |
| **Vite** | 构建工具 |
| **Lucide React** | 图标库 |
| **(无 UI 框架)** | 原生 CSS（可后续添加 Tailwind / shadcn/ui） |

---

## 状态管理

App.tsx 中使用 React useState 管理全局状态：
- `recordings`：录音列表
- `selectedRecordingId`：当前选中的录音
- `tasks`：进行中的任务
- `toasts`：Toast 消息
- 等等

---

## 相关文档

- [../PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md) - 整体项目结构
- [../CODE_WIKI.md](../CODE_WIKI.md) - 代码维基
