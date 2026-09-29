import { Link } from 'react-router'
import { useAuth } from '../features/auth/use-auth.ts'
import { homePathFor } from '../features/auth/home-path.ts'
import { ButtonLink } from '../shared/ui/ui.tsx'

export function HomePage() {
  const { user } = useAuth()
  const ctaTo = user ? homePathFor(user.roles) : '/register'
  const ctaLabel = user ? 'Open your workspace' : 'Start studying'
  const secondaryTo = user ? '/courses' : '/login'
  const secondaryLabel = user ? 'Browse courses' : 'Log in'

  return (
    <main className="relative overflow-hidden">
      <div className="pointer-events-none absolute inset-0 study-grid opacity-60" aria-hidden />
      <div className="relative mx-auto flex min-h-[calc(100vh-4.5rem)] max-w-5xl flex-col justify-center px-6 py-16 lg:py-20">
        <div className="max-w-2xl animate-rise">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-accent-deep">Universität Siegen</p>
          <h1 className="font-display mt-4 text-5xl font-semibold tracking-tight text-ink sm:text-6xl lg:text-7xl">
            Uni Companion
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-muted sm:text-xl">
            Official lecture materials become grounded Q&A, flashcards, and quizzes — then find classmates studying
            the same modules.
          </p>
          <div className="mt-8 flex flex-wrap gap-3 animate-rise-delay">
            <ButtonLink to={ctaTo} className="!px-5 !py-2.5 !text-base">
              {ctaLabel}
            </ButtonLink>
            <ButtonLink to={secondaryTo} variant="secondary" className="!px-5 !py-2.5 !text-base">
              {secondaryLabel}
            </ButtonLink>
          </div>
        </div>

        <div className="mt-16 grid gap-4 sm:grid-cols-3 animate-rise-delay-2">
          <Feature
            title="Grounded study"
            body="Ask questions against published lecture PDFs — answers stay tied to your course."
          />
          <Feature title="Active practice" body="Turn scripts into flashcards and quizzes when materials are ready." />
          <Feature
            title="Campus matching"
            body="Opt in to find peers enrolled in the same modules — no hallway scavenger hunt."
          />
        </div>

        {!user ? (
          <p className="mt-10 text-sm text-muted">
            Already enrolled on campus?{' '}
            <Link to="/login" className="font-medium text-ink underline decoration-accent/50 underline-offset-4">
              Log in
            </Link>
          </p>
        ) : null}
      </div>
    </main>
  )
}

function Feature({ title, body }: { title: string; body: string }) {
  return (
    <article className="surface-panel rounded-2xl p-5 transition duration-300 hover:-translate-y-0.5 hover:border-accent/40">
      <h2 className="font-display text-lg font-semibold text-ink">{title}</h2>
      <p className="mt-2 text-sm leading-relaxed text-muted">{body}</p>
    </article>
  )
}
