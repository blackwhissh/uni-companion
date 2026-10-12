const DRAFT_WORD = /\b(entwurf|draft|untitled|wip|tmp|temp)\b/i

export type DraftTitleMatch = { matched: true; trigger: string } | { matched: false }

export function matchDraftTitle(title: string): DraftTitleMatch {
  const trimmed = title.trim()
  const match = trimmed.match(DRAFT_WORD)
  if (!match) {
    return { matched: false }
  }
  return { matched: true, trigger: match[1] }
}

export function looksLikeDraftTitle(title: string): boolean {
  return matchDraftTitle(title).matched
}

export function draftTitleWarning(title: string): string | null {
  const hit = matchDraftTitle(title)
  if (!hit.matched) {
    return null
  }
  return `This title looks like a draft (matched “${hit.trigger}”).`
}
