export const identityPaperVersion =
  'b019c9420565afe6a3f81fcd8e9b3375f546003cecab93ecfa25d965b2f68bb2';
export const identityPaperTitle = 'Identity Management for Agentic AI';

export const goals = [
  {
    id: 'delegate',
    label: 'Design permissions for sub-agents',
    reason:
      'Start with a sub-agent request and trace the authority back to the user.',
  },
  {
    id: 'govern',
    label: 'Review an agent for security risks',
    reason:
      'Start with an agent that outlives a login, then decide where to enforce and audit policy.',
  },
  {
    id: 'understand',
    label: 'Understand agent identity',
    reason:
      'Build the identity and permission model first, then apply it to a chain of agents.',
  },
] as const;
export type Goal = (typeof goals)[number]['id'];
export type Experience = 'new' | 'familiar';
export type Permission = 'read' | 'write';

// The printed page is one less than the PDF's one-based page number in this edition.
type Source = { section: string; page: number };
type Lesson = {
  title: string;
  explanation: string;
  application: string;
  sources: readonly Source[];
};

export const lessons = {
  identity: {
    title: 'Identify the user and the actor',
    explanation:
      'An agent can select its next action as a task unfolds. A service needs to know which agent is calling, whose authority it is using, and what operation that authority covers. Agent identity identifies the caller. Delegated authority defines what it may do for the user.',
    application:
      'For a support agent reading a case, distinguish the customer who granted access, the agent instance that made the request, and the case the grant covers.',
    sources: [
      { section: '1.2 · Agent authentication and authorization', page: 6 },
      { section: '2.8 · Identity for AI agents', page: 11 },
    ],
  },
  scenario: {
    title: 'Give a helper a narrower task',
    explanation:
      'The paper’s recursive delegation example starts with an analysis agent that can access several resources. Its collection helper needs a fraction of that access and budget. The service handling the final request needs evidence of the chain that authorized it.',
    application:
      'Imagine a primary agent that can read and update a project. Give its research helper access to read the project, then trace what happens when the helper attempts an update.',
    sources: [
      { section: '4.4 · Recursive delegation', page: 26 },
      { section: '3.2 · Transitive trust', page: 18 },
    ],
  },
  scopes: {
    title: 'Check how permissions narrow',
    explanation:
      'Scope attenuation reduces authority as a task is passed to another agent. The final operation must fit the user’s original grant, the helper’s narrower grant, and the resource’s own policy. The paper discusses centralized token exchange and offline attenuation as approaches with different trust and revocation trade-offs.',
    application:
      'Use the example below to change the helper grant, the requested operation and the resource policy. The permission check recomputes for each combination.',
    sources: [
      { section: '3.2 · Scope attenuation', page: 18 },
      { section: '2.10 · Policy decision and enforcement', page: 13 },
    ],
  },
  audit: {
    title: 'Trace an action to its authorization',
    explanation:
      'The paper proposes preserving both the user who delegated authority and the agent that acted. The enforcement point can use that context to make a decision and record who did what on whose behalf.',
    application:
      'Sketch an audit event for a denied write. Include the initiating user, the agent instance, the resource, the requested operation and the decision. Check whether an investigator could reconstruct the delegation.',
    sources: [
      { section: '2.11 · Closing the auditability gap', page: 14 },
      { section: '3.2 · On-behalf-of delegation', page: 18 },
    ],
  },
  durability: {
    title: 'Plan for work that outlives a login',
    explanation:
      'The onboarding example spans several systems and may take days. The paper proposes an independently identifiable agent with durable delegated authority, bounded policy and a way to request an exceptional approval during the workflow.',
    application:
      'For an onboarding agent, decide which actions fit the standing grant, which need a fresh approval, and which systems must terminate access if the agent is compromised.',
    sources: [
      { section: '4.2 · Durable delegated authority', page: 25 },
      { section: '2.7 · Asynchronous authorization', page: 10 },
    ],
  },
  enforcement: {
    title: 'Enforce policy at the resource',
    explanation:
      'An agent’s internal decision can be wrong. The paper describes a policy decision point that evaluates rules and a policy enforcement point that applies the decision at the resource boundary. The same boundary can record the user and agent that led to a request.',
    application:
      'Place the check in front of a write API. Decide what evidence the service needs for the actor, delegated scope and approved destination before the write can proceed.',
    sources: [
      { section: '2.10 · Operationalizing authorization', page: 13 },
      { section: '4.1 · Consent fatigue', page: 25 },
    ],
  },
  revocation: {
    title: 'Bound the impact of revocation delays',
    explanation:
      'The paper describes the challenge of propagating revocation across multiple systems and previously delegated tokens. It also distinguishes stopping active access from removing the agent identity and its entitlements.',
    application:
      'Choose an expiration and operation limit for a temporary collection helper. Then name the systems that would need a revocation signal and the entitlements that off-boarding must remove.',
    sources: [
      { section: '3.2 · Revocation and off-boarding', page: 19 },
      { section: '5 · Conclusion', page: 28 },
    ],
  },
} satisfies Record<string, Lesson>;

export type LessonId = keyof typeof lessons;
const routes: Record<Goal, readonly LessonId[]> = {
  delegate: ['scenario', 'scopes', 'audit', 'revocation'],
  govern: ['durability', 'enforcement', 'audit', 'revocation'],
  understand: ['identity', 'enforcement', 'scenario', 'scopes', 'audit'],
};

export function recommend(
  goal: Goal,
  experience: Experience,
): readonly LessonId[] {
  const route = routes[goal];
  if (goal === 'understand') {
    return experience === 'new' ? route : route.slice(1);
  }
  return experience === 'new'
    ? [route[0], 'identity', ...route.slice(1)]
    : route;
}

export type WalkthroughCursor = {
  version: 1;
  goal: Goal;
  experience: Experience;
  lesson: LessonId;
  helperCanWrite: boolean;
  resourceCanWrite: boolean;
  request: Permission;
};

export function startWalkthrough(
  goal: Goal,
  experience: Experience,
): WalkthroughCursor {
  return {
    version: 1,
    goal,
    experience,
    lesson: recommend(goal, experience)[0],
    helperCanWrite: false,
    resourceCanWrite: true,
    request: 'write',
  };
}

export function isWalkthroughCursor(
  value: unknown,
): value is WalkthroughCursor {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const c = value as Partial<WalkthroughCursor>;
  return (
    c.version === 1 &&
    !!c.goal &&
    goals.some(goal => goal.id === c.goal) &&
    (c.experience === 'new' || c.experience === 'familiar') &&
    !!c.lesson &&
    recommend(c.goal, c.experience).includes(c.lesson) &&
    typeof c.helperCanWrite === 'boolean' &&
    typeof c.resourceCanWrite === 'boolean' &&
    (c.request === 'read' || c.request === 'write')
  );
}

export function evaluateRequest(cursor: WalkthroughCursor): string {
  if (cursor.request === 'read') {
    return 'Allowed. Read is in the user grant, the helper grant and the resource policy.';
  }
  if (!cursor.helperCanWrite) {
    return 'Denied. The helper received read access only.';
  }
  if (!cursor.resourceCanWrite) {
    return 'Denied. The resource policy permits reads only.';
  }
  return 'Allowed. Write is in the user grant, the helper grant and the resource policy.';
}
