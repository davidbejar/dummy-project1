import { request } from '../../lib/http.ts'

/** Must match Todo.TITLE_MAX_LENGTH in the backend and the VARCHAR length in the database. */
export const TITLE_MAX_LENGTH = 255

export const TITLE_BLANK_MESSAGE = 'Title must not be blank'
export const TITLE_TOO_LONG_MESSAGE = `Title must be at most ${TITLE_MAX_LENGTH} characters`

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

export const todosApi = {
  list: () => request<Todo[]>('/todos'),
  create: (title: string) =>
    request<Todo>('/todos', { method: 'POST', body: JSON.stringify({ title }) }),
  update: (id: string, patch: Partial<Pick<Todo, 'title' | 'completed'>>) =>
    request<Todo>(`/todos/${id}`, { method: 'PATCH', body: JSON.stringify(patch) }),
  remove: (id: string) => request<void>(`/todos/${id}`, { method: 'DELETE' }),
}
