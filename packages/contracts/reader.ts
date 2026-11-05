/** Source identity survives session changes and document layout changes. */
export interface SourceAnchor {
  sourceVersion: string;
  format: 'pdf' | 'epub';
  locator: Record<string, unknown>;
  quote?: string;
}
export interface WorkspaceState {
  mode: 'read' | 'learn';
  readingLocator: Record<string, unknown> | null;
  learningCursor: Record<string, unknown> | null;
  selection: SourceAnchor | null;
  visualState: Record<string, unknown>;
  quizDraft: Record<string, unknown> | null;
  detours: unknown[];
  assistant: {threadId: string | null; model: string | null; draft: string};
  preferences: {fontSize: number; pdfZoom: number};
}
export interface BookSession {
  id: string;
  bookId: string;
  sourceVersion: string;
  label: string;
  state: WorkspaceState;
  revision: number;
  updatedAt: number;
}
