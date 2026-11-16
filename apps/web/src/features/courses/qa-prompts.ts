/** One-click starters. Each one asks for a result, then a walkthrough. */
export const QA_SUGGESTED_PROMPTS = [
  {
    id: 'result',
    label: 'Give me the result',
    question:
      'State the main result of these pages in one or two sentences I could write on an exam. Then walk through why that result follows from the lecture.',
  },
  {
    id: 'walk-through',
    label: 'Walk through the steps',
    question:
      'Walk me through the core process in order. Number the steps, define each term the first time you use it, and end with the result I should remember.',
  },
  {
    id: 'why',
    label: 'Explain why it works',
    question:
      'Explain the main idea: what it is, why it works that way, and the result I should be able to repeat. Use the lecture’s own example if it has one.',
  },
  {
    id: 'terms',
    label: 'Define the terms',
    question:
      'List the terms these pages actually define. For each one, give the definition in one sentence, then one sentence on why it matters for the result.',
  },
  {
    id: 'check',
    label: 'Check I understand',
    question:
      'Give me the result first. Then ask one short check question from the lecture, and put the answer on the next line so I can compare.',
  },
] as const

/** Follow-ups that tighten the result instead of starting a new topic. */
export const QA_FOLLOW_UP_PROMPTS = [
  {
    id: 'one-sentence',
    label: 'One-sentence result',
    question: 'Restate only the result in one sentence I can memorize. Do not add a new idea.',
  },
  {
    id: 'slower',
    label: 'Walk through slower',
    question: 'Walk through that again more slowly, one step per line, and end with the result.',
  },
  {
    id: 'simpler',
    label: 'Explain simpler',
    question: 'Explain that more simply, like I’m new to the topic. Keep the same result.',
  },
  {
    id: 'miss',
    label: 'What I might miss',
    question:
      'What do students usually mix up here? Name the distinction the lecture actually makes, then repeat the result.',
  },
] as const
