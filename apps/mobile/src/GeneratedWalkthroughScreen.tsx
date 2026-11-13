import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Button, Choice, styles } from './WalkthroughScreen';
import { library } from './library';
import { lessonText } from './generatedWalkthrough';
import type {
  GeneratedCursor,
  GenerationStatus,
  LearnerProfile,
  LearningEdition,
  Option,
  Pass,
  Question,
} from './generatedWalkthrough';

const exampleProfile: LearnerProfile = {
  background:
    'Staff IAM engineer who already works with OAuth, identity providers, service identities and policy enforcement.',
  goal: 'Design and review a cross-domain multi-agent workflow with recursive sub-agent delegation that keeps working after logout but preserves least privilege, revocation and attributable audit.',
  preference:
    'Start with a concrete design problem and focus on decision logic, trust boundaries and negative tests.',
};

const profileFields = [
  {
    key: 'background',
    label: 'Your background',
    hint: 'What do you already know about this topic?',
  },
  {
    key: 'goal',
    label: 'What do you want to learn?',
    hint: 'Describe what you want to be able to do.',
  },
  {
    key: 'preference',
    label: 'How do you prefer to learn?',
    hint: 'For example, start with a worked example.',
  },
] as const;

export function GeneratedSetup({
  title,
  busy,
  onStart,
}: {
  title: string;
  busy: boolean;
  onStart: (profile: LearnerProfile) => void;
}) {
  const profile = useRef({ background: '', goal: '', preference: '' });
  const [complete, setComplete] = useState(false);
  const [exampleCount, setExampleCount] = useState(0);
  return (
    <View style={styles.content}>
      <Text accessibilityRole="header" style={styles.title}>
        Build learning edition
      </Text>
      <Text style={styles.subtitle}>{title}</Text>
      <Button
        label="Fill an example learner profile"
        disabled={busy}
        onPress={() => {
          profile.current = { ...exampleProfile };
          setComplete(true);
          setExampleCount(count => count + 1);
        }}
      />
      {profileFields.map(({ key, label, hint }) => (
        <View style={styles.group} key={key}>
          <Text style={styles.label}>{label}</Text>
          <TextInput
            key={exampleCount}
            defaultValue={exampleCount ? exampleProfile[key] : ''}
            accessibilityLabel={label}
            placeholder={hint}
            placeholderTextColor="#58665B"
            style={ui.input}
            onChangeText={value => {
              profile.current[key] = value;
              setComplete(
                profileFields.every(
                  field => !!profile.current[field.key].trim(),
                ),
              );
            }}
            editable={!busy}
            multiline
            maxLength={500}
          />
        </View>
      ))}
      <Text style={styles.body}>
        Readle sends the whole PDF and your profile to the Mac connected by USB.
        Local models build Concepts, Technical, and Assessment passes. The Mac
        uses Qwen 27B to write lessons and Ministral 8B to review them.
        Generation can take several minutes. Saved lessons are available
        offline.
      </Text>
      <Button
        label="Generate on connected Mac"
        primary
        disabled={!complete || busy}
        onPress={() =>
          onStart({
            background: profile.current.background.trim(),
            goal: profile.current.goal.trim(),
            preference: profile.current.preference.trim(),
          })
        }
      />
    </View>
  );
}

