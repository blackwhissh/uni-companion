import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router'
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
} from '../../shared/ui/ui.tsx'
import { getCourse } from './course-api.ts'
import { listMaterials, openMaterialAtPage } from './material-api.ts'
import { ExplainLanguageField } from './ExplainLanguageField.tsx'
import type { ExplainLanguage } from './ExplainLanguageField.tsx'
import { searchLectures, summarizeMaterial } from './rag-api.ts'
import type { RagAnswer, SearchHit } from './rag-api.ts'
import { studyActionError } from './study-ui.ts'

const SEARCH_MAX = 200

export function CourseSearchPage() {
  const { courseId = '' } = useParams()
  const course = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => getCourse(courseId),
    retry: false,
    refetchOnMount: 'always',
  })
  const courseLoading = course.isLoading || (course.isFetching && !course.data)
  const enrolled = course.data?.enrolled === true
  const materials = useQuery({
    queryKey: ['materials', courseId],
    queryFn: () => listMaterials(courseId),
    enabled: enrolled,
    retry: false,
  })
  const readyMaterials =
    materials.data?.filter((material) => material.visibility === 'PUBLISHED' && material.processingStatus === 'READY') ?? []
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<SearchHit[] | null>(null)
  const [summaryMaterialId, setSummaryMaterialId] = useState('')
  const [summaryLanguage, setSummaryLanguage] = useState<Exclude<ExplainLanguage, ''>>('EN')
  const [summary, setSummary] = useState<RagAnswer | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!summaryMaterialId && readyMaterials.length > 0) {
      setSummaryMaterialId(readyMaterials[0].id)
    }
  }, [readyMaterials, summaryMaterialId])

  useEffect(() => {
    if (window.location.hash === '#summary') {
      document.getElementById('summary')?.scrollIntoView()
    }
  }, [course.data, readyMaterials.length])

  const search = useMutation({
    mutationFn: (text: string) => searchLectures(courseId, text),
    onSuccess: (result) => {
      setError(null)
      setHits(result.hits)
    },
    onError: (err) => setError(studyActionError(err, 'Could not search these lectures.')),
  })

  const summarize = useMutation({
    mutationFn: ({ materialId, language }: { materialId: string; language: 'EN' | 'DE' }) =>
      summarizeMaterial(courseId, materialId, language),
    onSuccess: (result) => {
      setError(null)
      setSummary(result)
    },
    onError: (err) => setError(studyActionError(err, 'Could not summarize that lecture.')),
  })

  const unavailable = course.error instanceof ApiError && course.error.status === 404

  async function openPage(materialId: string, pageNumber: number) {
    try {
      await openMaterialAtPage(materialId, pageNumber)
    } catch (err) {
      setError(studyActionError(err, 'Could not open that page.'))
    }
  }

  function onSearch(event: FormEvent) {
    event.preventDefault()
    const text = query.trim()
    if (!text || text.length > SEARCH_MAX) {
      return
    }
    search.mutate(text)
  }

  function onSummarize(event: FormEvent) {
    event.preventDefault()
    if (!summaryMaterialId) {
      return
    }
    summarize.mutate({ materialId: summaryMaterialId, language: summaryLanguage })
  }

  return (
    <Page>
      <BackLink to={`/courses/${courseId}`}>
        {course.data?.title ?? (courseLoading ? 'Loading course…' : 'Course')}
      </BackLink>
      {courseLoading ? (
        <p className="mt-6 animate-pulse-soft text-muted" role="status">
          Loading course…
        </p>
      ) : null}
      <div className="mt-4">
        <PageHeader
          eyebrow={course.data ? `${course.data.code} · ${course.data.term}` : 'Search'}
          title="Search and summaries"
          description="Search published lectures, then open a short summary of one lecture. Both stay inside the published PDFs."
        />
      </div>

      {unavailable ? <p className="mt-6 text-muted">This course is not available.</p> : null}

      {course.data && !enrolled && !unavailable ? (
        <EmptyState
          title="Enroll to search this course"
          description="Search and summaries are available after you join this course."
        />
      ) : null}

      {enrolled && !materials.isLoading && readyMaterials.length === 0 ? (
        <EmptyState
          title="No published materials yet"
          description="Once your professor publishes ready lecture PDFs, you can search them and read a short summary."
        />
      ) : null}

      {enrolled && readyMaterials.length > 0 ? (
        <>
          {error ? (
            <div className="mt-6">
              <Alert>{error}</Alert>
            </div>
          ) : null}

          <Panel className="mt-8">
            <SectionLabel>Search</SectionLabel>
            <p className="mt-2 text-sm text-muted">Looks across every published lecture in this course.</p>
            <form className="mt-4 flex flex-col gap-3" onSubmit={onSearch}>
              <label htmlFor="lecture-search" className="text-sm font-medium text-ink-soft">
                Search published lectures
              </label>
              <input
                id="lecture-search"
                value={query}
                maxLength={SEARCH_MAX}
                onChange={(event) => setQuery(event.target.value.slice(0, SEARCH_MAX))}
                className="w-full rounded-lg border border-ink/20 bg-white px-3 py-2 text-sm text-ink"
              />
              <div>
                <Button type="submit" disabled={search.isPending || query.trim().length === 0}>
                  {search.isPending ? 'Searching…' : 'Search'}
                </Button>
              </div>
            </form>
            {hits && hits.length === 0 ? (
              <p className="mt-4 text-sm text-muted" role="status">
                No published pages match that search.
              </p>
            ) : null}
            {hits && hits.length > 0 ? (
              <ul className="mt-4 flex flex-col gap-3">
                {hits.map((hit) => (
                  <li key={`${hit.materialId}-${hit.pageNumber}-${hit.excerpt}`} className="rounded-xl border border-ink/10 px-4 py-3">
                    <p className="text-sm font-medium text-ink">
                      {hit.title} · page {hit.pageNumber}
                    </p>
                    <p className="mt-1 text-sm leading-relaxed text-ink-soft">{hit.excerpt}</p>
                    <div className="mt-2">
                      <Button type="button" variant="secondary" onClick={() => void openPage(hit.materialId, hit.pageNumber)}>
                        Open page
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            ) : null}
          </Panel>

          <section id="summary" className="mt-8">
            <Panel>
              <SectionLabel>Summary</SectionLabel>
              <p className="mt-2 text-sm text-muted">A short summary of one published lecture, in English or German, with the pages it uses.</p>
              <form className="mt-4 flex flex-col gap-3" onSubmit={onSummarize}>
                <label htmlFor="summary-lecture" className="text-sm font-medium text-ink-soft">
                  Lecture to summarize
                </label>
                <select
                  id="summary-lecture"
                  value={summaryMaterialId}
                  onChange={(event) => setSummaryMaterialId(event.target.value)}
                  className="w-full rounded-lg border border-ink/20 bg-white px-3 py-2 text-sm text-ink"
                >
                  {readyMaterials.map((material) => (
                    <option key={material.id} value={material.id}>
                      {material.title}
                    </option>
                  ))}
                </select>
                <ExplainLanguageField
                  name="summary-language"
                  value={summaryLanguage}
                  onChange={(next) => {
                    if (next === 'EN' || next === 'DE') {
                      setSummaryLanguage(next)
                    }
                  }}
                />
                <div>
                  <Button type="submit" disabled={summarize.isPending || !summaryMaterialId}>
                    {summarize.isPending ? 'Summarizing…' : 'Summarize'}
                  </Button>
                </div>
              </form>
              {summary ? (
                <div className="mt-4">
                  <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink">{summary.answer}</p>
                  {summary.citations.length > 0 ? (
                    <ul className="mt-3 flex flex-col gap-2">
                      {summary.citations.map((citation) => (
                        <li key={`${citation.materialId}-${citation.pageNumber}`}>
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={() => void openPage(citation.materialId, citation.pageNumber)}
                          >
                            Open {citation.title}, page {citation.pageNumber}
                          </Button>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              ) : null}
            </Panel>
          </section>
        </>
      ) : null}
    </Page>
  )
}
