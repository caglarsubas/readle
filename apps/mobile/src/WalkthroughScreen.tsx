import React, { useState } from 'react';
import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';
import {
  evaluateRequest,
  goals,
  identityPaperTitle,
  lessons,
  recommend,
  startWalkthrough,
} from './walkthrough';
import type { Experience, Goal, WalkthroughCursor } from './walkthrough';

export function Choice({
  label,
  selected,
  disabled = false,
  onPress,
}: {
  label: string;
  selected: boolean;
  disabled?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.choice,
        selected && styles.selected,
        disabled && styles.disabled,
      ]}
    >
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

export function Button({
  label,
  onPress,
  primary = false,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  primary?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.choice,
        primary && styles.primary,
        disabled && styles.disabled,
      ]}
    >
      <Text style={[styles.label, primary && styles.primaryText]}>{label}</Text>
    </Pressable>
  );
}

export function WalkthroughSetup({
  busy,
  onStart,
}: {
  busy: boolean;
  onStart: (cursor: WalkthroughCursor) => void;
}) {
  const [goal, setGoal] = useState<Goal>('delegate');
  const [experience, setExperience] = useState<Experience>('new');
  const route = recommend(goal, experience);
  return (
    <View style={styles.content}>
      <Text accessibilityRole="header" style={styles.title}>
        Plan your walkthrough
      </Text>
      <Text style={styles.subtitle}>{identityPaperTitle}</Text>
      <Text style={styles.body}>Curated sample walkthrough</Text>
      <View accessibilityRole="radiogroup" style={styles.group}>
        <Text accessibilityRole="header" style={styles.heading}>
          What do you want to do?
        </Text>
        {goals.map(option => (
          <Choice
            key={option.id}
            label={option.label}
            selected={goal === option.id}
            onPress={() => setGoal(option.id)}
          />
        ))}
      </View>
      <View accessibilityRole="radiogroup" style={styles.group}>
        <Text accessibilityRole="header" style={styles.heading}>
          Your starting point
        </Text>
        <Choice
          label="New to agent identity"
          selected={experience === 'new'}
          onPress={() => setExperience('new')}
        />
        <Choice
          label="I know the basics"
          selected={experience === 'familiar'}
          onPress={() => setExperience('familiar')}
        />
      </View>
      <View style={styles.group}>
        <Text accessibilityRole="header" style={styles.heading}>
          Recommended path
        </Text>
        <Text style={styles.body}>
          {goals.find(option => option.id === goal)?.reason}
        </Text>
        {route.map(id => (
          <Text key={id} style={styles.pathItem}>
            {lessons[id].title}
          </Text>
        ))}
      </View>
      <Button
        label="Start walkthrough"
        primary
        disabled={busy}
        onPress={() => onStart(startWalkthrough(goal, experience))}
      />
    </View>
  );
}

