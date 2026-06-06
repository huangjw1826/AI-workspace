"""
Summary service - LLM 智能摘要生成服务

通过 OpenAI 兼容接口调用大模型，对转写文本生成结构化摘要。
支持 12 种摘要模板（6 个工作场景 + 4 个生活场景 + 2 个通用场景），
3 个 LLM 提供商（DeepSeek/通义千问/小米 MiMo）。
所有模板均输出固定结构化 Markdown，长文本自动分段处理，网络异常自动重试。
"""

from time import sleep

from openai import OpenAI

from app.config import get_settings


# ---------------------------------------------------------------------------
# 输出格式规范 — 注入到每个模板的 system prompt 中，确保输出结构固定
# ---------------------------------------------------------------------------
OUTPUT_FORMAT_RULES = (
    "输出格式要求（必须严格遵守）：\n"
    "1. 每个章节必须使用 ## 二级标题开头，不得使用一级标题。\n"
    "2. 所有列表项使用 - 开头，不得使用数字编号（除非明确要求表格）。\n"
    "3. 每个章节末尾必须包含一行 > 置信度：高/中/低，表示该章节内容在原文中的支撑程度。\n"
    "4. 任何原文未提及的信息，字段值必须填写“未提及”，不得编造。\n"
    "5. 总字数控制在 300-800 字，简明扼要，不堆砌原文。"
)

# ---------------------------------------------------------------------------
# 多人对话处理指导原则
# ---------------------------------------------------------------------------
MULTI_SPEAKER_GUIDANCE = (
    "以下转写可能来自多人对话、会议或访谈。请把内容视为对话记录，不要默认所有观点都属于\"我\"或同一个人；"
    "如果无法确认说话人身份，请使用\"发言者\"\"与会者\"\"一方\"等中性称谓。不要编造姓名、职务、责任人或时间。"
    "输出必须忠实于转写内容，缺失信息请明确写\"未提及\"。"
)

