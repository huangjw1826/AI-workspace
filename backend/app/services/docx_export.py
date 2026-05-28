"""
DOCX export service - Word 文档生成服务

纯 Python 实现的 DOCX 生成器（无外部依赖），直接构建 OpenXML 格式的 ZIP 包。
用于将转写和摘要导出为 .docx 格式，支持多段落文本。
"""

from __future__ import annotations

from html import escape
from io import BytesIO
from zipfile import ZIP_DEFLATED, ZipFile


def _paragraph(text: str) -> str:
    """构建 WordProcessingML 段落 XML。

    支持多行文本（通过 <w:br/> 换行），使用 html.escape 转义特殊字符。

    Args:
        text: 段落文本内容，可包含换行符

    Returns:
        <w:p>...</w:p> 格式的 XML 字符串
    """
    runs = []
    for index, line in enumerate(text.splitlines() or [""]):
        if index:
            runs.append("<w:r><w:br/></w:r>")
        runs.append(f"<w:r><w:t xml:space=\"preserve\">{escape(line)}</w:t></w:r>")
    return f"<w:p>{''.join(runs)}</w:p>"


def build_docx(title: str, lines: list[str]) -> bytes:
    """生成 DOCX 文件的字节内容。

    构建符合 OpenXML 规范的 ZIP 包，包含：
    - [Content_Types].xml: 内容类型声明
    - _rels/.rels: 文件关系定义
    - word/document.xml: 正文内容（A4 纸张，标准页边距）

    Args:
        title: 文档标题（作为第一段）
        lines: 正文段落列表，每项为一段文本

    Returns:
        DOCX 文件的完整字节内容，可直接写入文件或通过 HTTP 响应返回
    """
    body = [_paragraph(title), _paragraph("")]
    body.extend(_paragraph(line) for line in lines)
    document_xml = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    {''.join(body)}
    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
  </w:body>
</w:document>"""
    buffer = BytesIO()
    with ZipFile(buffer, "w", ZIP_DEFLATED) as docx:
        docx.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        docx.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""",
        )
        docx.writestr("word/document.xml", document_xml)
    return buffer.getvalue()
