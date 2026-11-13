import { NativeModules } from 'react-native';
import type {
  GeneratedCursor,
  GenerationStatus,
  LearnerProfile,
  LearningEdition,
} from './generatedWalkthrough';
import type { WalkthroughCursor } from './walkthrough';
export type Book = {
  id: string;
  title: string;
  format: string;
  sourceVersion: string;
  epubId?: string | null;
  sourcePdfId?: string | null;
};
export type Session = {
  bookTitle?: string;
  id: string;
  bookId: string;
  label: string;
  location: string;
  updatedAt: number;
  revision: number;
  mode?: 'read' | 'learn';
};
export type ImportItem = {
  id: string;
  name: string;
  status: string;
  error: string;
  bookId: string;
};
interface LibraryBridge {
  versionName: string;
  buildNumber: string;
  books(): Promise<Book[]>;
  sessions(bookId: string): Promise<Session[]>;
  recentSessions(): Promise<Session[]>;
  importBooks(): Promise<ImportItem[]>;
  convertToEpub(bookId: string): Promise<Book>;
  importFixtures(): Promise<ImportItem[]>;
  importItems(): Promise<ImportItem[]>;
  retryImport(itemId: string): Promise<ImportItem[]>;
  createSession(bookId: string): Promise<Session>;
  openSession(sessionId: string): Promise<void>;
  createWalkthrough(
    bookId: string,
    cursor: WalkthroughCursor,
  ): Promise<Session>;
  createGeneratedWalkthrough(
    bookId: string,
    profile: LearnerProfile,
  ): Promise<Session>;
  generationStatus(
    sessionId: string,
    start: boolean,
  ): Promise<GenerationStatus>;
  learningEdition(sessionId: string): Promise<LearningEdition | null>;
  learningCursor(sessionId: string): Promise<unknown>;
  saveLearningCursor(
    sessionId: string,
    cursor: WalkthroughCursor | GeneratedCursor,
  ): Promise<void>;
  openWalkthroughSource(sessionId: string, printedPage: number): Promise<void>;
}
export const library: LibraryBridge | undefined = NativeModules.ReadleLibrary;