export function GenerationProgress({
  sessionId,
  start,
  onReady,
}: {
  sessionId: string;
  start: boolean;
  onReady: (edition: LearningEdition) => void;
}) {
  const [status, setStatus] = useState<GenerationStatus>(
    start
      ? { state: 'running', message: 'Connecting to the Mac' }
      : { state: 'pending' },
  );
  const [request, setRequest] = useState({ send: start, attempt: 0 });
  const ready = useRef(onReady);
  useEffect(() => {
    ready.current = onReady;
  }, [onReady]);
  useEffect(() => {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    async function check(send: boolean) {
      try {
        const next = await library!.generationStatus(sessionId, send);
        if (stopped) {
          return;
        }
        setStatus(next);
        if (next.state === 'ready') {
          ready.current(next.edition);
        } else if (next.state === 'running') {
          timer = setTimeout(() => check(false), 4000);
        }
      } catch (error) {
        if (!stopped) {
          setStatus({
            state: 'failed',
            message:
              error instanceof Error
                ? error.message
                : 'Reconnect to the generator and retry.',
          });
        }
      }
    }
    check(request.send);
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [request, sessionId]);
  return (
    <View style={styles.content}>
      <Text accessibilityRole="header" style={styles.title}>
        Building your walkthrough
      </Text>
      <Text style={styles.body} accessibilityLiveRegion="polite">
        {status.state === 'running' || status.state === 'failed'
          ? status.message
          : 'The local generator is ready to receive your PDF.'}
      </Text>
      {status.state === 'running' ? (
        <View style={styles.group}>
          <ActivityIndicator color="#355742" />
          <Text style={styles.body}>
            You can return to this session while your Mac continues working.
          </Text>
        </View>
      ) : (
        status.state !== 'ready' && (
          <Button
            label="Retry generation"
            primary
            onPress={() => {
              setStatus({ state: 'running', message: 'Connecting to the Mac' });
              setRequest(current => ({
                send: true,
                attempt: current.attempt + 1,
              }));
            }}
          />
        )
      )}
    </View>
  );
}

function Section({ title, content }: { title: string; content: string }) {
  return (
    <View style={styles.group}>
      <Text accessibilityRole="header" style={styles.heading}>
        {title}
      </Text>
      <Text style={styles.body}>{lessonText(content)}</Text>
    </View>
  );
}

const optionKeys: Option[] = ['a', 'b', 'c', 'd'];
const passes = [
  { id: 1, name: 'Concepts' },
  { id: 2, name: 'Technical' },
  { id: 3, name: 'Assessment' },
] as const;

export function GeneratedLessonScreen({
  cursor,
  edition,
  busy,
  onChange,
  onSource,
}: {
  cursor: GeneratedCursor;
  edition: LearningEdition;
  busy: boolean;
  onChange: (cursor: GeneratedCursor) => void;
  onSource: (pageIndex: number) => void;
}) {
  const [showPath, setShowPath] = useState(false);
  const lesson = edition.lessons[cursor.lesson];
  if (!lesson) {
    return <Text style={styles.body}>This lesson could not be restored.</Text>;
  }
  function references(ids: string[]) {
    return [...new Set(ids)].map(id => {
      const source = edition.citations[id];
      return (
        source && (
          <Button
            key={id}
            disabled={busy}
            label={`PDF page ${source.page} · ${source.concept}`}
            onPress={() => onSource(source.page - 1)}
          />
        )
      );
    });
  }
  function assessmentItem(question: Question, index: number) {
    const key = `${lesson.id}-${index}`;
    const submitted = cursor.submissions[key];
    const choice = submitted ?? cursor.drafts[key];
    const verified = lesson.assessment_key_matches[index];
    return (
      <View key={key} style={styles.experiment}>
        <Text accessibilityRole="header" style={styles.heading}>
          {index === 0 ? 'Concept question' : 'Apply the concept'}
        </Text>
        <Text style={styles.body}>{lessonText(question.stem)}</Text>
        <View accessibilityRole="radiogroup" style={styles.group}>
          {optionKeys.map(option => (
            <Choice
              key={option}
              label={lessonText(question.options[option])}
              selected={choice === option}
              disabled={busy || !!submitted || !verified}
              onPress={() =>
                onChange({
                  ...cursor,
                  drafts: { ...cursor.drafts, [key]: option },
                })
              }
            />
          ))}
        </View>
        {!verified ? (
          <Text style={styles.body}>
            This question needs review. It is excluded from scoring.
          </Text>
        ) : submitted ? (
          <View style={styles.result} accessibilityLiveRegion="polite">
            <Text style={styles.label}>
              {submitted === question.answer ? 'Correct' : 'Incorrect'}
            </Text>
            <Text style={styles.body}>{lessonText(question.rationale)}</Text>
            {references(question.source_ids)}
          </View>
        ) : (
          <Button
            label="Submit answer"
            primary
            disabled={!choice || busy}
            onPress={() => {
              if (choice) {
                onChange({
                  ...cursor,
                  submissions: { ...cursor.submissions, [key]: choice },
                });
              }
            }}
          />
        )}
      </View>
    );
  }
  const content = cursor.pass === 1 ? lesson.concepts : lesson.technical;
  const selectPass = (pass: Pass) => onChange({ ...cursor, pass });
  return (
    <View style={styles.content}>
      <Text style={styles.progress}>{`Lesson ${cursor.lesson + 1} of ${
        edition.lessons.length
      }`}</Text>
      <Text accessibilityRole="header" style={styles.title}>
        {lessonText(lesson.title)}
      </Text>
      <Text style={styles.body}>{lessonText(lesson.objective)}</Text>
      <View style={ui.passes} accessibilityRole="radiogroup">
        {passes.map(pass => (
          <Choice
            key={pass.id}
            label={`Pass ${pass.id} · ${pass.name}`}
            selected={cursor.pass === pass.id}
            disabled={busy}
            onPress={() => selectPass(pass.id)}
          />
        ))}
      </View>
      {lesson.review.lesson_status === 'needs_review' && (
        <Text style={styles.body}>
          This lesson explanation needs source review.
        </Text>
      )}
      {cursor.pass === 1 && (
        <>
          <Section title="Explanation" content={lesson.concepts.explanation} />
          <Section title="Worked example" content={lesson.concepts.example} />
        </>
      )}
      {cursor.pass === 2 && (
        <>
          <Section title="Assumptions" content={lesson.technical.assumptions} />
          <Section title="Mechanism" content={lesson.technical.mechanism} />
          <Section
            title="Failure mode"
            content={lesson.technical.failure_mode}
          />
          <Section
            title="Engineering exercise"
            content={lesson.technical.exercise}
          />
        </>
      )}
      {cursor.pass === 3 ? (
        lesson.assessment.map(assessmentItem)
      ) : (
        <>
          <View style={styles.experiment}>
            <Section title="Ungraded checkpoint" content={content.checkpoint} />
          </View>
          <View style={styles.group}>
            <Text accessibilityRole="header" style={styles.heading}>
              Source references
            </Text>
            {references(content.source_ids)}
          </View>
        </>
      )}
      {cursor.lesson + 1 < edition.lessons.length && (
        <Button
          label="Next lesson"
          primary
          disabled={busy}
          onPress={() => onChange({ ...cursor, lesson: cursor.lesson + 1 })}
        />
      )}
      <Button
        label={showPath ? 'Hide walkthrough path' : 'View walkthrough path'}
        onPress={() => setShowPath(!showPath)}
      />
      {showPath && (
        <>
          <Text style={styles.subtitle}>{edition.title}</Text>
          <Text style={styles.body}>{lessonText(edition.rationale)}</Text>
          {edition.lessons.map((unit, index) => (
            <Button
              key={unit.id}
              label={lessonText(unit.title)}
              disabled={busy || cursor.lesson === index}
              onPress={() => onChange({ ...cursor, lesson: index })}
            />
          ))}
        </>
      )}
    </View>
  );
}

const ui = StyleSheet.create({
  input: {
    minHeight: 75,
    borderColor: '#B4C2B6',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    color: '#25372B',
    backgroundColor: '#F9FBF7',
    fontSize: 16,
    textAlignVertical: 'top',
  },
  passes: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
});
