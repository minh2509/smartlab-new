function value(name: string) {
  const result = process.env[name]?.trim()
  return result || undefined
}

function positiveInteger(name: string) {
  const raw = value(name)
  if (!raw) return undefined
  const parsed = Number(raw)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer, received "${raw}".`)
  }
  return parsed
}

export const env = Object.freeze({
  apiBaseURL: (value('E2E_API_BASE_URL') ?? 'http://127.0.0.1:8080/api/v1.0').replace(/\/+$/, ''),
  admin: {
    email: value('E2E_ADMIN_EMAIL'),
    password: value('E2E_ADMIN_PASSWORD'),
  },
  member: {
    email: value('E2E_MEMBER_EMAIL'),
    password: value('E2E_MEMBER_PASSWORD'),
    researchFieldName: value('E2E_RESEARCH_FIELD_NAME'),
  },
  publicData: {
    memberName: value('E2E_PUBLIC_MEMBER_NAME'),
    researchFieldName: value('E2E_PUBLIC_RESEARCH_FIELD_NAME'),
    projectName: value('E2E_PUBLIC_PROJECT_NAME'),
    postTitle: value('E2E_PUBLIC_POST_TITLE'),
    eventTitle: value('E2E_PUBLIC_EVENT_TITLE'),
  },
  journey: {
    projectId: positiveInteger('E2E_MEMBER_PROJECT_ID'),
    projectName: value('E2E_MEMBER_PROJECT_NAME'),
    taskId: positiveInteger('E2E_MEMBER_TASK_ID'),
    taskTitle: value('E2E_MEMBER_TASK_TITLE'),
    evaluationMarker: value('E2E_MEMBER_EVALUATION_MARKER'),
    reviewedPostTitle: value('E2E_MEMBER_REVIEWED_POST_TITLE'),
    reviewedPostStatus: value('E2E_MEMBER_REVIEWED_POST_STATUS') ?? 'Đã xuất bản',
    notificationMarker: value('E2E_MEMBER_NOTIFICATION_MARKER'),
    mutate: value('E2E_RUN_MUTATING_JOURNEY') === 'true',
  },
})

export function missing(values: Record<string, unknown>) {
  return Object.entries(values)
    .filter(([, item]) => item === undefined || item === '')
    .map(([name]) => name)
}