# ---------------------------------------------------------------------------
# 摘要模板定义
# 每个模板包含固定输出结构，按场景分类：工作 / 生活 / 通用
# ---------------------------------------------------------------------------
SUMMARY_TEMPLATES = {
    # =========================================================================
    # 工作场景
    # =========================================================================
    "meeting_minutes": {
        "id": "meeting_minutes",
        "name": "📋 会议纪要",
        "description": "工作·正式会议记录，含议题、结论、风险和责任人",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名专业会议记录员。请将以下转写整理为会议纪要，严格按照以下固定结构输出：\n\n"
            "## 会议主题\n"
            "一句话概括本次会议的核心议题。\n"
            "> 置信度：高/中/低\n\n"
            "## 参会角色\n"
            "- 发言人A：角色（如能推断）\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 议题与讨论\n"
            "- 议题1：讨论要点\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 结论与决策\n"
            "- 已决定：xxx\n"
            "- 待确认：xxx\n"
            "> 置信度：高/中/低\n\n"
            "## 风险与阻塞\n"
            "- 风险/阻塞点（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 后续行动\n"
            "| 事项 | 责任人 | 截止时间 | 优先级 |\n"
            "|------|--------|----------|--------|\n"
            "| ... | ... | ... | ... |\n"
            "> 置信度：高/中/低"
        ),
    },
    "action_items": {
        "id": "action_items",
        "name": "✅ 待办清单",
        "description": "工作·提取行动项、责任人和截止时间",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名项目助理。请从转写中提取所有待办事项，严格按以下结构输出：\n\n"
            "## 待办总览\n"
            "一句话概述待办数量和紧急程度。\n"
            "> 置信度：高/中/低\n\n"
            "## 行动项\n"
            "| 序号 | 事项描述 | 责任人 | 截止时间 | 优先级 | 前置依赖 |\n"
            "|------|---------|--------|----------|--------|----------|\n"
            "| 1 | ... | ... | ... | 🔴高/🟡中/🟢低 | ... |\n"
            "> 置信度：高/中/低\n\n"
            "## 阻塞与风险\n"
            "- 影响待办推进的阻塞点（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 备注\n"
            "- 补充说明或上下文（如无则写“未提及”）\n"
            "> 置信度：高/中/低"
        ),
    },
    "decisions_risks": {
        "id": "decisions_risks",
        "name": "⚠️ 决策与风险",
        "description": "工作·梳理已定决策、未决事项和潜在风险",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名决策分析顾问。请从转写中提取决策和风险信息，严格按以下结构输出：\n\n"
            "## 背景\n"
            "一句话说明决策上下文。\n"
            "> 置信度：高/中/低\n\n"
            "## 已定决策\n"
            "- 决策1：内容 + 决策依据\n"
            "- ...（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 待决事项\n"
            "- 事项1：分歧点 / 待确认原因\n"
            "- ...（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 风险评估\n"
            "| 风险描述 | 可能性 | 影响程度 | 缓解措施 |\n"
            "|---------|--------|---------|----------|\n"
            "| ... | 高/中/低 | 高/中/低 | ... |\n"
            "> 置信度：高/中/低\n\n"
            "## 建议\n"
            "- 下一步建议动作\n"
            "> 置信度：高/中/低"
        ),
    },
    "executive_brief": {
        "id": "executive_brief",
        "name": "📊 管理层简报",
        "description": "工作·高层速览，提炼关键结论和需决策事项",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名战略顾问。请将转写压缩为管理层一页简报，严格按以下结构输出：\n\n"
            "## 一句话概要\n"
            "用一句话概括核心内容和结论。\n"
            "> 置信度：高/中/低\n\n"
            "## 关键结论（3-5条）\n"
            "- 结论1\n"
            "- 结论2\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 核心数据/事实\n"
            "- 关键数字或事实引用（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 分歧与风险\n"
            "- 主要分歧或风险点（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 需管理层决策\n"
            "- 需要上级拍板的事项（如无则写“未提及”）\n"
            "> 置信度：高/中/低"
        ),
    },
    "interview_notes": {
        "id": "interview_notes",
        "name": "🎤 访谈记录",
        "description": "工作·面试/用户访谈/调研的要点整理",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名访谈分析师。请将转写整理为结构化访谈记录，严格按以下结构输出：\n\n"
            "## 访谈概要\n"
            "一句话概述访谈主题和参与者。\n"
            "> 置信度：高/中/低\n\n"
            "## 核心观点\n"
            "- 观点1：发言人立场 + 原文依据\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 关键引用\n"
            "- “引用原文关键句1”\n"
            "- ...（最多5条）\n"
            "> 置信度：高/中/低\n\n"
            "## 发现与洞察\n"
            "- 有价值的发现或模式\n"
            "> 置信度：高/中/低\n\n"
            "## 后续跟进\n"
            "- 待确认或需补充了解的事项（如无则写“未提及”）\n"
            "> 置信度：高/中/低"
        ),
    },
    "brainstorm": {
        "id": "brainstorm",
        "name": "💡 头脑风暴",
        "description": "工作·创意讨论整理，归类想法和可行性",
        "category": "work",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "你是一名创新引导师。请将转写中的头脑风暴整理为结构化创意清单，严格按以下结构输出：\n\n"
            "## 讨论主题\n"
            "一句话概括本次头脑风暴的目标。\n"
            "> 置信度：高/中/低\n\n"
            "## 创意清单\n"
            "| 序号 | 创意描述 | 提出者 | 可行性 | 创新度 |\n"
            "|------|---------|--------|--------|--------|\n"
            "| 1 | ... | 发言者 | 🔴高/🟡中/🟢低 | ⭐⭐⭐ |\n"
            "> 置信度：高/中/低\n\n"
            "## 亮点方案\n"
            "- 最有潜力的2-3个方案及理由\n"
            "> 置信度：高/中/低\n\n"
            "## 待深入方向\n"
            "- 需要进一步研究的领域（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 下一步\n"
            "- 落实创意的具体动作\n"
            "> 置信度：高/中/低"
        ),
    },

    # =========================================================================
    # 生活场景
    # =========================================================================
    "daily_journal": {
        "id": "daily_journal",
        "name": "📝 日记整理",
        "description": "生活·个人录音日记的结构化梳理",
        "category": "life",
        "prompt": (
            "你是一名细腻的个人助理。以下转写来自个人录音日记，请以温暖而客观的语气整理，严格按以下结构输出：\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "## 今日主题\n"
            "一句话概括录音的核心话题。\n"
            "> 置信度：高/中/低\n\n"
            "## 事件与经历\n"
            "- 事件1：时间 + 经过\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 感受与反思\n"
            "- 表达的情绪或感悟\n"
            "> 置信度：高/中/低\n\n"
            "## 待办/提醒\n"
            "- 提到的事项（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 闪光时刻\n"
            "- 值得记录的亮点或金句\n"
            "> 置信度：高/中/低"
        ),
    },
    "study_notes": {
        "id": "study_notes",
        "name": "📚 学习笔记",
        "description": "生活·课程/讲座/读书录音的知识提炼",
        "category": "life",
        "prompt": (
            "你是一名学习导师。以下转写来自学习录音（课程/讲座/读书），请以清晰的教学风格整理，严格按以下结构输出：\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "## 学习主题\n"
            "一句话概括学习内容。\n"
            "> 置信度：高/中/低\n\n"
            "## 核心知识点\n"
            "- 知识点1：定义 + 关键理解\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 重要概念\n"
            "| 术语 | 解释 |\n"
            "|------|------|\n"
            "| ... | ... |\n"
            "> 置信度：高/中/低\n\n"
            "## 疑问与思考\n"
            "- 未理解或需要深入的问题（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 复习建议\n"
            "- 巩固学习的建议\n"
            "> 置信度：高/中/低"
        ),
    },
    "family_meeting": {
        "id": "family_meeting",
        "name": "👨‍👩‍👧 家庭记录",
        "description": "生活·家庭会议/亲子对话的温馨记录",
        "category": "life",
        "prompt": (
            "你是一名温暖的家庭助理。以下转写来自家庭场景（家庭会议/亲子对话/生活讨论），请以温馨亲切的语气整理，严格按以下结构输出：\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "## 话题概要\n"
            "一句话概括家庭对话的主题。\n"
            "> 置信度：高/中/低\n\n"
            "## 讨论内容\n"
            "- 话题1：要点\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 家庭共识\n"
            "- 达成的一致意见或决定\n"
            "> 置信度：高/中/低\n\n"
            "## 待办事项\n"
            "| 事项 | 负责人 | 时间 |\n"
            "|------|--------|------|\n"
            "| ... | ... | ... |\n"
            "> 置信度：高/中/低\n\n"
            "## 温馨时刻\n"
            "- 值得记录的家庭瞬间（如无则写“未提及”）\n"
            "> 置信度：高/中/低"
        ),
    },
    "health_consult": {
        "id": "health_consult",
        "name": "🏥 健康记录",
        "description": "生活·问诊/健康咨询的关键信息整理",
        "category": "life",
        "prompt": (
            "你是一名细心的健康助理。以下转写来自健康相关对话（问诊/咨询/健康讨论），请严谨客观地整理，严格按以下结构输出：\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "## 咨询概要\n"
            "一句话概括咨询背景。\n"
            "> 置信度：高/中/低\n\n"
            "## 症状/状况描述\n"
            "- 描述的症状或健康关注点\n"
            "> 置信度：高/中/低\n\n"
            "## 建议与指导\n"
            "- 对方的建议（如为医生建议请标注“医嘱”）\n"
            "> 置信度：高/中/低\n\n"
            "## 用药/治疗信息\n"
            "| 项目 | 说明 | 频率/用量 | 备注 |\n"
            "|------|------|----------|------|\n"
            "| ... | ... | ... | ... |\n"
            "（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 复诊/跟进\n"
            "- 下次预约或需跟进事项（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "⚠️ 重要提示：本整理仅供参考，不能替代专业医疗建议。"
        ),
    },

    # =========================================================================
    # 通用场景
    # =========================================================================
    "structured_summary": {
        "id": "structured_summary",
        "name": "📄 通用摘要",
        "description": "通用·提炼背景、主题、关键结论和后续事项",
        "category": "general",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            f"{OUTPUT_FORMAT_RULES}\n\n"
            "请将以下转写整理为通用结构化摘要，严格按以下结构输出：\n\n"
            "## 背景\n"
            "一句话交代录音场景和上下文。\n"
            "> 置信度：高/中/低\n\n"
            "## 核心内容\n"
            "- 要点1\n"
            "- 要点2\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 关键结论\n"
            "- 结论1\n"
            "- ...\n"
            "> 置信度：高/中/低\n\n"
            "## 重要事实/数据\n"
            "- 关键信息（如无则写“未提及”）\n"
            "> 置信度：高/中/低\n\n"
            "## 后续事项\n"
            "- 需跟进的事项（如无则写“未提及”）\n"
            "> 置信度：高/中/低"
        ),
    },
    "polished_transcript": {
        "id": "polished_transcript",
        "name": "📝 转写规整",
        "description": "通用·修正口语冗余和错别字，保持原意不变",
        "category": "general",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请对以下录音转写做语篇规整：修正口语冗余、明显错别字和断句，保持原意。"
            "如果出现多方观点，请按主题或发言线索组织，不要统一改写成第一人称叙述。"
            "输出适合阅读的 Markdown。"
        ),
    },
}


