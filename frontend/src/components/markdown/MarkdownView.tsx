import React from "react";

export function MarkdownView({ content }: { content: string }) {
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  const blocks: React.ReactNode[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }

    if (line.trim().startsWith("```")) {
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith("```")) {
        code.push(lines[index]);
        index += 1;
      }
      index += 1;
      blocks.push(
        <pre key={blocks.length} className="markdown-code">
          <code>{code.join("\n")}</code>
        </pre>
      );
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      const level = Math.min(heading[1].length, 4);
      const Tag = `h${level}` as keyof React.JSX.IntrinsicElements;
      blocks.push(
        <Tag key={blocks.length}>{renderInline(heading[2])}</Tag>
      );
      index += 1;
      continue;
    }

    if (isTable(lines, index)) {
      const header = splitRow(lines[index]);
      index += 2;
      const rows: string[][] = [];
      while (index < lines.length && lines[index].includes("|") && lines[index].trim()) {
        rows.push(splitRow(lines[index]));
        index += 1;
      }
      blocks.push(
        <div key={blocks.length} className="markdown-table-wrap">
          <table>
            <thead>
              <tr>
                {header.map((cell, ci) => (
                  <th key={ci}>{renderInline(cell)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, ri) => (
                <tr key={ri}>
                  {header.map((_, ci) => (
                    <td key={ci}>{renderInline(row[ci] ?? "")}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
      continue;
    }

    const ul = line.match(/^\s*[-*]\s+(.+)$/);
    const olMatch = line.match(/^\s*\d+[.)]\s+(.+)$/);
    if (ul || olMatch) {
      const ordered = Boolean(olMatch);
      const items: string[] = [];
      while (index < lines.length) {
        const m = ordered
          ? lines[index].match(/^\s*\d+[.)]\s+(.+)$/)
          : lines[index].match(/^\s*[-*]\s+(.+)$/);
        if (!m) break;
        items.push(m[1]);
        index += 1;
      }
      const ListTag = ordered ? "ol" : "ul";
      blocks.push(
        <ListTag key={blocks.length}>
          {items.map((item, i) => (
            <li key={i}>{renderInline(item)}</li>
          ))}
        </ListTag>
      );
      continue;
    }

    const paragraph: string[] = [];
    while (index < lines.length && lines[index].trim()) {
      if (
        /^(#{1,4})\s+/.test(lines[index]) ||
        /^\s*[-*]\s+/.test(lines[index]) ||
        /^\s*\d+[.)]\s+/.test(lines[index]) ||
        isTable(lines, index)
      ) {
        break;
      }
      paragraph.push(lines[index].trim());
      index += 1;
    }
    if (paragraph.length) {
      blocks.push(<p key={blocks.length}>{renderInline(paragraph.join(" "))}</p>);
    }
  }

  return <div className="markdown-view">{blocks}</div>;
}

function renderInline(text: string) {
  return text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean).map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={i}>{part.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={i}>{part}</React.Fragment>;
  });
}

function isTable(lines: string[], i: number) {
  return Boolean(
    lines[i]?.includes("|") &&
    lines[i + 1]?.includes("|") &&
    /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[i + 1])
  );
}

function splitRow(line: string) {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((c) => c.trim());
}
