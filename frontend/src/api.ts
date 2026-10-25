/** Must match Todo.TITLE_MAX_LENGTH in the backend and the VARCHAR length in the database. */
export const TITLE_MAX_LENGTH = 255

export const TITLE_BLANK_MESSAGE = 'Title must not be blank'
export const TITLE_TOO_LONG_MESSAGE = `Title must be at most ${TITLE_MAX_LENGTH} characters`
export const NETWORK_ERROR_MESSAGE = "Can't reach the server. Is the backend running?"

/** Mirrors the backend Todo entity as serialized to JSON. */
export interface Todo {
  id: string // UUID
  title: string
  completed: boolean
  createdAt: string // ISO-8601 timestamp with offset
}

/** Same rules the backend enforces; returns an error message or null when valid. */
export function validateTitle(title: string): string | null {
  const trimmed = title.trim()
  if (!trimmed) return TITLE_BLANK_MESSAGE
  if (trimmed.length > TITLE_MAX_LENGTH) return TITLE_TOO_LONG_MESSAGE
  return null
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    })
  } catch {
    // fetch only rejects when no response arrived at all (server down, network error).
    throw new Error(NETWORK_ERROR_MESSAGE)
  }
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.status === 204 ? (undefined as T) : res.json()
}

// Gateway errors come from a proxy in front of the backend (Vite in development).
const UNREACHABLE_STATUSES = [502, 503, 504]

/** Prefers the backend's problem-detail message; falls back to the HTTP status. */
async function errorMessage(res: Response): Promise<string> {
  if (UNREACHABLE_STATUSES.includes(res.status)) return NETWORK_ERROR_MESSAGE
  try {
    const body = await res.json()
    if (typeof body?.detail === 'string' && body.detail) return body.detail
  } catch {
    // Not JSON; use the status line below.
  }
  return `${res.status} ${res.statusText}`.trim()
}

export const api = {
  list: () => request<Todo[]>('/todos'),
  create: (title: string) =>
    request<Todo>('/todos', { method: 'POST', body: JSON.stringify({ title }) }),
  update: (id: string, patch: Partial<Pick<Todo, 'title' | 'completed'>>) =>
    request<Todo>(`/todos/${id}`, { method: 'PATCH', body: JSON.stringify(patch) }),
  remove: (id: string) => request<void>(`/todos/${id}`, { method: 'DELETE' }),
}