# 转写规整的分段配置
POLISHED_TRANSCRIPT_CHUNK_CHARS = 2200   # 每段最大字符数
POLISHED_TRANSCRIPT_RETRY_CHARS = 900     # 超出 token 限制时拆分的子段最大字符数


class SummaryService:
    """LLM 摘要生成服务。

    使用 OpenAI 兼容接口调用大模型，支持以下特性：
    - 6 种摘要模板（会议纪要、行动事项、决策风险等）
    - 长文本自动分段：超过 token 限制的转写自动拆分处理
    - 指数退避重试：网络/API 异常自动重试，最多 3 次
    - 多提供商适配：自动处理 MiMo 的 thinking 模式和 API Key 映射
    """

    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = OpenAI(
            api_key=self.settings.resolved_llm_api_key,
            base_url=self.settings.resolved_llm_base_url,
            timeout=self.settings.llm_timeout_seconds,
        ) if self.settings.resolved_llm_api_key else None

    def configured(self) -> bool:
        """检查 LLM 是否已配置（API Key 已填写）。"""
        return bool(self.settings.resolved_llm_api_key)

    def _request(self, system_prompt: str, user_content: str) -> dict[str, object]:
        """构建 OpenAI API 请求参数。

        自动注入模型名、temperature、top_p、max_completion_tokens。
        MiMo 提供商会额外注入 extra_body 的 thinking 配置。
        """
        request: dict[str, object] = {
            "model": self.settings.resolved_llm_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content},
            ],
            "max_completion_tokens": self.settings.llm_max_completion_tokens,
        }
        if self.settings.resolved_llm_temperature is not None:
            request["temperature"] = self.settings.resolved_llm_temperature
        if self.settings.resolved_llm_top_p is not None:
            request["top_p"] = self.settings.resolved_llm_top_p
        if self.settings.normalized_llm_provider == "mimo":
            thinking = self.settings.mimo_thinking.strip().lower()
            if thinking in {"enabled", "disabled"}:
                request["extra_body"] = {"thinking": {"type": thinking}}
        return request

    def _complete(self, system_prompt: str, user_content: str) -> tuple[str, str | None]:
        """调用 LLM 完成请求，返回 (内容, finish_reason)。"""
        if self.client is None:
            raise RuntimeError("LLM client is not configured.")
        response = self._complete_with_retry(system_prompt, user_content)
        choice = response.choices[0]
        return choice.message.content or "", choice.finish_reason

    def _complete_with_retry(self, system_prompt: str, user_content: str):
        """带指数退避重试的 LLM 请求。

        重试策略：最多 llm_retry_attempts 次（默认 3），
        退避间隔 2^(attempt-1) 秒，上限 8 秒。
        """
        attempts = max(1, self.settings.llm_retry_attempts)
        last_error: Exception | None = None
        for attempt in range(1, attempts + 1):
            try:
                return self.client.chat.completions.create(**self._request(system_prompt, user_content))
            except Exception as exc:
                last_error = exc
                if attempt >= attempts:
                    break
                sleep(min(2 ** (attempt - 1), 8))
        raise RuntimeError(f"LLM request failed after {attempts} attempt(s): {last_error}") from last_error

    def _split_transcript(self, transcript: str, max_chars: int) -> list[str]:
        """按行将转写文本拆分为不超过 max_chars 字符的段落。

        保持行完整性，不会在行中间切断。
        """
        chunks: list[str] = []
        current: list[str] = []
        current_size = 0
        for line in transcript.splitlines():
            line = line.strip()
            if not line:
                continue
            projected = current_size + len(line) + 1
            if current and projected > max_chars:
                chunks.append("\n".join(current))
                current = [line]
                current_size = len(line)
            else:
                current.append(line)
                current_size = projected
        if current:
            chunks.append("\n".join(current))
        return chunks or [transcript]

    def _split_long_text(self, text: str) -> list[str]:
        """将单段长文本按中点附近的换行符/句号分割为两段。

        用于处理 finish_reason=length 时的递归分割。
        优先以换行符分割，其次以句号分割。
        """
        midpoint = len(text) // 2
        nearby_breaks = [
            text.rfind("\n", 0, midpoint),
            text.find("\n", midpoint),
            text.rfind("。", 0, midpoint),
            text.find("。", midpoint),
        ]
        breakpoints = [point for point in nearby_breaks if point > 0]
        if not breakpoints:
            return [text[:midpoint], text[midpoint:]]
        split_at = min(breakpoints, key=lambda point: abs(point - midpoint)) + 1
        return [text[:split_at], text[split_at:]]

    def _polish_chunk(self, chunk: str, index: int, total: int) -> str:
        """对单个转写段落进行规整处理。

        处理 finish_reason=length 的情况：
        - 如果当前段落大于 RETRY_CHARS，递归二分分割
        - 否则抛出异常提示增加 max_completion_tokens
        """
        system_prompt = (
            f"{SUMMARY_TEMPLATES['polished_transcript']['prompt']}\n\n"
            "重要：这是长录音的一部分。请只规整本段，不要总结、不要压缩、不要省略信息、不要补写其他段落。"
            "如果原文有明显识别错误但无法确定正确词，请尽量保留原词并让句子更可读。"
        )
        user_content = f"第 {index}/{total} 段转写如下：\n\n{chunk}"
        content, finish_reason = self._complete(system_prompt, user_content)
        if finish_reason == "length":
            if len(chunk) <= POLISHED_TRANSCRIPT_RETRY_CHARS:
                raise RuntimeError(
                    "LLM output reached the token limit while polishing transcript. "
                    "Increase LLM_MAX_COMPLETION_TOKENS and try again."
                )
            parts = self._split_long_text(chunk)
            polished_parts = [
                self._polish_chunk(part.strip(), index, total)
                for part in parts
                if part.strip()
            ]
            return "\n\n".join(polished_parts)
        return content.strip()

    def _generate_polished_transcript(self, transcript: str) -> str:
        """生成转写规整结果。

        按 POLISHED_TRANSCRIPT_CHUNK_CHARS 分段，每段独立规整后合并。
        单段直接规整，多段添加分段标题。
        """
        chunks = self._split_transcript(transcript, POLISHED_TRANSCRIPT_CHUNK_CHARS)
        if len(chunks) == 1:
            return self._polish_chunk(chunks[0], 1, 1)

        sections = ["# 转写内容规整", ""]
        for index, chunk in enumerate(chunks, start=1):
            polished = self._polish_chunk(chunk, index, len(chunks))
            sections.extend([f"## 第 {index} 段", "", polished, ""])
        return "\n".join(sections).strip()

    def generate(self, transcript: str, mode: str = "summary") -> str:
        """生成指定模板的摘要。

        流程：
        1. 检查 LLM 配置 → 获取模板 prompt
        2. polished_transcript 模式走分段规整流程
        3. 其他模式直接调用 LLM complete
        4. 检查 finish_reason=length 并报错

        Args:
            transcript: 拼接后的转写文本
            mode: 摘要模板 ID（默认 "summary" 即 structured_summary）

        Returns:
            Markdown 格式的摘要内容

        Raises:
            RuntimeError: LLM 未配置 / token 限制 / API 错误
        """
        if not self.configured():
            if self.settings.normalized_llm_provider == "mimo":
                raise RuntimeError("MIMO_API_KEY or LLM_API_KEY is not configured.")
            raise RuntimeError("LLM_API_KEY is not configured.")

        template = SUMMARY_TEMPLATES.get(mode, SUMMARY_TEMPLATES["structured_summary"])
        if mode == "polished_transcript":
            return self._generate_polished_transcript(transcript)

        content, finish_reason = self._complete(str(template["prompt"]), transcript)
        if finish_reason == "length":
            raise RuntimeError(
                "LLM output reached the token limit before the summary was complete. "
                "Increase LLM_MAX_COMPLETION_TOKENS or use a shorter transcript."
            )
        return content