export function WalkthroughLesson({
  cursor,
  busy,
  onChange,
  onSource,
}: {
  cursor: WalkthroughCursor;
  busy: boolean;
  onChange: (cursor: WalkthroughCursor) => void;
  onSource: (page: number) => void;
}) {
  const [showPath, setShowPath] = useState(false);
  const route = recommend(cursor.goal, cursor.experience);
  const index = route.indexOf(cursor.lesson);
  const lesson = lessons[cursor.lesson];
  const next = route[index + 1];
  return (
    <View style={styles.content}>
      <Text style={styles.subtitle}>{identityPaperTitle}</Text>
      <Text style={styles.progress}>{`Lesson ${index + 1} of ${
        route.length
      }`}</Text>
      <Text accessibilityRole="header" style={styles.title}>
        {lesson.title}
      </Text>
      <Text style={styles.body}>{lesson.explanation}</Text>
      <Text accessibilityRole="header" style={styles.heading}>
        Apply the idea
      </Text>
      <Text style={styles.body}>{lesson.application}</Text>
      {cursor.lesson === 'scopes' && (
        <View style={styles.experiment}>
          <Text accessibilityRole="header" style={styles.heading}>
            Delegation simulator
          </Text>
          <Text style={styles.body}>
            A worked example. The user gives the primary agent read and write
            access.
          </Text>
          <View style={styles.chain}>
            <Text style={styles.label}>Primary agent · Read and write</Text>
            <Text style={styles.label}>
              {cursor.helperCanWrite
                ? 'Helper grant · Read and write'
                : 'Helper grant · Read only'}
            </Text>
            <Text style={styles.label}>
              {cursor.resourceCanWrite
                ? 'Resource policy · Read and write'
                : 'Resource policy · Read only'}
            </Text>
          </View>
          <View style={styles.switchRow}>
            <Text style={styles.switchLabel}>Give the helper write access</Text>
            <Switch
              accessibilityLabel="Give the helper write access"
              disabled={busy}
              value={cursor.helperCanWrite}
              onValueChange={helperCanWrite =>
                onChange({ ...cursor, helperCanWrite })
              }
            />
          </View>
          <View style={styles.switchRow}>
            <Text style={styles.switchLabel}>Allow writes at the resource</Text>
            <Switch
              accessibilityLabel="Allow writes at the resource"
              disabled={busy}
              value={cursor.resourceCanWrite}
              onValueChange={resourceCanWrite =>
                onChange({ ...cursor, resourceCanWrite })
              }
            />
          </View>
          <View accessibilityRole="radiogroup" style={styles.group}>
            <Text style={styles.label}>Helper request</Text>
            <Choice
              label="Read"
              selected={cursor.request === 'read'}
              onPress={() => {
                if (!busy) onChange({ ...cursor, request: 'read' });
              }}
            />
            <Choice
              label="Write"
              selected={cursor.request === 'write'}
              onPress={() => {
                if (!busy) onChange({ ...cursor, request: 'write' });
              }}
            />
          </View>
          <View style={styles.result} accessibilityLiveRegion="polite">
            <Text style={styles.label}>{evaluateRequest(cursor)}</Text>
          </View>
        </View>
      )}
      <View style={styles.group}>
        <Text accessibilityRole="header" style={styles.heading}>
          Source references
        </Text>
        {lesson.sources.map(source => (
          <Button
            key={source.section}
            disabled={busy}
            label={`${source.section} · p. ${source.page}`}
            onPress={() => onSource(source.page)}
          />
        ))}
      </View>
      {next && (
        <Button
          label="Next lesson"
          primary
          disabled={busy}
          onPress={() => onChange({ ...cursor, lesson: next })}
        />
      )}
      <Button
        label={showPath ? 'Hide walkthrough path' : 'View walkthrough path'}
        onPress={() => setShowPath(!showPath)}
      />
      {showPath &&
        route.map(id => (
          <Button
            key={id}
            label={lessons[id].title}
            disabled={busy || id === cursor.lesson}
            onPress={() => onChange({ ...cursor, lesson: id })}
          />
        ))}
    </View>
  );
}

export const styles = StyleSheet.create({
  content: { gap: 16, paddingBottom: 20 },
  group: { gap: 8 },
  title: {
    color: '#25372B',
    fontSize: 32,
    fontWeight: '700',
    letterSpacing: -0.7,
  },
  subtitle: { color: '#526458', fontSize: 16, lineHeight: 24 },
  heading: { color: '#25372B', fontSize: 20, fontWeight: '700' },
  body: { color: '#44554A', fontSize: 16, lineHeight: 25 },
  progress: { color: '#355742', fontSize: 14, fontWeight: '700' },
  pathItem: {
    color: '#25372B',
    fontSize: 16,
    paddingLeft: 12,
    borderLeftWidth: 2,
    borderColor: '#7E9C84',
    paddingVertical: 3,
  },
  label: { color: '#25372B', fontSize: 15, fontWeight: '600', lineHeight: 22 },
  choice: {
    minHeight: 48,
    justifyContent: 'center',
    paddingVertical: 11,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: '#B4C2B6',
    borderRadius: 8,
  },
  selected: { borderColor: '#355742', backgroundColor: '#DBE7D9' },
  primary: { borderColor: '#355742', backgroundColor: '#355742' },
  primaryText: { color: '#FFFDF6' },
  disabled: { opacity: 0.62 },
  experiment: {
    backgroundColor: '#E8EEE5',
    borderRadius: 10,
    padding: 16,
    gap: 12,
  },
  chain: {
    borderLeftWidth: 2,
    borderColor: '#7E9C84',
    paddingLeft: 12,
    gap: 9,
  },
  switchRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  switchLabel: { flex: 1, color: '#25372B', fontSize: 15, lineHeight: 22 },
  result: { backgroundColor: '#F9FBF7', borderRadius: 8, padding: 12 },
});
