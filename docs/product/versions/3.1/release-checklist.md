# AI Recorder 3.1 版本发布检查清单

## 发布前检查

### 代码质量

- [ ] 所有代码通过 ESLint / flake8 检查
- [ ] 无 TODO / FIXME / FIXME 注释遗留
- [ ] 敏感信息（API Key、Token）已清除
- [ ] 注释已更新或移除

### 功能验证

#### SSE 实时推送
- [ ] 创建任务后 SSE 连接建立
- [ ] 任务进度变化时 Toast 正确显示
- [ ] 任务完成时 Toast 通知正确
- [ ] 断线后自动重连成功
- [ ] 同时只有一个 SSE 连接活跃

#### URL 路由
- [ ] `/` 录音库页面正常加载
- [ ] `/recording/:id` 录音详情页正常加载
- [ ] `/watch` 目录监控页面正常加载
- [ ] `/settings` 设置页面正常加载
- [ ] `/health` 健康面板页面正常加载
- [ ] 页面刷新保持当前路由
- [ ] 浏览器前进/后退正常
- [ ] `document.title` 随路由正确更新

#### Android DeepLink
- [ ] `airecorder://recording/{id}` 正确跳转
- [ ] `airecorder://settings` 正确跳转
- [ ] 无效 DeepLink 正确处理

#### 说话人分离
- [ ] 功能默认开启
- [ ] 开启时转写结果包含说话人标签
- [ ] 关闭时不影响原转写流程
- [ ] 设置页标注为「实验性功能」

### 安全检查

- [ ] Rate Limiting 生效
- [ ] CORS 配置正确
- [ ] 请求 ID 追踪正常
- [ ] 敏感操作有审计日志
- [ ] 日志中无敏感信息明文
- [ ] 无 SQL 注入风险
- [ ] 无 XSS 风险

### 兼容性测试

#### 浏览器
- [ ] Chrome 90+ 测试通过
- [ ] Firefox 88+ 测试通过
- [ ] Edge 90+ 测试通过
- [ ] Safari 14+ 测试通过

#### Android
- [ ] Android 8.0+ 测试通过
- [ ] Jetpack Compose 兼容性正常

### 文档更新

- [ ] CHANGELOG.md 已更新
- [ ] API 文档已更新（包含 SSE 端点）
- [ ] 用户手册已更新（新功能说明）
- [ ] 开发指南已更新（环境配置）

### 部署验证

- [ ] 数据库迁移脚本验证通过
- [ ] `alembic upgrade head` 成功
- [ ] `alembic downgrade -1` 成功（回滚验证）
- [ ] Caddy 配置已更新（History 模式支持）
- [ ] 一键部署脚本验证通过

### 性能基准

- [ ] 首页加载时间 < 2s
- [ ] SSE 连接建立时间 < 500ms
- [ ] 任务状态推送延迟 < 500ms

---

## 发布执行

### 版本号确认

当前版本: `v3.0.x` → 发布版本: `v3.1.0`

### 发布步骤

```bash
# 1. 创建 release 分支
git checkout -b release/v3.1.0

# 2. 更新版本号和 CHANGELOG
# 编辑 CHANGELOG.md

# 3. 提交并推送到远程
git add .
git commit -m "chore: prepare release v3.1.0"
git push origin release/v3.1.0

# 4. 合并到 main/master
git checkout main
git merge release/v3.1.0 --no-ff
git tag v3.1.0
git push origin main --tags

# 5. 删除 release 分支（可选）
git branch -d release/v3.1.0
git push origin --delete release/v3.1.0
```

### 回滚方案

如发布后出现问题：

```bash
# 回滚到上一版本
git revert <commit_hash>
git push origin main

# 或者从 tag 重新部署
git checkout v3.0.x
# 重新部署 v3.0.x
```

---

## 发布后检查

- [ ] 生产环境功能验证通过
- [ ] 监控告警正常
- [ ] 日志正常输出
- [ ] 用户反馈收集渠道畅通
- [ ] 文档已同步到在线文档（如果适用）

---

## 负责人签字

| 检查项 | 负责人 | 日期 | 签字 |
|--------|--------|------|------|
| 代码质量 | | | |
| 功能验证 | | | |
| 安全检查 | | | |
| 兼容性测试 | | | |
| 文档更新 | | | |
| 部署验证 | | | |
| 正式发布 | | | |