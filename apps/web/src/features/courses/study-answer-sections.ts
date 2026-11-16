export type StudySectionKind = 'result' | 'walkthrough' | 'terms' | 'plain'

export type StudySection = {
  kind: StudySectionKind
  heading: string | null
  body: string
}

const HEADING = /^(#{2,3})\s+(.+)$/

/**
 * Splits a tutor reply into the result, the walkthrough, and optional terms
 * so the chat can show the answer before the explanation.
 */
export function splitStudyAnswer(markdown: string): StudySection[] {
  const text = markdown.replace(/\r\n/g, '\n').trim()
  if (!text) {
    return []
  }
  const chunks: { heading: string | null; lines: string[] }[] = []
  let current: { heading: string | null; lines: string[] } | null = null
  for (const line of text.split('\n')) {
    const match = HEADING.exec(line.trim())
    if (match) {
      if (current) {
        chunks.push(current)
      }
      current = { heading: match[2].trim(), lines: [] }
      continue
    }
    if (!current) {
      current = { heading: null, lines: [] }
    }
    current.lines.push(line)
  }
  if (current) {
    chunks.push(current)
  }
  return chunks.map((chunk) => ({
    kind: kindFor(chunk.heading),
    heading: chunk.heading,
    body: chunk.lines.join('\n').trim(),
  }))
}

export function sectionLabel(section: StudySection): string | null {
  const german = isGermanHeading(section.heading)
  if (section.kind === 'result') {
    return german ? 'Das Ergebnis' : 'The result'
  }
  if (section.kind === 'walkthrough') {
    return german ? 'Schritt für Schritt' : 'Walk through it'
  }
  if (section.kind === 'terms') {
    return german ? 'Zum Merken' : 'Remember'
  }
  return section.heading
}

function kindFor(heading: string | null): StudySectionKind {
  const normalized = normalize(heading)
  if (normalized === 'direct answer' || normalized === 'direkte antwort' || normalized === 'antwort') {
    return 'result'
  }
  if (normalized === 'explanation' || normalized === 'erklärung') {
    return 'walkthrough'
  }
  if (normalized === 'key terms' || normalized === 'schlüsselbegriffe') {
    return 'terms'
  }
  return 'plain'
}

function isGermanHeading(heading: string | null): boolean {
  const normalized = normalize(heading)
  return (
    normalized === 'antwort' ||
    normalized === 'direkte antwort' ||
    normalized === 'erklärung' ||
    normalized === 'schlüsselbegriffe'
  )
}

function normalize(heading: string | null): string {
  return (heading ?? '').toLowerCase().replace(/\s+/g, ' ').trim()
}
