# AI Recorder 更改日志

## 2026-04-25 优化批次

本次改动聚焦"最优先的 5 个问题"，未改动业务核心逻辑，仅做健壮性与可维护性增强。

### 1. 前端依赖版本锁定
- **文件**: `frontend/package.json`
- **改动**: 将所有 `"latest"` 替换为实际安装的固定版本号
  - `react` / `react-dom`: `^19.2.5`
  - `lucide-react`: `^0.487.0`
  - `@types/react`: `^19.2.14`
  - `@types/react-dom`: `^19.2.3`
  - `@vitejs/plugin-react`: `^6.0.1`
  - `typescript`: `^6.0.3`
  - `vite`: `^8.0.10`
- **原因**: 避免未来某个依赖发布大版本更新后，项目突然无法构建。
- **额外调整**: 将 `vite`、`typescript`、`@vitejs/plugin-react` 从 `dependencies` 移至 `devDependencies`，生产构建时不再安装这些开发工具。

### 2. 后端上传接口增加文件大小限制
- **文件**: `backend/app/api/recordings.py`
- **改动**:
  - 新增常量 `MAX_UPLOAD_SIZE = 500 * 1024 * 1024`（500 MB）
  - 上传时先读取完整内容并检查大小，超限返回 `413` 错误及中文提示
  - 文件格式不支持时返回中文提示
- **原因**: 防止用户误上传超大文件导致后端内存被打满或卡死。

### 3. 新增删除录音功能
- **文件**:
  - `backend/app/api/recordings.py` — 新增 `DELETE /api/recordings/{recording_id}` 接口
  - `frontend/src/lib/api.ts` — 新增 `deleteRecording()` 调用
  - `frontend/src/App.tsx` — 新增删除按钮与确认对话框
  - `frontend/src/styles.css` — 新增删除按钮样式
- **后端行为**:
  - 删除数据库中的录音记录
  - 级联删除关联的转写片段 (`TranscriptSegment`)、摘要 (`Summary`)、任务 (`Task`)
  - 尝试删除硬盘上的原始音频、标准化音频、转写 JSON、摘要 Markdown（删除失败不影响数据库事务）
- **前端行为**:
  - 每个录音条目右侧显示垃圾桶图标
  - 点击后弹出浏览器确认框：`"确定要删除这条录音吗？转写结果和摘要也会一并删除。"`
  - 删除成功后自动刷新列表；若删除的是当前选中录音，则清空右侧详情区

### 4. 前端代码结构拆分
- **文件**:
  - 新增 `frontend/src/App.tsx` — 包含全部应用状态、业务逻辑与 JSX
  - 重写 `frontend/src/main.tsx` — 仅保留 React 根节点挂载代码
- **拆分前**: 所有逻辑与渲染挤在 `main.tsx`（约 420 行）
- **拆分后**:
  - `main.tsx` → 入口渲染（5 行）
  - `App.tsx` → 业务主组件（约 350 行）
- **好处**: 职责分离，后续想改界面找 `App.tsx`，想改入口配置找 `main.tsx`，不需要在几百行代码里翻找。

### 5. 环境配置模板已就绪（无需修改）
- **文件**: `backend/.env.example`
- **状态**: 文件已存在且字段完整，包含 ASR、LLM、路径、CORS 等全部配置项
- **说明**: 本次未做改动，因为该文件在项目中已经存在。

---

## 待后续考虑的优化（未在本次处理）

- 任务状态恢复机制（后端重启后恢复正在运行的任务）
- 日志自动轮转（防止日志无限增长）
- 前端引入 Tailwind CSS / shadcn/ui 提升 UI 一致性与开发效率
- 录音导出功能（TXT / Markdown / JSON）
- 搜索与标签筛选
- 文字同步播放（点击转写文本跳转对应音频时间）
