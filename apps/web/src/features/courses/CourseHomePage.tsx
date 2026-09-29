import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../shared/api/identity-client.ts'
import {
  Alert,
  BackLink,
  Button,
  EmptyState,
  Page,
  PageHeader,
  Panel,
  SectionLabel,
  StatusPill,
} from '../../shared/ui/ui.tsx'
import { enrollInCourse, getCourse, unenrollFromCourse } from './course-api.ts'
import { downloadMaterial, listMaterials, openMaterialPreview } from './material-api.ts'
import type { Material } from './material-api.ts'

export function CourseHomePage() {
  const { courseId = '' } = useParams()
  const queryClient = useQueryClient()
  const course = useQuery({ queryKey: ['courses', courseId], queryFn: () => getCourse(courseId), retry: false })
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: course.data?.enrolled === true,
    retry: false,
  })
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['courses'] })
    await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
  }
  const [fileError, setFileError] = useState<string | null>(null)
  const [actionNotice, setActionNotice] = useState<string | null>(null)
  const [preview, setPreview] = useState<{ material: Material; url: string } | null>(null)
  const [previewLoadingId, setPreviewLoadingId] = useState<string | null>(null)
  const enroll = useMutation({
    mutationFn: () => enrollInCourse(courseId),
    onSuccess: async () => {
      setActionNotice('Enrolled.')
      await refresh()
    },
  })
  const unenroll = useMutation({
    mutationFn: () => unenrollFromCourse(courseId),
    onSuccess: async () => {
      setActionNotice('Unenrolled.')
      await refresh()
    },
  })
  const unavailable = course.error instanceof ApiError && course.error.status === 404
  const readyCount =
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY')
      .length ?? 0

  async function onDownload(material: Material) {
    try {
      setFileError(null)
      await downloadMaterial(material)
    } catch (err) {
      setFileError(err instanceof ApiError ? err.message : 'Could not download the PDF.')
    }
  }

  async function onPreview(material: Material) {
    try {
      setFileError(null)
      setPreviewLoadingId(material.id)
      const url = await openMaterialPreview(material.id)
      setPreview((current) => {
        if (current) {
          URL.revokeObjectURL(current.url)
        }
        return { material, url }
      })
    } catch (err) {
      setFileError(err instanceof ApiError ? err.message : 'Could not open the PDF preview.')
    } finally {
      setPreviewLoadingId(null)
    }
  }

  function closePreview() {
    setPreview((current) => {
      if (current) {
        URL.revokeObjectURL(current.url)
      }
      return null
    })
  }

  return (
    <Page>
      <BackLink to="/courses">Your courses</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Course'}
          title={course.data?.title ?? 'Course'}
          description={
            course.data?.enrolled
              ? 'Study from published materials. Peer matching will arrive later with an explicit consent step.'
              : 'Enroll to unlock materials and study tools for this module.'
          }
          actions={
            course.data && !course.data.enrolled ? (
              <Button type="button" variant="success" disabled={enroll.isPending} onClick={() => enroll.mutate()}>
                {enroll.isPending ? 'Working…' : 'Enroll'}
              </Button>
            ) : course.data?.enrolled ? (
              <Button
                type="button"
                variant="warn"
                disabled={unenroll.isPending}
                onClick={() => {
                  if (window.confirm(`Unenroll from “${course.data?.title ?? 'this course'}”?`)) {
                    unenroll.mutate()
                  }
                }}
              >
                {unenroll.isPending ? 'Working…' : 'Unenroll'}
              </Button>
            ) : null
          }
        />
      </div>

      {actionNotice ? (
        <p className="mt-4 text-sm font-medium text-ok" role="status">
          {actionNotice}
        </p>
      ) : null}

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {course.data?.enrolled ? (
        <>
          <p className="mt-3">
            <StatusPill tone="ok">Enrolled</StatusPill>
          </p>

          <section className="mt-10 animate-rise-delay">
            <SectionLabel>Study modes</SectionLabel>
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <StudyMode
                title="Q&A"
                body="Ask questions grounded in published lecture PDFs."
                state={readyCount > 0 ? 'Open' : 'Needs materials'}
                to={readyCount > 0 ? `/courses/${courseId}/qa` : undefined}
              />
              <StudyMode
                title="Flashcards"
                body="Drill key ideas extracted from your course pack."
                state={readyCount > 0 ? 'Open' : 'Needs materials'}
                to={readyCount > 0 ? `/courses/${courseId}/flashcards` : undefined}
              />
              <StudyMode
                title="Quiz"
                body="Check understanding with generated practice questions."
                state={readyCount > 0 ? 'Open' : 'Needs materials'}
                to={readyCount > 0 ? `/courses/${courseId}/quiz` : undefined}
              />
            </div>
          </section>

          <section className="mt-10 animate-rise-delay-2">
            <div className="flex items-end justify-between gap-3">
              <SectionLabel>Materials</SectionLabel>
              {materials.isLoading ? <p className="text-sm text-muted">Loading materials…</p> : null}
            </div>

            {fileError ? (
              <div className="mt-4">
                <Alert>{fileError}</Alert>
              </div>
            ) : null}

            {materials.data?.length === 0 ? (
              <EmptyState
                title="No materials yet."
                description="Your course admin has not published lecture PDFs for this module. Check back after they go live."
              />
            ) : null}

            <ul className="mt-4 flex flex-col gap-3">
              {materials.data?.map((material) => (
                <li key={material.id}>
                  <article className="surface-panel flex flex-wrap items-center justify-between gap-3 rounded-2xl px-5 py-4 transition duration-300 hover:border-accent/35">
                    <button
                      type="button"
                      aria-label={`Preview ${material.title}`}
                      disabled={previewLoadingId === material.id}
                      onClick={() => onPreview(material)}
                      className="min-w-0 flex-1 cursor-pointer rounded-lg text-left outline-none transition focus-visible:ring-2 focus-visible:ring-accent/40 disabled:cursor-wait disabled:opacity-70"
                    >
                      <p className="font-medium text-ink">{material.title}</p>
                      <p className="mt-1 text-sm text-muted">
                        {previewLoadingId === material.id ? 'Opening preview…' : material.fileName}
                      </p>
                    </button>
                    <div className="flex shrink-0 flex-wrap items-center gap-2">
                      <StatusPill tone="accent">Published</StatusPill>
                      <Button
                        type="button"
                        variant="secondary"
                        aria-label={`Download ${material.title}`}
                        onClick={() => onDownload(material)}
                      >
                        Download
                      </Button>
                    </div>
                  </article>
                </li>
              ))}
            </ul>

            {preview ? (
              <div
                className="fixed inset-0 z-50 flex items-center justify-center bg-ink/45 p-4 backdrop-blur-[2px]"
                role="presentation"
                onClick={closePreview}
              >
                <div
                  role="dialog"
                  aria-modal="true"
                  aria-label={`Preview ${preview.material.title}`}
                  className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-line bg-white shadow-[0_24px_60px_rgb(7_52_60_/_0.28)]"
                  onClick={(event) => event.stopPropagation()}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-ink">{preview.material.title}</p>
                      <p className="mt-0.5 truncate text-sm text-muted">{preview.material.fileName}</p>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button type="button" variant="secondary" onClick={() => onDownload(preview.material)}>
                        Download
                      </Button>
                      <Button type="button" variant="ghost" onClick={closePreview}>
                        Close
                      </Button>
                    </div>
                  </div>
                  <iframe title={preview.material.title} src={preview.url} className="min-h-[70vh] w-full flex-1 bg-mist" />
                </div>
              </div>
            ) : null}
          </section>

          <Panel className="mt-10">
            <SectionLabel>Peer matching</SectionLabel>
            <p className="mt-3 text-sm leading-relaxed text-muted">
              Study-partner matching is not available yet. When it launches, you will see a clear consent screen
              before anything about you is shared with classmates.
            </p>
          </Panel>
        </>
      ) : null}

      {course.data && !course.data.enrolled && !unavailable ? (
        <EmptyState
          title="You are not enrolled yet"
          description="Join this course to see published materials and unlock study modes. Use Enroll above to get started."
        />
      ) : null}
    </Page>
  )
}

function StudyMode({
  title,
  body,
  state,
  to,
}: {
  title: string
  body: string
  state: string
  to?: string
}) {
  const content = (
    <>
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-display text-lg font-semibold text-ink">{title}</h3>
        <StatusPill tone={to ? 'ok' : 'neutral'}>{state}</StatusPill>
      </div>
      <p className="mt-2 text-sm leading-relaxed text-muted">{body}</p>
    </>
  )

  if (to) {
    return (
      <Link
        to={to}
        className="surface-panel block rounded-2xl p-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35"
      >
        {content}
      </Link>
    )
  }

  return (
    <article className="surface-panel rounded-2xl p-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35">
      {content}
    </article>
  )
}
