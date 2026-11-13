import { NativeModules } from 'react-native';
import type {WalkthroughCursor} from './walkthrough';
export type Book = {
  id: string;
  title: string;
  format: string;
  sourceVersion: string;
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
  importFixtures(): Promise<ImportItem[]>;
  importItems(): Promise<ImportItem[]>;
  retryImport(itemId: string): Promise<ImportItem[]>;
  createSession(bookId: string): Promise<Session>;
  openSession(sessionId: string): Promise<void>;
  createWalkthrough(bookId: string, cursor: WalkthroughCursor): Promise<Session>;
  learningCursor(sessionId: string): Promise<unknown>;
  saveLearningCursor(sessionId: string, cursor: WalkthroughCursor): Promise<void>;
  openWalkthroughSource(sessionId: string, printedPage: number): Promise<void>;
}
export const library: LibraryBridge | undefined = NativeModules.ReadleLibrary;
