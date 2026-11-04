import { NativeModules } from 'react-native';
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
};
export type ImportItem = {
  id: string;
  name: string;
  status: string;
  error: string;
  bookId: string;
};
interface LibraryBridge {
  books(): Promise<Book[]>;
  sessions(bookId: string): Promise<Session[]>;
  recentSessions(): Promise<Session[]>;
  importBooks(): Promise<ImportItem[]>;
  importFixtures(): Promise<ImportItem[]>;
  importItems(): Promise<ImportItem[]>;
  retryImport(itemId: string): Promise<ImportItem[]>;
  createSession(bookId: string): Promise<Session>;
  openSession(sessionId: string): Promise<void>;
}
export const library: LibraryBridge | undefined = NativeModules.ReadleLibrary;
