import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  BackHandler,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { Book, ImportItem, library, Session } from './src/library';
import {WalkthroughLesson, WalkthroughSetup} from './src/WalkthroughScreen';
import {identityPaperTitle, identityPaperVersion, isWalkthroughCursor} from './src/walkthrough';
import type {WalkthroughCursor} from './src/walkthrough';

function Action({
  label,
  onPress,
  secondary = false,
}: {
  label: string;
  onPress: () => void;
  secondary?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        secondary && styles.secondary,
        pressed && styles.pressed,
      ]}
    >
      <Text style={[styles.buttonText, secondary && styles.secondaryText]}>
        {label}
      </Text>
    </Pressable>
  );
}
function SessionRow({
  session,
  onOpen,
  showBook = false,
}: {
  session: Session;
  showBook?: boolean;
  onOpen: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Continue ${session.label}, ${session.location}`}
      onPress={onOpen}
      style={styles.row}
    >
      <View style={styles.grow}>
        <Text style={styles.rowTitle}>{session.label}</Text>
        {showBook && session.bookTitle && (
          <Text style={styles.meta}>{session.bookTitle}</Text>
        )}
        <Text style={styles.body}>{session.location} · {session.mode === 'learn' ? 'Learn' : 'Read'}</Text>
        <Text style={styles.meta}>
          {new Date(session.updatedAt).toLocaleString()}
        </Text>
      </View>
      <Text style={styles.link}>Open</Text>
    </Pressable>
  );
}
function LibraryScreen() {
  const [books, setBooks] = useState<Book[]>([]);
  const [selected, setSelected] = useState<Book | null>(null);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [recent, setRecent] = useState<Session[]>([]);
  const [items, setItems] = useState<ImportItem[]>([]);
  const [tab, setTab] = useState<'library' | 'continue'>('library');
  const [about, setAbout] = useState(false);
  const [busy, setBusy] = useState(false);
  const actionInProgress = useRef(false);
  const [error, setError] = useState('');
  const [sampleItems, setSampleItems] = useState<ImportItem[]>([]);
  const [planning, setPlanning] = useState(false);
  const [walkthrough, setWalkthrough] = useState<{id: string; cursor: WalkthroughCursor} | null>(null);
  useEffect(() => {
    if (!planning && !walkthrough) {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!busy) {
        setPlanning(false);
        setWalkthrough(null);
      }
      return true;
    });
    return () => subscription.remove();
  }, [busy, planning, walkthrough]);
  const refresh = useCallback(async () => {
    if (!library) {
      return;
    }
    const [nextBooks, nextItems, nextRecent] = await Promise.all([
      library.books(),
      library.importItems(),
      library.recentSessions(),
    ]);
    setBooks(nextBooks);
    setItems(nextItems);
    setRecent(nextRecent);
    if (selected) {
      setSessions(await library.sessions(selected.id));
    }
  }, [selected]);
  useEffect(() => {
    refresh().catch(e => setError(e.message));
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refresh().catch(e => setError(e.message));
      }
    });
    return () => subscription.remove();
  }, [refresh]);
  async function run(action: () => Promise<unknown>, refreshLibrary = true) {
    if (actionInProgress.current) {
      return;
    }
    actionInProgress.current = true;
    setError('');
    setBusy(true);
    try {
      await action();
      if (refreshLibrary) {
        await refresh();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Please try again.');
    } finally {
      actionInProgress.current = false;
      setBusy(false);
    }
  }
  const open = (session: Session) =>
    run(async () => {
      if (session.mode === 'learn') {
        const cursor = await library!.learningCursor(session.id);
        if (!isWalkthroughCursor(cursor)) {
          throw new Error('This saved walkthrough could not be restored.');
        }
        setWalkthrough({id: session.id, cursor});
      } else {
        await library!.openSession(session.id);
      }
    });
  const create = () =>
    run(async () => {
      const session = await library!.createSession(selected!.id);
      await library!.openSession(session.id);
    });
  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor="#F5F4F0" />
      <View style={styles.header}>
        <Text style={styles.brand}>readle</Text>
        {planning || walkthrough ? (
          <Action label="Back to sessions" secondary onPress={() => {
            if (!busy) {
              setPlanning(false);
              setWalkthrough(null);
            }
          }} />
        ) : (
          <Action label={about ? 'Close About' : 'About'} onPress={() => setAbout(!about)} secondary />
        )}
      </View>
      <ScrollView key={walkthrough?.cursor.lesson ?? (planning ? 'planning' : 'library')}
        contentContainerStyle={styles.content}>
        {walkthrough ? (
          <WalkthroughLesson cursor={walkthrough.cursor} busy={busy}
            onChange={cursor => run(async () => {
              await library!.saveLearningCursor(walkthrough.id, cursor);
              setWalkthrough({...walkthrough, cursor});
            }, false)}
            onSource={page => run(() => library!.openWalkthroughSource(walkthrough.id, page), false)} />
        ) : planning && selected ? (
          <WalkthroughSetup busy={busy} onStart={cursor => run(async () => {
            const session = await library!.createWalkthrough(selected.id, cursor);
            setWalkthrough({id: session.id, cursor});
            setPlanning(false);
          })} />
        ) : about ? (
          <>
            <Text style={styles.heading}>About Readle</Text>
            <Text style={styles.body}>
              {library?.versionName
                ? `Version ${library.versionName} · Build ${library.buildNumber}`
                : 'Version unavailable'}
            </Text>
            <Text style={styles.body}>Read and keep notes in your own book sessions.</Text>
          </>
        ) : selected ? (
          <>
            <Pressable
              accessibilityRole="button"
              onPress={() => setSelected(null)}
              style={styles.back}
            >
              <Text style={styles.link}>‹ Library</Text>
            </Pressable>
            <Text style={styles.eyebrow}>
              {selected.format === 'pdf' ? 'PDF SOURCE' : 'EPUB READER'} · STORED ON THIS DEVICE
            </Text>
            <Text style={styles.heading}>{selected.sourceVersion === identityPaperVersion ? identityPaperTitle : selected.title}</Text>
            <Text style={styles.body}>
              Resume your work, or start with a blank workspace.
            </Text>
            {!busy && (
              <View style={styles.actions}>
                {sessions.length > 0 && (
                  <Action
                    label="Continue latest"
                    onPress={() => open(sessions[0])}
                  />
                )}
                {selected.sourceVersion === identityPaperVersion && (
                  <Action label="Plan a walkthrough" onPress={() => setPlanning(true)} secondary={sessions.length > 0} />
                )}
                <Action
                  label={
                    selected.sourceVersion === identityPaperVersion ? 'Read original' : sessions.length ? 'Start new session' : 'Start reading'
                  }
                  onPress={create}
                  secondary={sessions.length > 0 || selected.sourceVersion === identityPaperVersion}
                />
              </View>
            )}
            <Text style={styles.section}>
              Sessions <Text style={styles.count}>{sessions.length}</Text>
            </Text>
            {sessions.length ? (
              sessions.map(s => (
                <SessionRow key={s.id} session={s} onOpen={() => open(s)} />
              ))
            ) : (
              <Text style={styles.body}>
                Your first session will appear here when you start reading.
              </Text>
            )}
          </>
        ) : (
          <>
            <Text style={styles.eyebrow}>A LITTLE FURTHER, EVERY DAY</Text>
            <Text style={styles.heading}>
              {tab === 'library' ? 'Your library.' : 'Pick up the thread.'}
            </Text>
            <Text style={styles.body}>
              {tab === 'library'
                ? 'Original books. A place for every train of thought.'
                : 'Return to the exact session you left.'}
            </Text>
            <View style={styles.tabs}>
              {(['library', 'continue'] as const).map(t => (
                <Pressable
                  accessibilityRole="tab"
                  accessibilityState={{ selected: tab === t }}
                  key={t}
                  onPress={() => setTab(t)}
                  style={[styles.tab, tab === t && styles.activeTab]}
                >
                  <Text style={styles.tabText}>
                    {t === 'library' ? 'Library' : 'Continue'}
                  </Text>
                </Pressable>
              ))}
            </View>
            {!library ? (
              <Text style={styles.error}>
                The native reader is available in the Android spike build. iOS
                native integration is pending.
              </Text>
            ) : tab === 'library' ? (
              <>
                {!busy && (
                  <View style={styles.actions}>
                    <Action
                      label="Import books"
                      onPress={() => run(() => library!.importBooks())}
                    />
                    <Action
                      label="Add sample books"
                      secondary
                      onPress={() => run(async () => {
                        setSampleItems([]);
                        setSampleItems(await library!.importFixtures());
                      })}
                    />
                    {sampleItems.length > 0 && (
                      <View accessibilityLiveRegion="polite" style={styles.sampleResult}>
                        {sampleItems.map(item => {
                          const format = item.name.endsWith('.epub') ? 'EPUB' : 'PDF';
                          const sample = books.find(book => book.id === item.bookId);
                          return (
                            <View key={item.id}>
                              <Text style={item.status === 'failed' ? styles.error : styles.body}>
                                {`Sample ${format}: ${item.status === 'ready' ? 'added to your library' : item.status === 'duplicate' ? 'already in your library' : item.error || 'could not be added'}`}
                              </Text>
                              {sample && (
                                <Pressable
                                  accessibilityRole="button"
                                  style={styles.back}
                                  onPress={() => {
                                    setSessions([]);
                                    setSelected(sample);
                                  }}
                                >
                                  <Text style={styles.link}>{`View sample ${format}`}</Text>
                                </Pressable>
                              )}
                            </View>
                          );
                        })}
                      </View>
                    )}
                  </View>
                )}
                {books.length === 0 && (
                  <View style={styles.empty}>
                    <Text style={styles.section}>
                      Make room for a good idea.
                    </Text>
                    <Text style={styles.body}>
                      Import an EPUB to read, or a PDF to keep as an original source.
                      Everything in this build stays on your device.
                    </Text>
                  </View>
                )}
                {books.map((book, i) => (
                  <Pressable
                    accessibilityRole="button"
                    key={book.id}
                    onPress={() => {
                      setSessions([]);
                      setSelected(book);
                    }}
                    style={styles.bookRow}
                  >
                    <View
                      style={[
                        styles.cover,
                        i % 2 ? styles.coverAlternate : styles.coverDefault,
                      ]}
                    >
                      <Text style={styles.coverMark}>r.</Text>
                      <Text style={styles.coverType}>
                        {book.format.toUpperCase()}
                      </Text>
                    </View>
                    <View style={styles.grow}>
                      <Text style={styles.rowTitle}>{book.sourceVersion === identityPaperVersion ? identityPaperTitle : book.title}</Text>
                      <Text style={styles.meta}>
                        {book.format === 'pdf' ? 'PDF · Source/reference' : 'EPUB · Reader'} · Available offline
                      </Text>
                      <Text style={styles.link}>View sessions →</Text>
                    </View>
                  </Pressable>
                ))}
                {items.length > 0 && (
                  <>
                    <Text style={styles.section}>Import queue</Text>
                    {items.slice(0, 12).map(item => (
                      <View key={item.id} style={styles.row}>
                        <View style={styles.grow}>
                          <Text style={styles.rowTitle}>{item.name}</Text>
                          <Text
                            style={
                              item.status === 'failed'
                                ? styles.error
                                : styles.meta
                            }
                          >
                            {item.status}
                            {item.error ? ` · ${item.error}` : ''}
                          </Text>
                        </View>
                        {item.status === 'failed' && (
                          <Action
                            label="Retry"
                            secondary
                            onPress={() =>
                              run(() => library!.retryImport(item.id))
                            }
                          />
                        )}
                      </View>
                    ))}
                  </>
                )}
              </>
            ) : recent.length ? (
              recent.map(s => (
                <SessionRow
                  showBook
                  key={s.id}
                  session={s}
                  onOpen={() => open(s)}
                />
              ))
            ) : (
              <Text style={styles.body}>
                Start a session from a book in your Library.
              </Text>
            )}
          </>
        )}
        {busy && (
          <ActivityIndicator
            accessibilityLabel="Saving or opening"
            color="#355742"
            style={styles.loader}
          />
        )}
        {error !== '' && (
          <Text accessibilityRole="alert" style={styles.error}>
            {error}
          </Text>
        )}
        {!planning && !walkthrough && (
          <Text style={styles.footer}>
            Each new session starts without notes or highlights from other sessions.
          </Text>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
export default function App() {
  return (
    <SafeAreaProvider>
      <LibraryScreen />
    </SafeAreaProvider>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F5F4F0' },
  header: {
    paddingHorizontal: 24,
    paddingVertical: 18,
    borderBottomWidth: 1,
    borderColor: '#DDDED6',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  brand: {
    fontSize: 30,
    color: '#263B2D',
    fontWeight: '700',
    letterSpacing: -1.5,
  },
  edition: { fontSize: 10, letterSpacing: 1.3, color: '#687369' },
  content: {
    padding: 24,
    paddingBottom: 40,
    maxWidth: 840,
    width: '100%',
    alignSelf: 'center',
  },
  eyebrow: {
    fontSize: 10,
    letterSpacing: 1.8,
    color: '#637164',
    marginTop: 20,
    marginBottom: 12,
  },
  heading: {
    fontSize: 34,
    letterSpacing: -1.1,
    fontWeight: '600',
    color: '#25372B',
    marginBottom: 14,
  },
  body: { fontSize: 15, lineHeight: 23, color: '#626A60' },
  actions: { gap: 10, marginVertical: 24 },
  sampleResult: { padding: 12, backgroundColor: '#E9EEE5', borderRadius: 8, gap: 8 },
  button: {
    minHeight: 48,
    backgroundColor: '#355742',
    borderRadius: 8,
    paddingHorizontal: 18,
    paddingVertical: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  buttonText: { color: '#F8F7F2', fontWeight: '600', fontSize: 15 },
  secondary: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#BAC4B6',
  },
  secondaryText: { color: '#355742' },
  pressed: { opacity: 0.7 },
  tabs: {
    flexDirection: 'row',
    marginTop: 24,
    gap: 24,
    borderBottomWidth: 1,
    borderColor: '#DDDED6',
  },
  tab: { paddingVertical: 14, minHeight: 48 },
  activeTab: { borderBottomWidth: 2, borderColor: '#355742' },
  tabText: { fontSize: 15, color: '#355742', fontWeight: '600' },
  bookRow: {
    flexDirection: 'row',
    paddingVertical: 20,
    borderBottomWidth: 1,
    borderColor: '#DDDED6',
    gap: 20,
    alignItems: 'center',
  },
  cover: {
    width: 76,
    height: 104,
    padding: 10,
    borderRadius: 3,
    justifyContent: 'space-between',
  },
  coverDefault: { backgroundColor: '#D9E1D7' },
  coverAlternate: { backgroundColor: '#DCD8CB' },
  coverMark: { fontSize: 36, fontWeight: '600', color: '#355742' },
  coverType: { fontSize: 9, letterSpacing: 2, color: '#355742' },
  grow: { flex: 1 },
  rowTitle: {
    fontSize: 17,
    lineHeight: 24,
    color: '#2B392E',
    fontWeight: '600',
  },
  meta: { fontSize: 12, lineHeight: 20, color: '#70796D', marginVertical: 6 },
  link: { fontSize: 14, fontWeight: '600', color: '#355742' },
  row: {
    flexDirection: 'row',
    gap: 14,
    paddingVertical: 18,
    borderBottomWidth: 1,
    borderColor: '#DDDED6',
    alignItems: 'center',
  },
  section: {
    fontSize: 21,
    color: '#2B392E',
    fontWeight: '600',
    marginTop: 24,
    marginBottom: 12,
  },
  count: { color: '#7B8475', fontSize: 16 },
  empty: { paddingVertical: 20 },
  error: { color: '#9A3F30', fontSize: 14, lineHeight: 22, marginVertical: 8 },
  footer: { fontSize: 12, lineHeight: 20, color: '#767E71', marginTop: 36 },
  back: { minHeight: 44, justifyContent: 'center' },
  loader: { margin: 20 },
});
