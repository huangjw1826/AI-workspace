# AI Recorder Android 2.0 - 实施计划（分拆并按优先级排序任务）

## [x] Task 20：build.gradle.kts 依赖更新（P0，优先执行）
- **Priority**：P0
- **Depends On**：None
- **Description**：补充 ExoPlayer 等所需依赖
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `programmatic` TR20.1：Gradle sync 成功
  - `programmatic` TR20.2：项目编译通过
- **Notes**：此任务应该放在前面执行

## [x] Task 1：后端多 Token 模型与认证中间件（P0）
- **Priority**：P0
- **Depends On**：None
- **Description**：新增 ApiToken 和 AccessLog 数据模型，升级 auth 中间件支持多 Token 和旧环境变量 Token
- **Acceptance Criteria Addressed**：AC9, AC10
- **Test Requirements**：
  - `programmatic` TR1.1：ApiToken 和 AccessLog 模型能正确持久化
  - `programmatic` TR1.2：认证中间件同时支持旧环境变量 Token 和数据库 Token
  - `programmatic` TR1.3：本地访问无需 Token
- **Notes**：向后兼容为最高优先级

## [x] Task 2：后端 Token API 路由（P0）
- **Priority**：P0
- **Depends On**：Task1
- **Description**：新增 /api/tokens 路由（POST/GET/DELETE/PATCH）和 /api/tokens/logs
- **Acceptance Criteria Addressed**：AC9
- **Test Requirements**：
  - `programmatic` TR2.1：GET /api/tokens 返回所有 Token（掩码显示）
  - `programmatic` TR2.2：POST /api/tokens 创建新 Token 并返回完整值（仅一次）
  - `programmatic` TR2.3：DELETE /api/tokens/{id} 正确撤销 Token
- **Notes**：仅本地访问或已有有效 Token 可调用

## [x] Task 3：后端健康 API 增强与路由注册（P0）
- **Priority**：P0
- **Depends On**：Task2
- **Description**：health.py 新增 auth_required 字段，main.py 注册新路由
- **Acceptance Criteria Addressed**：AC9
- **Test Requirements**：
  - `programmatic` TR3.1：GET /health 返回 auth_required 字段
  - `programmatic` TR3.2：新路由正确注册且可访问
- **Notes**：

## [x] Task 4：Models.kt 补充与状态映射（P0）
- **Priority**：P0
- **Depends On**：None
- **Description**：Models.kt 新增 8 种状态映射的扩展函数，补充 WatchEvent 数据模型
- **Acceptance Criteria Addressed**：AC1
- **Test Requirements**：
  - `programmatic` TR4.1：所有 8 种后端状态正确映射为中文标签
  - `programmatic` TR4.2：状态颜色正确对应
- **Notes**：参考规划文档附录 A

## [x] Task 5：录音库统计面板组件（P0）
- **Priority**：P0
- **Depends On**：Task4
- **Description**：新增 MetricCard 和 MetricCardsRow 组件，统计四项指标
- **Acceptance Criteria Addressed**：AC2
- **Test Requirements**：
  - `human-judgment` TR5.1：统计面板水平滑动展示四项指标
  - `human-judgment` TR5.2：指标数字变化有动画效果
- **Notes**：

## [x] Task 6：录音库筛选与排序系统（P0）
- **Priority**：P0
- **Depends On**：Task4
- **Description**：新增 FilterChips、ActiveFilterTags 组件，LibraryViewModel 增强筛选状态管理
- **Acceptance Criteria Addressed**：AC3
- **Test Requirements**：
  - `programmatic` TR6.1：状态筛选多选正确生效
  - `programmatic` TR6.2：来源筛选和排序正确生效
  - `human-judgment` TR6.3：已激活筛选标签可点击移除
- **Notes**：

## [x] Task 7：录音列表卡片重构与多选（P0）
- **Priority**：P0
- **Depends On**：Task6
- **Description**：完全重构 LibraryScreen，卡片式列表，支持长按多选，新增 BatchOperationBar
- **Acceptance Criteria Addressed**：AC4, AC5
- **Test Requirements**：
  - `human-judgment` TR7.1：录音卡片正确展示文件名、状态、时长、大小、日期、来源
  - `human-judgment` TR7.2：左滑删除有确认 Snackbar，支持撤销
  - `programmatic` TR7.3：多选后批量操作正确执行
  - `human-judgment` TR7.4：有进度条的状态正确显示进度条
- **Notes**：

## [x] Task 8：骨架屏与下拉刷新（P1）
- **Priority**：P1
- **Depends On**：Task7
- **Description**：新增 SkeletonLoading 组件，添加下拉刷新
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `human-judgment` TR8.1：初始加载有骨架屏
  - `human-judgment` TR8.2：下拉刷新动画流畅
- **Notes**：

