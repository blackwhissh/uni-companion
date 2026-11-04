import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/use-auth.ts'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  Button,
  ButtonLink,
  ConfirmDialog,
  EmptyState,
  Page,
  PageHeader,
  SectionLabel,
  StatusPill,
} from '../../shared/ui/ui.tsx'
import { listAdminAccounts, listAdminCourses, setCourseAdmin } from './admin-api.ts'
import type { AdminAccount } from './admin-api.ts'

type PendingConfirm = {
  account: AdminAccount
  grant: boolean
  title: string
  body: string
  confirmLabel: string
} | null

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Platform admin',
  COURSE_ADMIN: 'Course admin',
  STUDENT: 'Student',
}

export function AdminPanelPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: ['admin', 'accounts'], queryFn: listAdminAccounts })
  const courses = useQuery({ queryKey: ['admin', 'courses'], queryFn: listAdminCourses })
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<PendingConfirm>(null)

  const updateRole = useMutation({
    mutationFn: ({ id, grant }: { id: string; grant: boolean }) => setCourseAdmin(id, grant),
    onSuccess: async (account, variables) => {
      setError(null)
      setNotice(
        variables.grant
          ? `${account.displayName} can open the course console after they log in again.`
          : `${account.displayName} is no longer a course admin. That takes effect the next time they log in.`,
      )
      await queryClient.invalidateQueries({ queryKey: ['admin', 'accounts'] })
    },
    onError: (err) => {
      setNotice(null)
      setError(err instanceof ApiError ? err.message : 'Could not update that account.')
    },
  })

  function requestChange(account: AdminAccount, grant: boolean) {
    setConfirm({
      account,
      grant,
      title: grant ? 'Grant course admin?' : 'Remove course admin?',
      body: grant
        ? `Grant course admin to ${account.displayName}? They can create courses and upload lecture PDFs after they log in again.`
        : `Remove course admin from ${account.displayName}? Their account stays. The course console disappears the next time they log in.`,
      confirmLabel: grant ? 'Grant course admin' : 'Remove course admin',
    })
  }

  const namesById = new Map(accounts.data?.map((account) => [account.id, account.displayName]) ?? [])
  const courseAdminCount = accounts.data?.filter((account) => account.roles.includes('COURSE_ADMIN')).length ?? 0
  const publishedCount = courses.data?.filter((course) => course.visibility === 'PUBLISHED').length ?? 0

  return (
    <Page>
      <PageHeader
        eyebrow="Platform"
        title="Admin panel"
        description="See every account and how each course is doing. A course-admin change takes effect the next time that person logs in."
        actions={<ButtonLink to="/admin/courses" variant="secondary">Course console</ButtonLink>}
      />

      {notice ? (
        <p className="mt-6 text-sm font-medium text-ok" role="status">
          {notice}
        </p>
      ) : null}
      {error ? (
        <div className="mt-6">
          <Alert>{error}</Alert>
        </div>
      ) : null}

      <section className="mt-8 grid gap-3 sm:grid-cols-3">
        <Summary label="Accounts" value={accounts.data ? String(accounts.data.length) : '…'} />
        <Summary label="Course admins" value={accounts.data ? String(courseAdminCount) : '…'} />
        <Summary
          label="Published courses"
          value={courses.data ? `${publishedCount} of ${courses.data.length}` : '…'}
        />
      </section>

      <section className="mt-10">
        <SectionLabel>Accounts</SectionLabel>
        {accounts.isLoading ? <p className="mt-4 animate-pulse-soft text-muted">Loading accounts…</p> : null}
        {accounts.isError ? (
          <div className="mt-4">
            <Alert>{accounts.error instanceof ApiError ? accounts.error.message : 'Could not load accounts.'}</Alert>
          </div>
        ) : null}
        {accounts.data?.length === 0 ? (
          <EmptyState title="No accounts yet." description="Accounts appear here after someone registers." />
        ) : null}
        {accounts.data && accounts.data.length > 0 ? (
          <ul className="mt-4 flex flex-col gap-3">
            {accounts.data.map((account) => {
              const courseAdmin = account.roles.includes('COURSE_ADMIN')
              const yours = account.id === user?.id
              return (
                <li key={account.id} className="surface-panel rounded-2xl px-5 py-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-display text-lg font-semibold text-ink">{account.displayName}</p>
                        {yours ? <StatusPill tone="accent">You</StatusPill> : null}
                      </div>
                      <p className="mt-1 truncate text-sm text-muted">{account.email}</p>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {account.roles.map((role) => (
                          <StatusPill key={role} tone={role === 'ADMIN' ? 'accent' : role === 'COURSE_ADMIN' ? 'ok' : 'neutral'}>
                            {ROLE_LABELS[role] ?? role}
                          </StatusPill>
                        ))}
                      </div>
                    </div>
                    <Button
                      type="button"
                      variant={courseAdmin ? 'warn' : 'secondary'}
                      disabled={updateRole.isPending}
                      onClick={() => requestChange(account, !courseAdmin)}
                    >
                      {courseAdmin ? 'Remove course admin' : 'Grant course admin'}
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        ) : null}
      </section>

      <section className="mt-10">
        <SectionLabel>Courses</SectionLabel>
        {courses.isLoading ? <p className="mt-4 animate-pulse-soft text-muted">Loading courses…</p> : null}
        {courses.isError ? (
          <div className="mt-4">
            <Alert>{courses.error instanceof ApiError ? courses.error.message : 'Could not load courses.'}</Alert>
          </div>
        ) : null}
        {courses.data?.length === 0 ? (
          <EmptyState title="No courses yet." description="Create the first course from the course console." />
        ) : null}
        {courses.data && courses.data.length > 0 ? (
          <ul className="mt-4 flex flex-col gap-3">
            {courses.data.map((course) => {
              const published = course.visibility === 'PUBLISHED'
              const owner = namesById.get(course.ownerId) ?? 'Unknown owner'
              return (
                <li key={course.id} className="surface-panel rounded-2xl px-5 py-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-display text-lg font-semibold text-ink">{course.title}</p>
                    <StatusPill tone={published ? 'ok' : 'warn'}>{published ? 'Published' : 'Unpublished'}</StatusPill>
                    {course.failedMaterials > 0 ? (
                      <StatusPill tone="danger">{countLabel(course.failedMaterials, 'failed lecture')}</StatusPill>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-muted">
                    {course.code} · {course.term} · {owner}
                  </p>
                  <p className="mt-2 text-sm text-ink-soft">
                    {countLabel(course.enrollmentCount, 'student')} enrolled ·{' '}
                    {countLabel(course.publishedReadyMaterials, 'published lecture')}
                    {course.otherMaterials > 0 ? ` · ${countLabel(course.otherMaterials, 'other file')}` : ''}
                  </p>
                  <div className="mt-3">
                    <Link
                      to={`/admin/courses/${course.id}/materials`}
                      className="text-sm font-medium text-ink underline decoration-accent/50 underline-offset-4"
                    >
                      Open materials
                    </Link>
                  </div>
                </li>
              )
            })}
          </ul>
        ) : null}
      </section>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        body={confirm?.body ?? ''}
        confirmLabel={confirm?.confirmLabel ?? 'Confirm'}
        danger={confirm?.grant === false}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) {
            return
          }
          const next = confirm
          setConfirm(null)
          updateRole.mutate({ id: next.account.id, grant: next.grant })
        }}
      />
    </Page>
  )
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="surface-panel rounded-2xl px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted">{label}</p>
      <p className="mt-1 font-display text-2xl font-semibold text-ink">{value}</p>
    </div>
  )
}

function countLabel(count: number, singular: string) {
  return `${count} ${singular}${count === 1 ? '' : 's'}`
}
