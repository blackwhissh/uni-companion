/** One-click starters for study chat — topic exploration, not exam dumps. */
export const QA_SUGGESTED_PROMPTS = [
  {
    id: 'explain-main',
    label: 'Explain the main idea',
    question: 'Explain the main idea of these lectures like you’re tutoring me. Keep it concrete.',
  },
  {
    id: 'key-terms',
    label: 'Key terms',
    question: 'What are the most important key terms here, with short definitions I can memorize?',
  },
  {
    id: 'walk-through',
    label: 'Walk me through it',
    question: 'Walk me through the core process or algorithm step by step, citing the lectures.',
  },
  {
    id: 'compare',
    label: 'Compare ideas',
    question: 'What important comparisons or contrasts do the materials make, and why do they matter?',
  },
  {
    id: 'exam-check',
    label: 'Exam checklist',
    question: 'What should I be able to explain after studying these lectures? Give a short checklist.',
  },
] as const

/** Follow-ups that feel like ChatGPT study mode after an answer exists. */
export const QA_FOLLOW_UP_PROMPTS = [
  {
    id: 'simpler',
    label: 'Explain simpler',
    question: 'Explain that more simply, like I’m new to the topic.',
  },
  {
    id: 'analogy',
    label: 'Give an analogy',
    question: 'Give a concrete everyday analogy for that idea, still grounded in the lectures.',
  },
  {
    id: 'quiz-me',
    label: 'Quiz me',
    question: 'Quiz me with 3 short questions on what we just covered. Wait for my answers before revealing yours.',
  },
  {
    id: 'deeper',
    label: 'Go deeper',
    question: 'Go one level deeper on the trickiest part — what do students usually miss?',
  },
] as const
