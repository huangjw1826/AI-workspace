# AI Recorder Android 2.0 迭代规划

**版本**：v1.0  
**日期**：2026-05-28  
**状态**：规划中  
**目标**：完全同步 Web 前端功能，提供更人性化、更优美的移动端 UI 和交互体验

---

## 目录

1. [迭代目标与设计原则](#1-迭代目标与设计原则)
2. [核心功能变更总览](#2-核心功能变更总览)
3. [录音库 - 移动端适配重构](#3-录音库)
4. [录音详情 - 播放、转写同步、摘要](#4-录音详情)
5. [Markdown 渲染增强](#5-markdown-渲染增强)
6. [操作响应与过渡动画](#6-操作响应与过渡动画)
7. [多设备接入与 Token 管理](#7-多设备接入与-token-管理)
8. [后端变更规划](#8-后端变更规划)
9. [实现阶段与里程碑](#9-实现阶段与里程碑)
10. [文件结构变更](#10-文件结构变更)

---

## 1. 迭代目标与设计原则

### 1.1 核心目标

| 目标 | 说明 |
|------|------|
| **功能同步** | Android 端完全对齐 Web 前端已有的功能，不添加后端不存在的字段/数据 |
| **移动端优先** | 页面布局、交互方式、手势操作均以手机竖屏为第一设计目标 |
| **操作响应提升** | 动画过渡、触觉反馈、加载状态全链路优化 |
| **本地播放同步** | 音频下载到本地后播放，播放进度与转写文本段落高亮同步 |
| **多设备支持** | 支持多个 Android 设备同时接入同一后端，多 Token 管理 |

### 1.2 设计原则

- **不新增后端数据字段**：Android 端只消费已有 API 数据，不做前端端内数据扩充
- **移动端专属交互**：滑动操作、底部 Sheet、长按菜单、下拉刷新等原生手势
- **性能优先**：Compose 高性能列表、懒加载、骨架屏
- **渐进增强**：先保证功能完整可用，再逐轮优化细节

---

## 2. 核心功能变更总览

### 2.1 功能对比

| 功能模块 | Web 前端 | Android 1.0（当前） | Android 2.0（目标） |
|---------|---------|-------------------|-------------------|
| 录音列表 | 表格 + 统计面板 + 筛选 | 基础列表 + 搜索 | 卡片列表 + 统计面板 + 筛选 + 批量操作 |
| 状态显示 | 8 种状态精细映射 | 仅 `已完成/处理中/失败` | 8 种状态完整映射 |
| 搜索筛选 | 文件名 + 标签 + 状态 + 来源 + 排序 | 仅文件名搜索 | 完整搜索 + 多维度筛选 |
| 批量操作 | 批量转写/摘要/删除 | 无 | 批量转写/摘要/删除 |
| 录音详情 | 转写/摘要/信息 三 Tab | 转写/摘要/信息 三 Tab | 增强：播放同步 + 摘要列表 |
| 音频播放 | 浏览器 native | 无 | 本地下载后播放 + 进度与转写同步 |
| 摘要显示 | 折叠/展开 | 折叠/展开 | 列表页 + 独立详情页（不折叠） |
| 转写编辑 | 支持就地编辑 | 无 | 不做（本期暂不实现） |
| 标签管理 | 支持编辑标签 | 无 | 仅显示（来自后端 tags 字段） |
| 导出 | MD/TXT/JSON/SRT/DOCX | MD/TXT | MD/TXT + 复制文本 |
| 目录监控 | 设置 + 事件列表 | 无 | 只读展示 + 事件列表 |
| 设置页 | LLM + 存储 + 监控 | 服务器 + LLM 只读 | 服务器 + LLM 只读 + 存储只读 + 监控只读 |
| 健康面板 | 系统状态 | 基础状态 | 增强状态卡片 |
| SSE 实时推送 | 支持 | 已实现 | 增强事件处理 |
| 多 Token | N/A | 单一 Token | 多 Token 管理 |
| 深色模式 | 无 | 基础 | 系统跟随 |

### 2.2 录音状态完整映射

后端 `Recording.status` 字段有如下状态，Android 2.0 需完整映射：

| 后端 status | 中文显示 | 徽章颜色 | 说明 |
|------------|---------|---------|------|
| `uploaded` | 待转写 | 灰色 `#86909C` | 文件已上传，等待转写 |
| `queued` | 排队中 | 黄色 `#D48806` | 任务已创建，排队等待执行 |
| `normalizing` | 处理中 | 黄色 `#D48806` | 正在音频格式归一化 |
| `transcribing` | 转写中 | 黄色 `#D48806` | 正在语音转文字 |
| `transcribed` | 已转写 | 蓝色 `#1677FF` | 转写完成，等待摘要 |
| `completed` | 已摘要 | 绿色 `#1A8C5B` | 摘要完成，全流程结束 |
| `cancelled` | 已取消 | 灰色 `#86909C` | 任务被用户取消 |
| `error` | 错误 | 红色 `#F5222D` | 处理失败 |

---

## 3. 录音库

### 3.1 整体布局

```
┌──────────────────────────────────────┐
│ 9:41                         🔋 5G   │ 状态栏 54dp
├──────────────────────────────────────┤
│  录音库                        🔄  ⋮ │ 顶栏 52dp
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │ 全部 12  待处理 3  已摘要 8    │  │ 统计卡片横向滚动
│  │ ████████░░  75% 完成率        │  │ MetricCards
│  └────────────────────────────────┘  │
├──────────────────────────────────────┤
│  ┌──🔍 搜索文件名或转写内容───────┐  │ 搜索栏
│  └────────────────────────────────┘  │
│  [全部] [待转写] [处理中] [已摘要]   │ 状态筛选胶囊
│  [上传] [目录监控]        最新优先 ▼ │ 来源筛选 + 排序
├──────────────────────────────────────┤
│  已选择 3 条  [批量转写] [批量摘要]  │ 批量操作栏（条件显示）
│  [🗑 批量删除]                      │
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │ 🎵 产品规划讨论会      [✓ 已摘要] │  │ 录音卡片
│  │ ⏱ 32:15  📦 18.2MB  📅 05-23 │  │
│  │ 来源：上传                     │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 🎵 客户访谈录音        [转写中] │  │
│  │ ⏱ 45:20  📦 25.6MB  📅 05-23 │  │
│  │ 来源：目录监控   ████░░ 35%    │  │ 进度条
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 🎵 个人备忘              [错误] │  │
│  │ ⏱ 05:20  📦 3.1MB  📅 05-22  │  │
│  │ ⚠ 转写失败：音频格式损坏       │  │ 错误信息
│  └────────────────────────────────┘  │
│                                     │
│                          [📤 FAB]   │ 上传按钮 56×56dp
├──────────────────────────────────────┤
│  [📁 录音库]    [📊 监控]   [⚙ 设置] │ 底栏 56dp（移除健康页）
└──────────────────────────────────────┘
```

### 3.2 统计面板

四项指标水平可滑动卡片（与 Web 端一致）：

| 指标 | 数据来源 | 显示 |
|------|---------|------|
| 全部录音 | `recordings.size` | 总数量 + 总大小（`formatSize(totalSize)`） |
| 待处理 | `status == "uploaded" or "queued"` | 数量 + 进度百分比 |
| AI 完成率 | `(completed / total) * 100` | 百分比 + 已摘要数量 |
| 累计时长 | `sum(duration_seconds)` | 格式化时长 + 错误数量 |

### 3.3 筛选交互

- **状态筛选**：水平滚动的胶囊按钮组，多选。激活态底部色条，非激活态灰色边框。
- **来源筛选**：`上传` / `目录监控` 两选一。
- **排序切换**：右上角下拉菜单（最新优先 / 最旧优先 / 时长最长 / 文件最大）。
- **已激活筛选标签**：搜索栏下方显示彩色标签，点击 × 移除单个条件。

### 3.4 列表卡片

每一张录音卡片包含：
- **文件名**（加粗，18sp，单行截断）
- **状态徽章**（右上角，8 种状态颜色映射）
- **时长** + **文件大小** + **日期**（12sp 辅助色）
- **来源类型**：上传 / 目录监控
- **进度条**（仅 `normalizing` / `transcribing` 状态显示）
- **错误信息**（仅 `error` 状态显示，红色文字）

交互：
- **左滑** → 快速删除（带确认 Snackbar + 撤销）
- **长按** → 进入多选模式，卡片左侧出现 Checkbox
- **点击** → 推入详情页（共享元素过渡动画）
- **下拉刷新** → 重新加载列表

### 3.5 批量操作栏

当至少选中 1 条时，列表顶部出现操作栏：
- 已选择 N 条
- 批量转写（对选中录音逐一创建转写任务）
- 批量摘要（对选中录音使用默认模板逐一创建摘要任务）
- 批量删除（确认弹窗，显示选中数量）

### 3.6 空状态

```
          📭
     还没有录音
  点击右下角 📤 上传音频文件
   或通过 PC 端导入
```

---

## 4. 录音详情

### 4.1 整体布局

```
┌──────────────────────────────────────┐
│ 9:41                         🔋 5G   │
├──────────────────────────────────────┤
│  ← 录音详情                   🗑  ⋮  │ 顶栏
├──────────────────────────────────────┤
│  产品规划讨论会.wav                  │ 文件名
│  ⏱ 32:15  📦 18.2MB  [✓ 已摘要]    │ 元数据
├──────────────────────────────────────┤
│  [🎵 播放]  [📝 转写]  [📋 摘要]   │ Tab 栏
│  [ℹ️ 信息]  [📊 任务]              │
├──────────────────────────────────────┤
│                                      │
│   (Tab 内容区域，见下方详细设计)       │
│                                      │
├──────────────────────────────────────┤
│  [📁 录音库]    [📊 监控]   [⚙ 设置] │ 底栏常驻
└──────────────────────────────────────┘
```

### 4.2 播放 Tab

**设计目标**：用户在手机上先下载音频到本地缓存，然后播放音频，播放进度与转写文本段落实时同步高亮。

**下载进度展示在录音详情页整体展示**：
```
┌──────────────────────────────────────┐
│ 9:41                         🔋 5G   │
├──────────────────────────────────────┤
│  ← 录音详情                   🗑  ⋮  │
├──────────────────────────────────────┤
│  产品规划讨论会.wav                  │
│  ⏱ 32:15  📦 18.2MB  [✓ 已摘要]│
├──────────────────────────────────────┤
│  🎵 播放进度: 45%  ────────○─      │ ← 下载进度条（在详情页顶部显示
│     已下载 8.1MB / 18.2MB         │
│     [暂停下载]  [取消]                │
├──────────────────────────────────────┤
│  [🎵 播放]  [📝 转写]  [📋 摘要]   │
│  [ℹ️ 信息]  [📊 任务]              │
├──────────────────────────────────────┤
│  ...                                │
```

**播放器界面（下载完成后）：
```
┌──────────────────────────────────┐
│                                  │
│       ┌──────────────────┐       │
│       │   音频波形/频谱    │       │ 波形可视化
│       │                  │       │
│       └──────────────────┘       │
│                                  │
│     00:45  ──●──────────  32:15 │ 进度条
│                                  │
│    ◁◁    ▶/⏸    ▷▷    1.0x    │ 播放控制
│                                  │
├──────────────────────────────────┤
│  当前段文本高亮（转写段落同步）     │
│  ┌────────────────────────────┐  │
│  │ 00:32  李总：关于Q2的规划    │  │ ← 当前播放段落高亮
│  │ 我建议重点关注AI能力...      │  │   绿色左侧边框 + 浅绿背景
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 00:45  张伟：我补充几点...   │  │ ← 已播放段落（灰色）
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 01:20  王芳：市场部这边...   │  │ ← 未播放段落
│  └────────────────────────────┘  │
│                                  │
└──────────────────────────────────┘
```

**技术实现要点**：
- 使用 **ExoPlayer** (Media3) 作为播放引擎
- 音频缓存到 `context.cacheDir/audio/{recording_id}.{format}`
- 下载支持 Range 请求，利用后端已有的 `GET /api/recordings/{id}/audio` Range 支持
- 播放进度回调（每 100ms）→ 查找当前 `start_time <= position < end_time` 的转写段落 → 高亮该段落 → 自动滚动 LazyColumn 到对应位置
- 点击任意转写段落 → 跳转到该时间点播放
- 下载进度通过 `DownloadProgress` 状态在详情页顶部进度条展示，实时更新

**下载管理**：
```
缓存目录：context.cacheDir/audio/{recording_id}.{format}
缓存策略：保留最近 5 个文件，超出自动清理
下载状态：未下载 / 下载中(进度条+百分比) / 已缓存 / 下载失败
```

### 4.3 转写 Tab

**设计**：
- 按时间戳分段显示（从 API `segments` 字段获取）
- 每段显示：时间戳（`start_time - end_time`）+ 说话人（如果有）+ 文本
- 支持一键复制全部转写文本到剪贴板
- 底部操作栏：复制文本 / 导出 MD / 导出 TXT

**不包含**：就地编辑功能（本期不实现）

### 4.4 摘要 Tab

**设计变更**（关键）：

**摘要列表页**：
```
┌──────────────────────────────────┐
│  摘要列表                        │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ 📋 结构化摘要          [最新] │  │ ← 最新摘要标记
│  │ 生成时间：05-28 14:30       │  │
│  │ 点击查看 →                  │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 📋 会议纪要                  │  │
│  │ 生成时间：05-27 10:15       │  │
│  │ 点击查看 →                  │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 📋 待办事项提取              │  │
│  │ 生成时间：05-26 09:00       │  │
│  │ 点击查看 →                  │  │
│  └────────────────────────────┘  │
│                                  │
│  [+ 生成新摘要]                  │ 底部操作
└──────────────────────────────────┘
```

**摘要命名规则**：
- 标题 = 摘要类型的中文名（从 `SUMMARY_TEMPLATES` 映射）
- 按 `created_at` 从新到旧排序
- 最新的一条标记 `[最新]` 绿色徽章

**摘要详情页**（新页面）：
```
┌──────────────────────────────────┐
│  ← 结构化摘要            🗑  ⋯  │ 顶栏：标题 = 摘要类型名
├──────────────────────────────────┤
│                                  │
│   (增强的 Markdown 渲染内容)       │
│                                  │
├──────────────────────────────────┤
│  [复制文本]    [导出 MD]  [导出TXT]│ 底部操作栏
└──────────────────────────────────┘
```

- 点击摘要列表项 → 推入新的 `SummaryDetailScreen`
- 全屏 Markdown 渲染，使用增强的 Markdown 组件
- 支持删除该摘要
- 支持复制 / 导出

**生成新摘要**：
- 底部按钮 → 弹出摘要模板选择底部 Sheet
- 选择模板后调用 `POST /api/summary/{id}?mode=...`
- 实时接收 SSE 推送的任务进度

### 4.5 信息 Tab

展示录音元数据（只读）：
- 文件名
- 时长（格式化）
- 文件大小（格式化）
- 音频格式
- 来源类型（上传/目录监控）
- 创建时间
- 标签（显示后端 `tags` 字段，逗号分隔展示为标签组）

### 4.6 任务 Tab

展示当前录音关联的任务列表：
- 任务类型（转写/摘要）
- 任务状态
- 进度条（如有）
- 错误信息（如有）
- 创建时间 / 完成时间

---

## 5. Markdown 渲染增强

### 5.1 当前状态

使用 `com.halilibo.compose-richtext` 库，基础渲染能力。

### 5.2 增强方案

**推荐使用 `com.mikepenz:multiplatform-markdown-renderer`**（或保持现有库 + 增强样式配置）：

| 元素 | 增强效果 |
|------|---------|
| H1 标题 | 24sp, Bold, 底部 1dp 分割线 |
| H2 标题 | 20sp, SemiBold, 顶部 16dp 间距 |
| H3 标题 | 18sp, Medium |
| 粗体文本 | 加粗 + 主色高亮（可选） |
| 无序列表 | 圆点图标化，左缩进 8dp |
| 有序列表 | 数字加粗，左缩进 8dp |
| 代码块 | 灰色背景 `#F5F5F5`，等宽字体，圆角 8dp，内边距 12dp |
| 引用块 | 左侧 3dp 绿色竖线，浅绿背景 `#F0FAF5` |
| 分割线 | 细线 `#E5E6EB` |
| 链接 | 绿色可点击，长按复制 URL |
| 表格 | 斑马条纹，表头加粗 |

### 5.3 阅读模式

摘要详情页进入「阅读模式」：
- 隐藏底栏，全屏沉浸
- 顶部只保留返回按钮 + 分享按钮
- 左右滑动手势用于返回（边缘滑动）

---

## 6. 操作响应与过渡动画

### 6.1 动画清单

| 场景 | 动画效果 | 时长 | 缓动 |
|------|---------|------|------|
| 页面推入 | 从右滑入 + 淡入 | 300ms | `FastOutSlowInEasing` |
| 页面返回 | 向右滑出 + 淡出 | 250ms | `FastOutLinearInEasing` |
| 列表项进入 | 从底部淡入 + 上移 | 300ms | `LinearOutSlowInEasing`，stagger(50ms) |
| 列表项删除 | 向右滑出 + 淡出 | 250ms | `FastOutLinearInEasing` |
| 卡片点击 | scale 0.97 + 触觉反馈 | 100ms | Spring(stiffness=500) |
| 下拉刷新 | Material 3 PullRefreshIndicator | — | 系统默认 |
| FAB 点击 | scale 0.92 + 涟漪 | — | 系统默认 |
| 底部 Sheet 弹出 | 从底部滑入 | 300ms | `FastOutSlowInEasing` |
| Toast/Snackbar | 从底部弹入 + 淡出 | 250ms / 200ms | 系统默认 |
| 统计数字变化 | 数字递增动画 | 500ms | `LinearEasing` |
| 进度条 | 平滑增长 | 300ms | `FastOutSlowInEasing` |
| Tab 切换 | 下划线平滑滑动 | 250ms | `FastOutSlowInEasing` |
| 搜索聚焦 | 搜索栏宽度动画 | 200ms | `FastOutSlowInEasing` |

### 6.2 触觉反馈

| 操作 | 反馈强度 |
|------|---------|
| 长按进入多选 | `HapticFeedbackType.LongPress` |
| 左滑删除确认 | `HapticFeedbackType.HeavyClick` |
| 操作成功 | `HapticFeedbackType.Confirm` |
| 操作失败 | `HapticFeedbackType.Reject` |
| 卡片点击 | `HapticFeedbackType.Click` |

### 6.3 加载态

| 状态 | UI |
|------|-----|
| 初始加载 | 骨架屏（4 个卡片占位，呼吸动画） |
| 刷新 | 下拉刷新指示器 |
| 详情加载 | 顶部进度条（`LinearProgressIndicator`） |
| 下载中 | 按钮内进度条 + 百分比 |
| 操作中 | 按钮 loading spinner，置灰其他操作 |

---

## 7. 多设备接入与 Token 管理（仅PC端提供UI）

### 7.1 当前问题

当前 [auth.py](file:///c:/Users/13318/Documents/AI%20workspace/backend/app/api/auth.py) 只支持**单一个** `API_TOKEN`，所有 Android 设备共用一个 Token，无法区分设备来源，无法做访问审计。

### 7.2 后端架构变更（核心变更）

#### 7.2.1 新增数据模型

**1. ApiToken 模型**（`backend/app/models/api_token.py`）：
```python
from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4
from sqlmodel import SQLModel, Field


class ApiToken(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    token: str = Field(index=True, unique=True)  # 32位以上随机字符串
    name: str  # 设备名称 / 标识
    device_info: Optional[str] = None  # JSON格式存储设备信息
    is_active: bool = True
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    last_used_at: Optional[datetime] = None
```

**2. AccessLog 模型**（`backend/app/models/access_log.py`）：
```python
class AccessLog(SQLModel, table=True):
    id: str = Field(default_factory=lambda: str(uuid4()), primary_key=True)
    token_id: Optional[str] = Field(index=True, default=None)  # 关联 ApiToken.id
    device_name: Optional[str] = None  # 冗余字段，便于查询
    method: str
    path: str
    status_code: int
    ip_address: Optional[str] = None
    user_agent: Optional[str] = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
```

#### 7.2.2 新增API（Tokens管理）

**新增路由文件** `backend/app/api/tokens.py`：

| 方法 | 路径 | 说明 | 认证要求 |
|------|------|------|---------|
| `POST` | `/api/tokens` | 创建新 Token，返回完整 Token 值（仅一次） | 需要本地访问或已有有效 Token |
| `GET` | `/api/tokens` | 列出所有 Token（不返回完整 token 值，返回掩码 `xxxx...abcd`） | 仅本地访问 |
| `DELETE` | `/api/tokens/{id}` | 撤销/删除 Token | 仅本地访问 |
| `PATCH` | `/api/tokens/{id}` | 更新 Token 名称/启用状态 | 仅本地访问 |
| `GET` | `/api/tokens/logs` | 查看访问日志（支持分页、按 token_id/时间范围筛选） | 仅本地访问 |

#### 7.2.3 认证中间件升级

**修改** [auth.py](file:///c:/Users/13318/Documents/AI%20workspace/backend/app/api/auth.py)：

```python
# 向后兼容的认证策略：
# 1. 本地访问（localhost/127.0.0.1）无需 Token
# 2. 如果设置了环境变量 API_TOKEN，优先支持旧的单一 Token 认证（向后兼容）
# 3. 如果有 ApiToken 表记录，同时支持多 Token 认证
async def dispatch(self, request, call_next):
    if is_local_request(request):
        return await call_next(request)

    candidate = request.headers.get("X-API-Token", "")
    if not candidate:
        return error_response

    # 策略1: 旧的单一环境变量 Token（向后兼容）
    settings = get_settings()
    if settings.api_token and compare_digest(candidate, settings.api_token):
        return await call_next(request)

    # 策略2: 数据库查询多 Token
    try:
        with Session(get_engine()) as session:
            token_record = session.exec(
                select(ApiToken).where(
                    ApiToken.token == candidate,
                    ApiToken.is_active == True
                )
            ).first()
            
            if token_record:
                # 更新 last_used_at
                token_record.last_used_at = datetime.now(timezone.utc)
                session.add(token_record)
                session.commit()
                
                # 写入访问日志（异步）
                background_tasks.add_task(
                    write_access_log,
                    token_id=token_record.id,
                    device_name=token_record.name,
                    ...
                )
                return await call_next(request)
    except Exception:
        pass

    return error_response
```

#### 7.2.4 数据库迁移策略

1. 首次升级时自动创建 `api_tokens` 和 `access_logs` 表
2. 如果环境变量 `API_TOKEN` 已设置，自动创建一条默认 Token 记录（name="Default Token"）

### 7.3 Android 端交互

- Android 端**不提供** Token 管理 UI
- 仅支持配置单一 Token（与当前一致）
- 所有 Token 创建/管理操作均在 PC 端 Web UI 进行
- 首次连接时，`GET /health` 响应中增加 `auth_required: boolean` 字段，告知客户端是否需要 Token

### 7.4 用户系统准备

日志表 `AccessLog` 的 `token_id` 字段后续可关联 `User` 表，为未来的用户系统做准备：
- 当前阶段：Token = 设备
- 未来阶段：Token → User（一个用户可有多个设备的 Token）

---

## 8. 后端架构变更规划（完整）

### 8.1 后端变更清单（必须优先完成）

#### 8.1.1 新增后端文件

| 文件路径 | 职责 | 优先级 |
|---------|------|--------|
| `backend/app/models/api_token.py` | ApiToken 数据模型（设备访问凭证） | P0 |
| `backend/app/models/access_log.py` | AccessLog 数据模型（访问审计日志） | P0 |
| `backend/app/api/tokens.py` | Token 管理 API（CRUD + 日志查询） | P0 |
| `backend/app/api/watch.py` | 目录监控事件查询 API（已有但需要补全） | P1 |

#### 8.1.2 需要修改的现有后端文件

| 文件 | 变更说明 | 优先级 |
|------|---------|--------|
| [auth.py](file:///c:/Users/13318/Documents/AI%20workspace/backend/app/api/auth.py) | 认证中间件从单 Token → 向后兼容的多 Token 策略 | P0 |
| [health.py](file:///c:/Users/13318/Documents/AI%20workspace/backend/app/api/health.py) | 响应新增 `auth_required` 字段 | P0 |
| [main.py](file:///c:/Users/13318/Documents/AI%20workspace/backend/app/main.py) | 注册 `/api/tokens` 路由、数据库初始化逻辑 | P0 |
| `backend/app/db/__init__.py` | 导入新模型，确保创建表 | P0 |
| `backend/app/config.py` | 可能需要新增配置项 | P1 |

### 8.2 后端API契约补充

#### 8.2.1 已有API无需变更（Android直接使用）

- `/api/recordings`（List/Get/Delete）
- `/api/recordings/{id}/audio`（Range下载，已有）
- `/api/transcribe`（启动转写）
- `/api/summary`（生成摘要）
- `/api/settings/llm`（只读）

#### 8.2.2 Android端需要补充的API

当前 [ApiService.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/data/remote/ApiService.kt) 需要补充：

```kotlin
// 目录监控事件
@GET("/api/watch/events")
suspend fun getWatchEvents(
    @Query("limit") limit: Int = 100,
    @Query("offset") offset: Int = 0
): Response<List<WatchEvent>>

// 存储设置（只读）
@GET("/api/settings/storage")
suspend fun getStorageSettings(): Response<StorageSettings>

// 监控设置（只读）
@GET("/api/settings/watch")
suspend fun getWatchSettings(): Response<WatchSettings>
```

**注意：Token管理相关API Android端不调用，全部在PC端完成。**

### 8.3 数据模型导入与数据库初始化

确保 `backend/app/db/__init__.py` 或等价文件中导入新模型：

```python
from app.models.api_token import ApiToken
from app.models.access_log import AccessLog
```

### 8.4 向后兼容性保障

1. 旧版本环境变量 `API_TOKEN` 继续有效（如设置）
2. 本地访问 `localhost/127.0.0.1` 无需 Token
3. 数据库表结构变更兼容现有表（不破坏现有录音数据）

---

## 9. 实现阶段与里程碑

### Phase 1：录音库全面重构

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 8 种状态精确映射 | P0 | Models.kt 中 `Recording` 增加状态描述方法；UI 徽章颜色映射 |
| 统计面板 MetricCards | P0 | 水平滚动卡片组，四项指标 |
| 筛选系统重构 | P0 | 状态多选 + 来源单选 + 排序下拉 + 已激活标签展示 |
| 录音卡片重新设计 | P0 | 替换当前列表项为卡片式布局 |
| 批量操作栏 | P0 | 长按多选 + 批量转写/摘要/删除 |
| 左滑删除 | P1 | SwipeToDismiss + Snackbar 撤销 |
| 下拉刷新 | P1 | Material 3 PullToRefresh |
| 骨架屏加载 | P1 | 列表加载态 |

### Phase 2：详情页增强

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 播放 Tab + 音频下载 | P0 | ExoPlayer 集成；Range 下载缓存；进度同步 |
| 转写文本与播放同步 | P0 | 高亮当前段落 + 自动滚动 + 点击跳转 |
| 摘要列表页重构 | P0 | 按类型命名 + 按时间排序 + 最新标记 |
| 摘要独立详情页 | P0 | 新页面全屏渲染 Markdown |
| 生成新摘要流程 | P1 | 模板选择 Sheet + SSE 进度追踪 |
| 信息 Tab 增强 | P1 | 标签展示、格式信息 |

### Phase 3：Markdown 与动画

| 任务 | 优先级 | 说明 |
|------|--------|------|
| Markdown 渲染增强 | P0 | 样式增强（标题、列表、代码块、引用、表格） |
| 页面过渡动画 | P0 | NavHost enterTransition/exitTransition |
| 共享元素过渡 | P1 | 列表→详情共享过渡 |
| 触觉反馈集成 | P1 | 关键操作 HapticFeedback |
| 加载态优化 | P1 | 骨架屏、进度条、loading spinner |

### Phase 4：后端架构升级（必须优先于Android端开发）

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 后端 ApiToken 模型 | P0 | 数据库表 + CRUD API 实现 |
| 后端 AccessLog 模型 | P0 | 数据库表 + 访问日志记录中间件 |
| 后端认证中间件升级 | P0 | 向后兼容的多 Token 认证策略 |
| 后端 tokens 路由注册 | P0 | main.py 中新增路由 |
| 健康检查API增强 | P0 | health.py 新增 auth_required 字段 |
| 数据库迁移脚本 | P0 | 自动创建表 + 导入旧Token（如设置环境变量 |
| PC端 Token管理UI | P1 | Web端新增Token管理页面（仅本地访问） |
| 访问日志查看页面 | P1 | PC端新增审计日志查看（仅本地访问） |

### Phase 5：目录监控与健康面板

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 监控事件列表页 | P1 | 从 `/api/watch/events` 加载 |
| 监控设置只读展示 | P1 | 从 `/api/settings/watch` 加载 |
| 存储设置只读展示 | P1 | 从 `/api/settings/storage` 加载 |
| 健康面板增强 | P1 | 状态视觉升级 |

---

## 10. 文件结构变更

### 10.1 新增文件

```
android/app/src/main/java/com/airecorder/android/
├── ui/
│   ├── animation/
│   │   └── PageTransitions.kt          # 页面过渡动画定义
│   ├── components/
│   │   ├── MetricCard.kt               # 统计卡片组件
│   │   ├── MetricCardsRow.kt           # 统计卡片横向滚动组
│   │   ├── FilterChips.kt              # 筛选胶囊组件
│   │   ├── ActiveFilterTags.kt         # 已激活筛选标签
│   │   ├── BatchOperationBar.kt        # 批量操作栏
│   │   ├── AudioPlayerBar.kt           # 音频播放器组件
│   │   ├── DownloadButton.kt           # 音频下载按钮（含进度）
│   │   ├── TranscriptSegmentItem.kt    # 转写段落组件（含高亮）
│   │   ├── SummaryListItem.kt          # 摘要列表项
│   │   ├── SkeletonLoading.kt          # 骨架屏组件
│   │   └── MarkdownContent.kt          # 增强 Markdown 渲染
│   ├── screens/
│   │   ├── detail/
│   │   │   ├── DetailScreen.kt         # (重构) 详情主页
│   │   │   ├── PlayTab.kt              # 播放 Tab
│   │   │   ├── TranscriptTab.kt        # 转写 Tab
│   │   │   ├── SummaryTab.kt           # 摘要列表 Tab
│   │   │   ├── SummaryDetailScreen.kt  # 摘要独立详情页
│   │   │   ├── InfoTab.kt              # 信息 Tab
│   │   │   ├── TaskTab.kt              # 任务 Tab
│   │   │   └── DetailViewModel.kt      # (增强)
│   │   ├── library/
│   │   │   ├── LibraryScreen.kt        # (重构)
│   │   │   ├── FilterBottomSheet.kt    # 筛选底部 Sheet
│   │   │   └── LibraryViewModel.kt     # (增强)
│   │   ├── watch/
│   │   │   ├── WatchScreen.kt          # 目录监控页
│   │   │   └── WatchViewModel.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt       # (增强)
│   │       └── SettingsViewModel.kt    # (增强)
│   └── theme/
│       └── Animation.kt               # 动画规格定义
├── data/
│   ├── local/
│   │   └── AudioCacheManager.kt        # 音频本地缓存管理
│   └── model/
│       └── WatchEvent.kt               # 监控事件数据模型
└── util/
    └── AudioUtils.kt                   # 音频工具
```

### 10.2 需要重构的现有文件

| 文件路径 | 变更幅度 | 说明 |
|---------|---------|------|
| [LibraryScreen.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/screens/LibraryScreen.kt) | 完全重写 | 卡片式列表 + 统计面板 + 筛选 + 批量操作 |
| [LibraryViewModel.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/screens/LibraryViewModel.kt) | 大幅增强 | 筛选状态 + 批量选择 + 排序 |
| [DetailScreen.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/screens/DetailScreen.kt) | 大幅重构 | 新增播放 Tab + 摘要列表 + 移除编辑 |
| [DetailViewModel.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/screens/DetailViewModel.kt) | 增强 | 播放状态 + 音频缓存状态（详情页顶部展示下载进度） |
| [SettingsScreen.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/screens/SettingsScreen.kt) | 增强 | 存储/监控设置只读展示 |
| [Models.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/data/model/Models.kt) | 补充 | WatchEvent |
| [ApiService.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/data/remote/ApiService.kt) | 补充 | 监控事件 + 设置只读API |
| [Theme.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/theme/Theme.kt) | 增强 | 状态颜色 + Markdown 颜色 |
| [NavDestinations.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/navigation/NavDestinations.kt) | 补充 | 新页面路由 |
| [BottomNavigationBar.kt](file:///c:/Users/13318/Documents/AI%20workspace/android/app/src/main/java/com/airecorder/android/ui/components/BottomNavigationBar.kt) | 修改 | 新增监控 Tab |
| [build.gradle.kts](file:///c:/Users/13318/Documents/AI%20workspace/android/app/build.gradle.kts) | 补充 | ExoPlayer、增强 Markdown 依赖 |

### 10.3 后端新增文件

```
backend/app/
├── models/
│   ├── api_token.py          # ApiToken 数据模型
│   └── access_log.py         # AccessLog 数据模型
└── api/
    └── tokens.py             # Token 管理 API（CRUD + 日志）
```

---

## 附录 A：状态映射实现参考

```kotlin
// Recording 扩展
val Recording.statusLabel: String
    get() = when (status) {
        "uploaded" -> "待转写"
        "queued" -> "排队中"
        "normalizing" -> "处理中"
        "transcribing" -> "转写中"
        "transcribed" -> "已转写"
        "completed" -> "已摘要"
        "cancelled" -> "已取消"
        "error" -> "错误"
        else -> status
    }

val Recording.statusColor: Color
    get() = when (status) {
        "completed" -> CompletedGreen
        "transcribed" -> TranscribedBlue
        "uploaded", "cancelled" -> NeutralGray
        "queued", "normalizing", "transcribing" -> ProcessingAmber
        "error" -> ErrorRed
        else -> NeutralGray
    }
```

## 附录 B：摘要类型映射

```kotlin
// 从 SUMMARY_TEMPLATES API 返回的模板
// 后端目前支持的模板（来自 summary_service.py）：
// structured_summary → 结构化摘要
// meeting_minutes → 会议纪要
// todo_extraction → 待办事项

val Summary.displayTitle: String
    get() = when (mode) {
        "structured_summary" -> "结构化摘要"
        "meeting_minutes" -> "会议纪要"
        "todo_extraction" -> "待办事项提取"
        else -> mode
    }
```

---

## 附录 C：音频缓存管理

```kotlin
// AudioCacheManager.kt
class AudioCacheManager(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "audio")

    fun getCachedPath(recordingId: String): File?
    fun cacheAudio(recordingId: String, format: String): File
    fun clearOldCache(maxFiles: Int = 5)
    fun getCacheSize(recordingId: String): Long
}
```

---

*本文档为 Android 2.0 迭代的完整规划，后续实现将严格按照此文档执行。*