## [x] Task 9：详情页顶部下载进度展示（P0）
- **Priority**：P0
- **Depends On**：None
- **Description**：DetailViewModel 增强下载状态管理，详情页顶部显示下载进度条
- **Acceptance Criteria Addressed**：AC6
- **Test Requirements**：
  - `human-judgment` TR9.1：下载进度条实时更新
  - `human-judgment` TR9.2：暂停/取消按钮功能正常
- **Notes**：

## [x] Task 10：音频缓存与下载管理（P0）
- **Priority**：P0
- **Depends On**：Task9
- **Description**：新增 AudioCacheManager 与 AudioUtils，实现 Range 下载与缓存管理
- **Acceptance Criteria Addressed**：AC6, AC7
- **Test Requirements**：
  - `programmatic` TR10.1：音频文件正确下载到缓存目录
  - `programmatic` TR10.2：旧缓存文件自动清理（保留最近 5 个）
- **Notes**：缓存目录：context.cacheDir/audio/

## [x] Task 11：ExoPlayer 集成与播放 Tab（P0）
- **Priority**：P0
- **Depends On**：Task10
- **Description**：新增 PlayTab 与 AudioPlayerBar 组件，集成 ExoPlayer
- **Acceptance Criteria Addressed**：AC7
- **Test Requirements**：
  - `human-judgment` TR11.1：音频播放功能正常
  - `human-judgment` TR11.2：播放控制按钮工作（播放/暂停/快进/快退/倍速）
- **Notes**：

## [x] Task 12：播放进度与转写段落同步（P0）
- **Priority**：P0
- **Depends On**：Task11
- **Description**：新增 TranscriptSegmentItem 组件，实现播放进度与段落高亮同步
- **Acceptance Criteria Addressed**：AC7
- **Test Requirements**：
  - `human-judgment` TR12.1：播放时当前段落高亮
  - `human-judgment` TR12.2：点击段落跳转到对应播放时间
  - `human-judgment` TR12.3：已播放段落变灰色
- **Notes**：

## [x] Task 13：摘要列表页重构（P0）
- **Priority**：P0
- **Depends On**：None
- **Description**：SummaryTab 重构为摘要列表，按类型命名，最新标记
- **Acceptance Criteria Addressed**：AC8
- **Test Requirements**：
  - `human-judgment` TR13.1：摘要标题使用类型中文名
  - `human-judgment` TR13.2：按创建时间从新到旧排序
  - `human-judgment` TR13.3：最新摘要有绿色「最新」标记
- **Notes**：参考规划文档附录 B

## [x] Task 14：摘要详情独立页面（P0）
- **Priority**：P0
- **Depends On**：Task13
- **Description**：新增 SummaryDetailScreen，新增导航路由
- **Acceptance Criteria Addressed**：AC8
- **Test Requirements**：
  - `human-judgment` TR14.1：点击摘要列表项推入独立详情页
  - `human-judgment` TR14.2：全屏展示摘要内容
  - `human-judgment` TR14.3：底部操作栏复制/导出功能正常
- **Notes**：

## [x] Task 15：Markdown 渲染增强（P0）
- **Priority**：P0
- **Depends On**：Task14
- **Description**：新增 MarkdownContent 组件，增强标题、列表、代码块、引用、表格样式
- **Acceptance Criteria Addressed**：AC8
- **Test Requirements**：
  - `human-judgment` TR15.1：标题层级清晰
  - `human-judgment` TR15.2：代码块有背景色和圆角
  - `human-judgment` TR15.3：引用块有左侧竖线
- **Notes**：可以继续使用现有库或换用 mikepenz

## [x] Task 16：页面过渡动画与主题动画（P1）
- **Priority**：P1
- **Depends On**：None
- **Description**：新增 PageTransitions.kt 与 theme/Animation.kt，实现页面推入/返回动画
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `human-judgment` TR16.1：页面推入从右侧滑入
  - `human-judgment` TR16.2：页面返回向右侧滑出
  - `human-judgment` TR16.3：动画流畅不卡顿
- **Notes**：

## [x] Task 17：触觉反馈集成（P1）
- **Priority**：P1
- **Depends On**：None
- **Description**：关键操作（长按/删除/成功/失败）添加触觉反馈
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `human-judgment` TR17.1：长按多选有触觉反馈
  - `human-judgment` TR17.2：操作成功/失败有反馈
- **Notes**：

## [x] Task 18：WatchScreen 目录监控页面（P1）
- **Priority**：P1
- **Depends On**：Task3
- **Description**：新增 WatchScreen 与 WatchViewModel，展示监控事件列表，补充 ApiService
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `programmatic` TR18.1：/api/watch/events API 正确调用
  - `human-judgment` TR18.2：监控事件列表正确展示
- **Notes**：

## [x] Task 19：SettingsScreen 增强（P1）
- **Priority**：P1
- **Depends On**：Task3
- **Description**：SettingsScreen 添加存储/监控设置只读展示
- **Acceptance Criteria Addressed**：
- **Test Requirements**：
  - `human-judgment` TR19.1：存储设置只读展示
  - `human-judgment` TR19.2：监控设置只读展示
- **Notes**：不包含 Token 管理界面
