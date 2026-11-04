export function GroundedNote({ className = '' }: { className?: string }) {
  return (
    <p className={`text-sm leading-relaxed text-muted ${className}`}>
      Drafted from published lecture PDFs. Open the cited page before you rely on it.
    </p>
  )
}
