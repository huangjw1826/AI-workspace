# AI Recorder 文档中心

这个目录分为三类文档：

| 目录/文件 | 用途 |
| --- | --- |
| `product/` | 产品路线图、版本规划、发布验收和待办池。 |
| `decisions/` | 架构决策记录，保存重要技术取舍的原因。 |
| `cloud-llm-providers.md` | 云端大模型供应商配置说明。 |
| `portable.md` | Windows 便携包打包和使用说明。 |
| `troubleshooting.md` | 常见故障排查。 |
| `execution-report.md` | 早期执行记录。 |
| `optimization-report.md` | 项目优化建议和问题清单。 |

## 当前重点

- 3.0 产品迭代：见 `product/versions/3.0/README.md`。
- 全局路线图：见 `product/roadmap.md`。
- 后续待办沉淀：见 `product/backlog/README.md`。

## 维护规则

1. 用户可见能力变化后，同步更新根目录 `README.md`。
2. 版本范围、优先级或验收口径变化后，同步更新 `product/`。
3. 影响架构、数据模型、后台任务、部署方式的重大决策，新增一条 ADR 到 `decisions/`。
