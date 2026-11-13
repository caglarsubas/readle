export type LearnerProfile = {
  background: string;
  goal: string;
  preference: string;
};

export type Pass = 1 | 2 | 3;
export type Option = 'a' | 'b' | 'c' | 'd';

export type GeneratedCursor = {
  version: 2;
  profile: LearnerProfile;
  lesson: number;
  pass: Pass;
  drafts: Record<string, Option>;
  submissions: Record<string, Option>;
};

export type Citation = { page: number; concept: string; text: string };
type Cited = { source_ids: string[] };
export type Question = Cited & {
  stem: string;
  options: Record<Option, string>;
  answer: Option;
  rationale: string;
};
type GeneratedLesson = {
  id: string;
  title: string;
  objective: string;
  why: string;
  concepts: Cited & {
    explanation: string;
    example: string;
    checkpoint: string;
  };
  technical: Cited & {
    assumptions: string;
    mechanism: string;
    failure_mode: string;
    exercise: string;
    checkpoint: string;
  };
  assessment: Question[];
  assessment_key_matches: boolean[];
  review: { lesson_status: 'supported' | 'needs_review' };
};
export type LearningEdition = {
  version: 1;
  title: string;
  sourceVersion: string;
  pageCount: number;
  rationale: string;
  lessons: GeneratedLesson[];
  citations: Record<string, Citation>;
};

export type GenerationStatus =
  | { state: 'pending' }
  | { state: 'running' | 'failed'; message: string }
  | { state: 'ready'; edition: LearningEdition };

export function isGeneratedCursor(value: unknown): value is GeneratedCursor {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const cursor = value as Partial<GeneratedCursor>;
  return (
    cursor.version === 2 &&
    typeof cursor.lesson === 'number' &&
    Number.isInteger(cursor.lesson) &&
    cursor.lesson >= 0 &&
    (cursor.pass === 1 || cursor.pass === 2 || cursor.pass === 3) &&
    !!cursor.profile &&
    !!cursor.drafts &&
    !!cursor.submissions
  );
}

export function lessonText(text: string): string {
  return text
    .replace(/[[(](?:E\d{3,}\s*(?:,\s*)?)+[\])]/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/ +([.,])/g, '$1');
}
