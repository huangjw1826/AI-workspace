# AI Recorder Android 2.0 - 产品需求文档

## 概述
- **Summary**：Android 端完整重构，对齐 Web 前端功能，提供更优美的移动端体验
- **Purpose**：将现有的基础 Android 应用升级为与 Web 端功能完全对齐的高质量移动端录音管理应用
- **Target Users**：使用 AI Recorder 的移动用户

## 目标
- 完全同步 Web 前端功能（录音列表、详情、筛选、批量操作等）
- 实现完整的 8 种状态映射（待转写/排队中/处理中/转写中/已转写/已摘要/已取消/错误）
- 添加本地音频播放功能与转写文本高亮同步
- 重构摘要展示为列表+独立详情页
- 添加动画过渡与触觉反馈优化
- 后端多 Token 支持（Android 仅使用，管理在 PC 端）

## 非目标（范围外）
- 移动端转写编辑功能（本期暂不实现）
- Android 端 Token 管理界面（仅在 PC 端）
- 新增后端未提供的字段或数据
- 深色模式以外的主题系统

## 背景与上下文
当前 Android 端为基础版本，仅支持简单的录音列表和详情展示。Web 端已有完整的功能，包括统计面板、高级筛选、批量操作等。需要将这些功能迁移到移动端，并针对手机体验进行优化。

## 功能需求
- **FR1**：录音库完整重设计（统计面板、筛选、批量操作、卡片列表）
- **FR2**：录音详情页增强（播放 Tab、摘要列表、下载进度展示）
- **FR3**：本地音频播放与转写段落同步高亮
- **FR4**：Markdown 渲染增强
- **FR5**：后端多 Token 支持与访问日志

## 非功能需求
- **NFR1**：流畅的动画与过渡效果（<300ms）
- **NFR2**：高性能列表加载（骨架屏 + 懒加载）
- **NFR3**：向后兼容（不破坏现有数据）

## 约束
- **Technical**：Kotlin + Jetpack Compose，后端为 Python FastAPI
- **Dependencies**：ExoPlayer (Media3) 用于音频播放

## 假设
- 现有后端 API 无需大改，仅需新增多 Token 支持
- Android 用户使用 1-2 个设备接入同一后端

## 验收标准

### AC1：录音库状态完整映射
- **Given**：用户打开录音库
- **When**：展示所有录音
- **Then**：8种状态（uploaded/queued/normalizing/transcribing/transcribed/completed/cancelled/error）正确映射显示
- **Verification**：programmatic
- **Notes**：每种状态有对应颜色与标签

### AC2：录音库统计面板显示
- **Given**：用户打开录音库
- **When**：加载完成
- **Then**：显示四项指标（全部录音/待处理/AI完成率/累计时长）
- **Verification**：programmatic

### AC3：录音库筛选与排序
- **Given**：用户在录音库页面
- **When**：使用筛选和排序
- **Then**：状态/来源筛选、排序正确生效
- **Verification**：programmatic

### AC4：录音列表卡片展示
- **Given**：用户在录音库
- **When**：查看列表
- **Then**：卡片显示文件名、状态、时长、大小、日期、来源，有进度条的显示进度
- **Verification**：human-judgment

### AC5：批量操作
- **Given**：用户长按录音卡片
- **When**：选中多个录音并执行批量操作
- **Then**：批量转写/摘要/删除正确执行
- **Verification**：programmatic

### AC6：详情页下载进度展示
- **Given**：用户在录音详情页
- **When**：下载音频
- **Then**：详情页顶部显示下载进度条与百分比
- **Verification**：human-judgment

### AC7：本地音频播放与转写同步
- **Given**：音频已下载
- **When**：播放音频
- **Then**：播放进度与转写段落高亮同步
- **Verification**：human-judgment

### AC8：摘要列表与详情页
- **Given**：用户在录音详情的摘要 Tab
- **When**：点击摘要列表项
- **Then**：推入独立的摘要详情页全屏展示
- **Verification**：human-judgment

### AC9：后端多 Token 支持
- **Given**：用户在 PC 端
- **When**：管理 Token
- **Then**：可以创建、查看、删除 Token
- **Verification**：programmatic

### AC10：后端向后兼容
- **Given**：旧版本环境变量设置
- **When**：升级到新版本
- **Then**：旧环境变量 Token 继续有效
- **Verification**：programmatic

## 未决问题
- [ ] Markdown 渲染库选用：保持现有库还是换用 mikepenz 库？
