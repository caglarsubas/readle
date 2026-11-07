import React from 'react';
import { Text } from 'react-native';
import renderer, { act, ReactTestRenderer } from 'react-test-renderer';
import App from '../App';
import { library } from '../src/library';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

jest.mock('../src/library', () => ({
  library: {
    versionName: "0.1.0-spike",
    buildNumber: "14",
    books: jest.fn(),
    importItems: jest.fn(),
    recentSessions: jest.fn(),
    sessions: jest.fn(),
    createSession: jest.fn(),
    openSession: jest.fn(),
    importBooks: jest.fn(),
    importFixtures: jest.fn(),
  },
}));
const bridge = library as jest.Mocked<NonNullable<typeof library>>;
const book = {
  id: 'book-a',
  title: 'A Small Book of Learning',
  format: 'pdf',
  sourceVersion: 'immutable-a',
};
const session = {
  id: 'session-a',
  bookId: 'book-a',
  label: 'Session 1',
  location: 'Page 2',
  updatedAt: 1,
  revision: 2,
};
let app: ReactTestRenderer;
async function press(label: string) {
  const text = app.root
    .findAllByType(Text)
    .find(node => node.props.children === label);
  let button = text;
  while (button && typeof button.props.onPress !== 'function') {
    button = button.parent ?? undefined;
  }
  if (!button) {
    throw new Error(
      `Button missing: ${label}. Text ${app.root
        .findAllByType(Text)
        .map(node => node.props.children)
        .join('|')}`,
    );
  }

  await act(async () => {
    await button!.props.onPress();
  });
}
beforeEach(async () => {
  jest.clearAllMocks();
  bridge.books.mockResolvedValue([book]);
  bridge.importItems.mockResolvedValue([]);
  bridge.recentSessions.mockResolvedValue([session]);
  bridge.sessions.mockResolvedValue([session]);
  bridge.createSession.mockResolvedValue({ ...session, id: 'session-b' });
  bridge.openSession.mockResolvedValue();
  await act(async () => {
    app = renderer.create(<App />);
  });
});
afterEach(async () => {
  await act(async () => app.unmount());
});
test('inspecting a book lists its saved sessions without creating one', async () => {
  await press(book.title);
  expect(bridge.sessions).toHaveBeenCalledWith('book-a');
  expect(bridge.createSession).not.toHaveBeenCalled();
  expect(
    app.root.findAllByType(Text).some(t => t.props.children === 'Session 1'),
  ).toBe(true);
});
test('continue latest opens the saved session ID', async () => {
  await press(book.title);
  await press('Continue latest');
  expect(bridge.openSession).toHaveBeenCalledWith('session-a');
  expect(bridge.createSession).not.toHaveBeenCalled();
});
test('start new session opens the newly created workspace', async () => {
  await press(book.title);
  await press('Start new session');
  expect(bridge.createSession).toHaveBeenCalledWith('book-a');
  expect(bridge.openSession).toHaveBeenCalledWith('session-b');
});

test('About shows the installed package version and can return to the library', async () => {
  await press('About');
  expect(
    app.root.findAllByType(Text).some(
      node => node.props.children === 'Version 0.1.0-spike · Build 14',
    ),
  ).toBe(true);
  await press('Close About');
  expect(app.root.findAllByType(Text).some(node => node.props.children === book.title)).toBe(true);
});
