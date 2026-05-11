from typing import Optional, Tuple


class CapabilityRouter:
    knowledge_terms = ("知识库", "根据资料", "文档", "说明", "检索", "查询", "资料里", "引用")
    video_terms = ("视频", "短视频", "分镜", "脚本", "镜头", "剪辑", "口播视频", "生成视频")
    image_terms = ("图片", "海报", "封面", "插画", "图像", "生成一张", "设计一张", "配图")
    ability_question_terms = ("可以", "能不能", "能否", "支持", "会不会", "你能", "你可以", "能做什么")
    question_terms = ("吗", "么", "？", "?", "什么", "哪些")
    image_action_terms = ("帮我", "请", "生成一张", "设计一张", "创作一张", "做一张", "画一张", "出一张")
    video_action_terms = ("帮我", "请", "生成一条", "写一条", "创作一条", "生成视频", "短视频脚本", "视频脚本")

    def route(
        self,
        message: str,
        image_asset_id: Optional[str],
        use_knowledge_base: bool,
        capability_hint: Optional[str] = None,
    ) -> Tuple[str, str]:
        if capability_hint:
            return capability_hint, f"使用前端传入的能力提示：{capability_hint}"

        if self._contains(message, self.video_terms) and self._contains(message, self.video_action_terms):
            suffix = "，并将上传图片作为参考图" if image_asset_id else ""
            return "VIDEO_CREATION", f"检测到用户要求生成视频、脚本或分镜{suffix}"

        if self._contains(message, self.image_terms) and self._contains(message, self.image_action_terms):
            suffix = "，并将上传图片作为参考图" if image_asset_id else ""
            return "IMAGE_CREATION", f"检测到用户要求生成图片、海报或视觉内容{suffix}"

        if use_knowledge_base and self._contains(message, self.knowledge_terms):
            return "KNOWLEDGE_RETRIEVAL", "检测到用户要求根据内置知识库进行检索问答"

        if use_knowledge_base:
            return "KNOWLEDGE_RETRIEVAL", "用户启用了知识库模式，优先按知识库检索处理"

        if self._is_ability_question(message):
            return "TEXT_CHAT", "检测到用户在询问能力范围，按文字沟通回答"

        if image_asset_id:
            return "TEXT_CHAT", "未检测到创作关键词，按文字沟通处理并保留图片上下文"

        return "TEXT_CHAT", "未检测到图片、视频或知识库检索意图，按文字沟通处理"

    def _contains(self, message: str, terms: tuple[str, ...]) -> bool:
        return any(term in message for term in terms)

    def _is_ability_question(self, message: str) -> bool:
        has_question = self._contains(message, self.question_terms)
        asks_ability = self._contains(message, self.ability_question_terms)
        mentions_capability = (
            self._contains(message, self.image_terms)
            or self._contains(message, self.video_terms)
            or self._contains(message, self.knowledge_terms)
            or "功能" in message
            or "能力" in message
        )
        return has_question and asks_ability and mentions_capability
