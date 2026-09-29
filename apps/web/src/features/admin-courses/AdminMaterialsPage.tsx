import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router'
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
  const [formOpen, setFormOpen] = useState(false)
  const canManage = course.data?.owned === true || platformAdmin

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
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not upload the PDF.')
    },
  })

  const visibility = useMutation({
    mutationFn: ({ id, next }: { id: string; next: Material['visibility'] }) => setMaterialVisibility(id, next),
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
      setError(err instanceof ApiError ? err.message : 'Could not update visibility.')
    },
  })

  const remove = useMutation({
    mutationFn: deleteMaterial,
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['materials', courseId] })
    },
    onError: (err) => {
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

  return (
    <Page>
      <BackLink to="/admin/courses">Course console</BackLink>
      <div className="mt-4">
        <PageHeader
          eyebrow="Materials"
          title={course.data?.title ?? 'Materials'}
          description="Upload lecture PDFs, wait until processing is Ready, then publish so enrolled students can study. Click a row or Download to save the original PDF. Professors manage their own materials; platform admins can upload, publish, unpublish, or delete on any course."
          actions={
            canManage && !formOpen ? (
              <Button type="button" onClick={() => setFormOpen(true)}>
                Upload PDF
              </Button>
            ) : null
          }
        />
      </div>

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

      {materials.data?.length === 0 ? (
        <EmptyState
          title="No materials yet."
          description="Use Upload PDF to add a lecture file. Processing and visibility are separate — Ready does not mean students can see it until you Publish."
        />
      ) : null}

      {materials.data && materials.data.length > 0 ? (
        <section className="mt-10 animate-rise-delay">
          <SectionLabel>Library</SectionLabel>
          <ul className="mt-4 flex flex-col gap-3">
            {materials.data.map((material) => (
              <li key={material.id} className="surface-panel relative rounded-2xl px-5 py-4 transition duration-300 hover:-translate-y-0.5 hover:border-accent/35">
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
                        disabled={visibility.isPending}
                        onClick={() =>
                          visibility.mutate({
                            id: material.id,
                            next: material.visibility === 'PUBLISHED' ? 'UNPUBLISHED' : 'PUBLISHED',
                          })
                        }
                      >
                        {material.visibility === 'PUBLISHED' ? 'Unpublish' : 'Publish'}
                      </Button>
                    ) : null}
                    {canManage ? (
                      <Button
                        type="button"
                        variant="danger"
                        disabled={remove.isPending}
                        onClick={() => remove.mutate(material.id)}
                      >
                        Delete
                      </Button>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </Page>
  )
}

function labelFor(status: Material['processingStatus']) {
  switch (status) {
    case 'UPLOADED':
      return 'Uploaded'
    case 'PROCESSING':
      return 'Processing'
    case 'READY':
      return 'Ready'
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
