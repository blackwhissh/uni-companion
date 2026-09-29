import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { Field } from '../auth/AuthForm.tsx'
import { useAuth } from '../auth/use-auth.ts'
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
import { getCourse } from '../courses/course-api.ts'
import {
  deleteMaterial,
  downloadMaterial,
  listMaterials,
  setMaterialVisibility,
  uploadMaterial,
} from '../courses/material-api.ts'
import type { Material } from '../courses/material-api.ts'

export function AdminMaterialsPage() {
  const { courseId = '' } = useParams()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const platformAdmin = user?.roles.includes('ADMIN') === true
  const course = useQuery({ queryKey: ['courses', courseId], queryFn: () => getCourse(courseId) })
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    refetchInterval: (query) => {
      const pending = query.state.data?.some(
        (material) => material.processingStatus === 'UPLOADED' || material.processingStatus === 'PROCESSING',
      )
      return pending ? 2000 : false
    },
  })
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const canManage = course.data?.owned === true || platformAdmin
  const courseUnpublished = course.data?.visibility === 'UNPUBLISHED'

  function resetForm() {
    setTitle('')
    setFile(null)
    setFileKey((current) => current + 1)
    setError(null)
  }

  const upload = useMutation({
    mutationFn: () => uploadMaterial(courseId, file as File, title),
    onSuccess: async () => {
      resetForm()
      setFormOpen(false)
      setNotice('PDF uploaded. Wait until status is Indexed, then publish.')
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not upload the PDF.')
    },
  })

  const visibility = useMutation({
    mutationFn: ({ id, next }: { id: string; next: Material['visibility']; title: string }) =>
      setMaterialVisibility(id, next),
    onSuccess: async (_data, variables) => {
      setError(null)
      setBusyId(null)
      setNotice(
        variables.next === 'PUBLISHED'
          ? `Published “${variables.title}”. Enrolled students can use it in study modes.`
          : `Unpublished “${variables.title}”. Students can no longer select it.`,
      )
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
      setBusyId(null)
      setError(err instanceof ApiError ? err.message : 'Could not update visibility.')
    },
  })

  const remove = useMutation({
    mutationFn: ({ id }: { id: string; title: string }) => deleteMaterial(id),
    onSuccess: async (_data, variables) => {
      setError(null)
      setBusyId(null)
      setNotice(`Deleted “${variables.title}”.`)
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
      setBusyId(null)
      setError(err instanceof ApiError ? err.message : 'Could not delete the material.')
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!file) {
      setError('Choose a PDF.')
      return
    }
    upload.mutate()
  }

  function closeForm() {
    resetForm()
    setFormOpen(false)
  }

  function requestPublishToggle(material: Material) {
    const publishing = material.visibility !== 'PUBLISHED'
    if (publishing && material.processingStatus !== 'READY') {
      setError('Wait until this PDF is Indexed before publishing.')
      return
    }
    if (publishing) {
      const draftNote = looksLikeDraftTitle(material.title)
        ? `\n\nThis title looks like a draft (“${material.title}”). Publish anyway?`
        : ''
      const courseNote = courseUnpublished
        ? '\n\nNote: this course is still unpublished, so students cannot open the course yet even after you publish the PDF.'
        : ''
      if (
        !window.confirm(
          `Publish “${material.title}” for enrolled students?${draftNote}${courseNote}`,
        )
      ) {
        return
      }
    } else if (!window.confirm(`Unpublish “${material.title}”? Students will lose access in study modes.`)) {
      return
    }
    setNotice(null)
    setBusyId(material.id)
    visibility.mutate({
      id: material.id,
      next: publishing ? 'PUBLISHED' : 'UNPUBLISHED',
      title: material.title,
    })
  }

  function requestDelete(material: Material) {
    if (
      !window.confirm(
        `Delete “${material.title}” permanently? This cannot be undone.`,
      )
    ) {
      return
    }
    setNotice(null)
    setBusyId(material.id)
    remove.mutate({ id: material.id, title: material.title })
  }

  return (
    <Page>
      <BackLink to="/admin/courses">Course console</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow="Materials"
          title={course.data?.title ?? 'Materials'}
          description="Upload lecture PDFs, wait until they are Indexed, then publish so enrolled students can study. Indexed means the PDF is searchable — it is not student-visible until Published."
          actions={
            <div className="flex flex-wrap gap-2">
              {course.data?.visibility === 'PUBLISHED' || canManage ? (
                <ButtonLinkish to={`/courses/${courseId}`}>
                  {courseUnpublished ? 'Preview as student (course still unpublished)' : 'Preview as student'}
                </ButtonLinkish>
              ) : null}
              {canManage && !formOpen ? (
                <Button type="button" onClick={() => setFormOpen(true)}>
                  Upload PDF
                </Button>
              ) : null}
            </div>
          }
        />
      </div>

      {courseUnpublished ? (
        <div className="mt-6">
          <Alert>
            This course is unpublished. Publishing a PDF alone will not open the course for students until you publish the course.
          </Alert>
        </div>
      ) : null}

      {canManage && formOpen ? (
        <Panel className="mt-8 animate-rise">
          <div className="flex items-center justify-between gap-3">
            <SectionLabel>Upload PDF</SectionLabel>
            <Button type="button" variant="ghost" onClick={closeForm}>
              Cancel
            </Button>
          </div>
          <form className="mt-4 flex max-w-md flex-col gap-4" onSubmit={onSubmit}>
            <Field label="Title" type="text" value={title} onChange={setTitle} autoComplete="off" />
            <label className="flex flex-col gap-1.5 text-sm font-medium text-ink-soft">
              PDF
              <input
                key={fileKey}
                type="file"
                accept="application/pdf,.pdf"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                className="rounded-lg border border-dashed border-line bg-white/60 px-3 py-3 text-base font-normal text-ink file:mr-3 file:rounded-md file:border-0 file:bg-mist file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-ink"
              />
            </label>
            {error ? <Alert>{error}</Alert> : null}
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={upload.isPending}>
                Save PDF
              </Button>
              <Button type="button" variant="secondary" onClick={closeForm}>
                Cancel
              </Button>
            </div>
          </form>
        </Panel>
      ) : null}

      {!formOpen && error ? (
        <div className="mt-8">
          <Alert>{error}</Alert>
        </div>
      ) : null}
      {notice ? (
        <Panel className="mt-6">
          <p className="text-sm text-ink-soft">{notice}</p>
        </Panel>
      ) : null}

      {materials.data?.length === 0 ? (
        <EmptyState
          title="No materials yet."
          description="Use Upload PDF to add a lecture file. Indexed means searchable; Published means students can select it."
        />
      ) : null}

      {materials.data && materials.data.length > 0 ? (
        <section className="mt-10 animate-rise-delay">
          <SectionLabel>Library</SectionLabel>
          <ul className="mt-4 flex flex-col gap-3">
            {materials.data.map((material) => {
              const indexed = material.processingStatus === 'READY'
              const rowBusy = busyId === material.id && (visibility.isPending || remove.isPending)
              return (
                <li
                  key={material.id}
                  className="surface-panel relative rounded-2xl px-5 py-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35"
                >
                  <button
                    type="button"
                    aria-label={`Download ${material.title}`}
                    onClick={() =>
                      downloadMaterial(material).catch((err) => {
                        setError(err instanceof ApiError ? err.message : 'Could not download the PDF.')
                      })
                    }
                    className="absolute inset-0 cursor-pointer rounded-2xl"
                  />
                  <div className="pointer-events-none relative z-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="font-medium text-ink">{material.title}</p>
                      <p className="mt-1 text-sm text-muted">
                        {material.fileName} · {labelFor(material.processingStatus)} ·{' '}
                        {material.visibility === 'PUBLISHED' ? 'Published' : 'Unpublished'}
                      </p>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <StatusPill
                          tone={processingTone(material.processingStatus)}
                          pulse={
                            material.processingStatus === 'PROCESSING' || material.processingStatus === 'UPLOADED'
                          }
                        >
                          {labelFor(material.processingStatus)}
                        </StatusPill>
                        <StatusPill tone={material.visibility === 'PUBLISHED' ? 'ok' : 'warn'}>
                          {material.visibility === 'PUBLISHED' ? 'Published' : 'Unpublished'}
                        </StatusPill>
                      </div>
                      {indexed && material.visibility !== 'PUBLISHED' ? (
                        <p className="mt-2 text-xs text-muted">
                          Indexed for search — students cannot see it until you Publish.
                        </p>
                      ) : null}
                      {material.visibility === 'PUBLISHED' && courseUnpublished ? (
                        <p className="mt-2 text-xs text-warn">
                          Material is published, but the course itself is still unpublished.
                        </p>
                      ) : null}
                      {material.failureReason ? (
                        <p className="mt-2 text-sm text-danger">{material.failureReason}</p>
                      ) : null}
                    </div>
                    <div className="pointer-events-auto flex flex-wrap gap-2">
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() =>
                          downloadMaterial(material).catch((err) => {
                            setError(err instanceof ApiError ? err.message : 'Could not download the PDF.')
                          })
                        }
                      >
                        Download
                      </Button>
                      {canManage ? (
                        <Button
                          type="button"
                          variant={material.visibility === 'PUBLISHED' ? 'warn' : 'success'}
                          disabled={rowBusy || visibility.isPending || (!indexed && material.visibility !== 'PUBLISHED')}
                          title={
                            !indexed && material.visibility !== 'PUBLISHED'
                              ? 'Wait until Indexed before publishing'
                              : undefined
                          }
                          onClick={() => requestPublishToggle(material)}
                        >
                          {material.visibility === 'PUBLISHED' ? 'Unpublish' : 'Publish'}
                        </Button>
                      ) : null}
                      {canManage ? (
                        <Button
                          type="button"
                          variant="danger"
                          className="sm:ml-2"
                          disabled={rowBusy || remove.isPending}
                          onClick={() => requestDelete(material)}
                        >
                          Delete
                        </Button>
                      ) : null}
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}
    </Page>
  )
}

function looksLikeDraftTitle(title: string) {
  return /\b(entwurf|draft|untitled|wip|tmp|temp|test)\b/i.test(title.trim())
}

function ButtonLinkish({ to, children }: { to: string; children: string }) {
  return (
    <Link
      to={to}
      className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-ink/20 bg-white/70 px-3.5 py-2 text-sm font-medium text-ink transition duration-200 hover:border-ink/40 hover:bg-white"
    >
      {children}
    </Link>
  )
}

function labelFor(status: Material['processingStatus']) {
  switch (status) {
    case 'UPLOADED':
      return 'Uploaded'
    case 'PROCESSING':
      return 'Processing'
    case 'READY':
      return 'Indexed'
    case 'FAILED':
      return 'Failed'
  }
}

function processingTone(status: Material['processingStatus']): 'neutral' | 'ok' | 'warn' | 'danger' | 'accent' {
  switch (status) {
    case 'READY':
      return 'ok'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
    case 'UPLOADED':
      return 'accent'
  }
}
