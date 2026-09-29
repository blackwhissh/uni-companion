/** Optional one-click study prompts for Course Q&A. */
export const QA_SUGGESTED_PROMPTS = [
  {
    id: 'summarize-ch1',
    label: 'Summarize chapter 1',
    question: 'Summarize the first chapter in clear study notes.',
  },
  {
    id: 'key-terms',
    label: 'Key terms',
    question: 'What are the most important key terms in the published materials, with short definitions?',
  },
  {
    id: 'explain-simply',
    label: 'Explain simply',
    question: 'Explain the main idea of this course material in simple language for a first-year student.',
  },
  {
    id: 'compare',
    label: 'Compare ideas',
    question: 'What important comparisons or contrasts do the materials make, and why do they matter?',
  },
  {
    id: 'exam-check',
    label: 'Exam check',
    question: 'What should I be able to explain after studying these materials? Give a short checklist.',
  },
] as const
