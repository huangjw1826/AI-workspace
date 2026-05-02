from openai import OpenAI

from app.config import get_settings


MULTI_SPEAKER_GUIDANCE = (
    "以下转写可能来自多人对话、会议或访谈。请把内容视为对话记录，不要默认所有观点都属于“我”或同一个人；"
    "如果无法确认说话人身份，请使用“发言者”“与会者”“一方”等中性称谓。不要编造姓名、职务、责任人或时间。"
    "输出必须忠实于转写内容，缺失信息请明确写“未提及”。"
)


SUMMARY_TEMPLATES = {
    "structured_summary": {
        "id": "structured_summary",
        "name": "结构化摘要",
        "description": "提炼背景、主题、关键结论和后续事项。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请把以下录音转写整理为结构化摘要，包含：背景、主要议题、关键结论、重要事实、后续建议。"
            "使用清晰的 Markdown 标题和列表。"
        ),
    },
    "meeting_minutes": {
        "id": "meeting_minutes",
        "name": "会议纪要",
        "description": "适合正式会议记录，包含议题、结论、风险和责任人。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请把以下录音转写整理为会议纪要，包含：会议主题、参会角色、议题、讨论要点、结论、风险、"
            "责任人、待确认事项。仅在转写中明确出现责任人时才写具体责任人，否则写“未提及”。"
            "使用 Markdown。"
        ),
    },
    "action_items": {
        "id": "action_items",
        "name": "待办事项",
        "description": "提取负责人、事项、截止时间和依赖。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请从以下录音转写中提取待办事项。按 Markdown 表格输出：负责人、事项、截止时间、优先级、依赖、备注。"
            "负责人、截止时间或优先级未明确出现时写“未提及”，不要根据语气猜测。"
        ),
    },
    "decisions_risks": {
        "id": "decisions_risks",
        "name": "决策与风险",
        "description": "突出已确定决策、未决问题和潜在风险。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请从以下录音转写中整理已确定决策、决策依据、潜在风险、阻塞点、未决问题和建议动作。"
            "区分“已决定”和“讨论中/待确认”，不要把建议误写成既定决策。使用 Markdown。"
        ),
    },
    "executive_brief": {
        "id": "executive_brief",
        "name": "管理层简报",
        "description": "压缩为高层快速阅读的一页简报。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请把以下录音转写压缩为管理层简报，包含：一句话概览、三到五条关键结论、主要分歧或风险、"
            "需要管理层拍板的事项。只保留对决策有帮助的信息，使用 Markdown。"
        ),
    },
    "polished_transcript": {
        "id": "polished_transcript",
        "name": "转写内容规整",
        "description": "修正口语冗余，保持原意。",
        "prompt": (
            f"{MULTI_SPEAKER_GUIDANCE}\n\n"
            "请对以下录音转写做语篇规整：修正口语冗余、明显错别字和断句，保持原意。"
            "如果出现多方观点，请按主题或发言线索组织，不要统一改写成第一人称叙述。"
            "输出适合阅读的 Markdown。"
        ),
    },
}


POLISHED_TRANSCRIPT_CHUNK_CHARS = 2200
POLISHED_TRANSCRIPT_RETRY_CHARS = 900


class SummaryService:
    def __init__(self) -> None:
        self.settings = get_settings()
        self.client = OpenAI(
            api_key=self.settings.resolved_llm_api_key,
            base_url=self.settings.resolved_llm_base_url,
        ) if self.settings.resolved_llm_api_key else None

    def configured(self) -> bool:
        return bool(self.settings.resolved_llm_api_key)

    def _request(self, system_prompt: str, user_content: str) -> dict[str, object]:
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
        if self.client is None:
            raise RuntimeError("LLM client is not configured.")
        response = self.client.chat.completions.create(**self._request(system_prompt, user_content))
        choice = response.choices[0]
        return choice.message.content or "", choice.finish_reason

    def _split_transcript(self, transcript: str, max_chars: int) -> list[str]:
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
        chunks = self._split_transcript(transcript, POLISHED_TRANSCRIPT_CHUNK_CHARS)
        if len(chunks) == 1:
            return self._polish_chunk(chunks[0], 1, 1)

        sections = ["# 转写内容规整", ""]
        for index, chunk in enumerate(chunks, start=1):
            polished = self._polish_chunk(chunk, index, len(chunks))
            sections.extend([f"## 第 {index} 段", "", polished, ""])
        return "\n".join(sections).strip()

    def generate(self, transcript: str, mode: str = "summary") -> str:
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
