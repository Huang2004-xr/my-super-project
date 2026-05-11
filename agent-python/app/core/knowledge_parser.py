import io
import os
import re
from typing import Dict, List

from docx import Document
from PIL import Image
from pypdf import PdfReader
import pytesseract


class KnowledgeParser:
    def __init__(self) -> None:
        self.chunk_target_chars = int(os.getenv("KNOWLEDGE_CHUNK_TARGET_CHARS", "800").strip() or "800")
        self.chunk_overlap_chars = int(os.getenv("KNOWLEDGE_CHUNK_OVERLAP_CHARS", "120").strip() or "120")
        self.ocr_lang = os.getenv("KNOWLEDGE_OCR_LANG", "chi_sim+eng").strip() or "chi_sim+eng"

    def parse(self, file_name: str, content_type: str, file_bytes: bytes) -> List[Dict[str, object]]:
        suffix = os.path.splitext(file_name or "")[1].lower()
        if suffix in {".txt", ".md", ".markdown"} or content_type.startswith("text/"):
            text = self._decode_text(file_bytes)
            return self._chunk_sections([
                {"content": text, "pageNo": 0, "sectionTitle": os.path.splitext(file_name)[0] or "正文"}
            ])
        if suffix == ".pdf" or content_type == "application/pdf":
            return self._chunk_sections(self._parse_pdf(file_bytes, file_name))
        if suffix == ".docx" or content_type in {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
        }:
            return self._chunk_sections(self._parse_docx(file_bytes, file_name))
        if content_type.startswith("image/") or suffix in {".png", ".jpg", ".jpeg", ".webp", ".bmp"}:
            return self._chunk_sections(self._parse_image(file_bytes, file_name))
        raise ValueError(f"unsupported document type: {content_type or suffix or 'unknown'}")

    def _parse_pdf(self, file_bytes: bytes, file_name: str) -> List[Dict[str, object]]:
        reader = PdfReader(io.BytesIO(file_bytes))
        sections: List[Dict[str, object]] = []
        for index, page in enumerate(reader.pages, start=1):
            text = self._clean_text(page.extract_text() or "")
            if text:
                sections.append({
                    "content": text,
                    "pageNo": index,
                    "sectionTitle": f"{os.path.splitext(file_name)[0]} 第{index}页",
                })
        if sections:
            return sections
        raise ValueError("pdf text extraction returned empty result; scanned PDF OCR fallback is not configured in this prototype")

    def _parse_docx(self, file_bytes: bytes, file_name: str) -> List[Dict[str, object]]:
        document = Document(io.BytesIO(file_bytes))
        sections: List[Dict[str, object]] = []
        current_title = os.path.splitext(file_name)[0] or "DOCX"
        current_lines: List[str] = []
        for paragraph in document.paragraphs:
            text = self._clean_text(paragraph.text)
            if not text:
                continue
            style_name = getattr(paragraph.style, "name", "") or ""
            if style_name.lower().startswith("heading") and current_lines:
                sections.append({
                    "content": "\n".join(current_lines),
                    "pageNo": 0,
                    "sectionTitle": current_title,
                })
                current_lines = []
                current_title = text
                continue
            if style_name.lower().startswith("heading"):
                current_title = text
                continue
            current_lines.append(text)
        if current_lines:
            sections.append({
                "content": "\n".join(current_lines),
                "pageNo": 0,
                "sectionTitle": current_title,
            })
        if not sections:
            raise ValueError("docx extraction returned empty result")
        return sections

    def _parse_image(self, file_bytes: bytes, file_name: str) -> List[Dict[str, object]]:
        text = self._extract_image_text(file_bytes)
        if not text:
            raise ValueError("ocr returned empty result")
        return [{"content": text, "pageNo": 1, "sectionTitle": os.path.splitext(file_name)[0] or "图片 OCR"}]

    def _extract_image_text(self, file_bytes: bytes) -> str:
        try:
            image = Image.open(io.BytesIO(file_bytes))
            return self._clean_text(pytesseract.image_to_string(image, lang=self.ocr_lang))
        except pytesseract.TesseractNotFoundError as exc:
            raise ValueError("Tesseract OCR is not installed or not in PATH") from exc

    def _chunk_sections(self, sections: List[Dict[str, object]]) -> List[Dict[str, object]]:
        chunks: List[Dict[str, object]] = []
        for section in sections:
            content = self._clean_text(str(section.get("content") or ""))
            if not content:
                continue
            page_no = int(section.get("pageNo") or 0)
            section_title = str(section.get("sectionTitle") or "正文").strip() or "正文"
            if len(content) <= self.chunk_target_chars:
                chunks.append({
                    "content": content,
                    "metadata": {
                        "pageNo": page_no,
                        "sectionTitle": section_title,
                        "charStart": 0,
                        "charEnd": len(content),
                    },
                })
                continue
            start = 0
            text_length = len(content)
            while start < text_length:
                end = min(text_length, start + self.chunk_target_chars)
                chunk_text = content[start:end].strip()
                if chunk_text:
                    chunks.append({
                        "content": chunk_text,
                        "metadata": {
                            "pageNo": page_no,
                            "sectionTitle": section_title,
                            "charStart": start,
                            "charEnd": end,
                        },
                    })
                if end >= text_length:
                    break
                start = max(0, end - self.chunk_overlap_chars)
        if not chunks:
            raise ValueError("document extraction returned no chunks")
        return chunks

    def _decode_text(self, file_bytes: bytes) -> str:
        for encoding in ("utf-8", "utf-8-sig", "gb18030"):
            try:
                return self._clean_text(file_bytes.decode(encoding))
            except UnicodeDecodeError:
                continue
        return self._clean_text(file_bytes.decode("utf-8", errors="ignore"))

    def _clean_text(self, value: str) -> str:
        value = value.replace("\r", "\n")
        value = re.sub(r"\n{3,}", "\n\n", value)
        value = re.sub(r"[ \t]+", " ", value)
        return value.strip()
