import type { ReactNode } from 'react'
import katex from 'katex'
import 'katex/dist/katex.min.css'

export type CitationMeta = {
  index: number
  title: string
  pageNumber: number
  sectionHint?: string | null
}

/**
 * Small Markdown subset for study answers: headings, paragraphs, lists, bold/italic, inline code,
 * $math$ / $$math$$, and [n] citations.
 */
export function StudyMarkdown({
  markdown,
  citations = [],
  className = '',
}: {
  markdown: string
  citations?: CitationMeta[]
  className?: string
}) {
  const blocks = splitBlocks(markdown.trim())
  const byIndex = new Map(citations.map((citation) => [citation.index, citation]))
  return (
    <div className={`study-md space-y-4 text-base leading-relaxed text-ink ${className}`}>
      {blocks.map((block, index) => (
        <Block key={`${block.type}-${index}`} block={block} citations={byIndex} />
      ))}
    </div>
  )
}

type Block =
  | { type: 'heading'; level: 2 | 3; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'ul'; items: string[] }
  | { type: 'ol'; items: string[] }

function Block({
  block,
  citations,
}: {
  block: Block
  citations: Map<number, CitationMeta>
}) {
  if (block.type === 'heading') {
    const Tag = block.level === 2 ? 'h2' : 'h3'
    return (
      <Tag
        className={
          block.level === 2
            ? 'font-display text-xl font-semibold tracking-tight text-ink'
            : 'text-sm font-semibold uppercase tracking-[0.12em] text-muted'
        }
      >
        {renderInline(block.text, citations)}
      </Tag>
    )
  }
  if (block.type === 'ul') {
    return (
      <ul className="flex list-disc flex-col gap-2 pl-5 marker:text-accent">
        {block.items.map((item, index) => (
          <li key={index}>{renderInline(item, citations)}</li>
        ))}
      </ul>
    )
  }
  if (block.type === 'ol') {
    return (
      <ol className="flex list-decimal flex-col gap-2 pl-5 marker:font-medium marker:text-accent-deep">
        {block.items.map((item, index) => (
          <li key={index}>{renderInline(item, citations)}</li>
        ))}
      </ol>
    )
  }
  return <p>{renderInline(block.text, citations)}</p>
}

function splitBlocks(markdown: string): Block[] {
  if (!markdown) {
    return []
  }
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) {
      i += 1
      continue
    }
    const heading = /^(#{2,3})\s+(.+)$/.exec(line.trim())
    if (heading) {
      blocks.push({
        type: 'heading',
        level: heading[1].length === 2 ? 2 : 3,
        text: heading[2].trim(),
      })
      i += 1
      continue
    }
    if (/^[-*]\s+/.test(line.trim())) {
      const items: string[] = []
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''))
        i += 1
      }
      blocks.push({ type: 'ul', items })
      continue
    }
    if (/^\d+\.\s+/.test(line.trim())) {
      const items: string[] = []
      while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+\.\s+/, ''))
        i += 1
      }
      blocks.push({ type: 'ol', items })
      continue
    }
    const paragraph: string[] = []
    while (
      i < lines.length &&
      lines[i].trim() &&
      !/^(#{2,3})\s+/.test(lines[i].trim()) &&
      !/^[-*]\s+/.test(lines[i].trim()) &&
      !/^\d+\.\s+/.test(lines[i].trim())
    ) {
      paragraph.push(lines[i].trim())
      i += 1
    }
    blocks.push({ type: 'paragraph', text: paragraph.join(' ') })
  }
  return blocks
}

function renderInline(text: string, citations: Map<number, CitationMeta>): ReactNode[] {
  const nodes: ReactNode[] = []
  const pattern = /(\$\$[^$]+\$\$|\$[^$\n]+\$|\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[\d+\])/g
  let last = 0
  let match: RegExpExecArray | null
  let key = 0
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(text.slice(last, match.index))
    }
    const token = match[0]
    if (token.startsWith('$$') || (token.startsWith('$') && token.endsWith('$'))) {
      const display = token.startsWith('$$')
      const tex = display ? token.slice(2, -2).trim() : token.slice(1, -1).trim()
      try {
        const html = katex.renderToString(tex, { throwOnError: false, displayMode: display })
        nodes.push(
          <span
            key={key++}
            className={display ? 'my-2 block overflow-x-auto text-[1.05em]' : 'mx-0.5 inline-block max-w-full overflow-x-auto align-middle'}
            dangerouslySetInnerHTML={{ __html: html }}
          />,
        )
      } catch {
        nodes.push(token)
      }
    } else if (token.startsWith('**')) {
      nodes.push(
        <strong key={key++} className="font-semibold text-ink-soft">
          {token.slice(2, -2)}
        </strong>,
      )
    } else if (token.startsWith('*')) {
      nodes.push(
        <em key={key++} className="italic text-ink-soft">
          {token.slice(1, -1)}
        </em>,
      )
    } else if (token.startsWith('`')) {
      nodes.push(
        <code key={key++} className="rounded bg-mist px-1.5 py-0.5 font-mono text-[0.9em] text-ink-soft">
          {token.slice(1, -1)}
        </code>,
      )
    } else {
      const n = Number(token.slice(1, -1))
      const meta = citations.get(n)
      if (!meta) {
        last = match.index + token.length
        continue
      }
      const label = meta.sectionHint
        ? `Source ${n}: ${meta.title}, ${meta.sectionHint}, page ${meta.pageNumber}`
        : `Source ${n}: ${meta.title}, page ${meta.pageNumber}`
      nodes.push(
        <a
          key={key++}
          href={`#qa-source-${n}`}
          title={label}
          aria-label={label}
          className="mx-0.5 inline-flex h-5 min-w-5 align-middle items-center justify-center rounded-md bg-accent/15 px-1 text-xs font-semibold text-accent-deep no-underline hover:bg-accent/25"
          onClick={(event) => {
            event.preventDefault()
            const target = document.getElementById(`qa-source-${n}`)
            if (target) {
              target.scrollIntoView({ behavior: 'smooth', block: 'center' })
              if (window.location.hash !== `#qa-source-${n}`) {
                window.history.replaceState(null, '', `#qa-source-${n}`)
              }
              window.dispatchEvent(new Event('hashchange'))
            }
          }}
        >
          {n}
        </a>,
      )
    }
    last = match.index + token.length
  }
  if (last < text.length) {
    nodes.push(text.slice(last))
  }
  return nodes
}
