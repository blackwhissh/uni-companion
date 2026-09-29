import { getAccessToken } from '../../shared/api/access-token.ts'
import { learningApiUrl } from '../../shared/api/config.ts'
import { ApiError } from '../../shared/api/identity-client.ts'
import { learningFetch, learningUpload } from '../../shared/api/learning-client.ts'

export type Material = {
  id: string
  courseId: string
  title: string
  fileName: string
  visibility: 'UNPUBLISHED' | 'PUBLISHED'
  processingStatus: 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'
  failureReason?: string
}

export function listMaterials(courseId: string) {
  return learningFetch<Material[]>(`/api/courses/${courseId}/materials`)
}

export function uploadMaterial(courseId: string, file: File, title: string) {
  const body = new FormData()
  body.append('file', file)
  if (title.trim()) {
    body.append('title', title.trim())
  }
  return learningUpload<Material>(`/api/courses/${courseId}/materials`, body)
}

export function setMaterialVisibility(id: string, visibility: Material['visibility']) {
  return learningFetch<Material>(`/api/materials/${id}/visibility`, {
    method: 'PATCH',
    body: JSON.stringify({ visibility }),
  })
}

export function deleteMaterial(id: string) {
  return learningFetch<void>(`/api/materials/${id}`, {
    method: 'DELETE',
  })
}

export async function fetchMaterialBlob(materialId: string) {
  const headers = new Headers()
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${learningApiUrl}/api/materials/${materialId}/file`, { headers })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.code ?? 'REQUEST_FAILED', body?.message ?? 'Could not load the PDF.', response.status)
  }
  return response.blob()
}

export async function downloadMaterial(material: Pick<Material, 'id' | 'fileName'>) {
  const blob = await fetchMaterialBlob(material.id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = material.fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export async function openMaterialPreview(materialId: string) {
  const blob = await fetchMaterialBlob(materialId)
  return URL.createObjectURL(blob)
}
