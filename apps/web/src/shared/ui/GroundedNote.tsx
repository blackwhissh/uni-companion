export function GroundedNote({ className = '' }: { className?: string }) {
  return (
    <p className={`text-sm leading-relaxed text-muted ${className}`}>
      Written from your published lecture PDFs. Open the cited page if you want to double-check.
    </p>
  )
}
