import type { ApiErrorPayload } from '../shared/types/api'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'

type ApiOptions = RequestInit & {
  token?: string | null
}

export class ApiClientError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
    this.name = 'ApiClientError'
  }
}

export async function apiClient<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const hasBody = options.body !== undefined && options.body !== null

  if (hasBody && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: 'include',
  })

  const text = await response.text()
  const data = text ? tryParseJson(text) : null

  if (!response.ok) {
    const payload = data as ApiErrorPayload | null
    throw new ApiClientError(payload?.message ?? `Request failed with status ${response.status}`, response.status)
  }

  return data as T
}

export function toQuery(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

function tryParseJson(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}
