import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  api,
  NETWORK_ERROR_MESSAGE,
  TITLE_BLANK_MESSAGE,
  TITLE_MAX_LENGTH,
  TITLE_TOO_LONG_MESSAGE,
  Todo,
  validateTitle,
} from './api.ts'

const todo: Todo = { id: '1', title: 'Test', completed: false, createdAt: '2026-01-01T00:00:00Z' }

function mockFetch(response: Response) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('lists todos', async () => {
    const fetch = mockFetch(json([todo]))

    await expect(api.list()).resolves.toEqual([todo])
    expect(fetch).toHaveBeenCalledWith('/api/todos', expect.objectContaining({ headers: { 'Content-Type': 'application/json' } }))
  })

  it('creates a todo with a POST body', async () => {
    const fetch = mockFetch(json(todo, 201))

    await api.create('Test')

    expect(fetch).toHaveBeenCalledWith('/api/todos', expect.objectContaining({ method: 'POST', body: '{"title":"Test"}' }))
  })

  it('updates a todo with PATCH', async () => {
    const fetch = mockFetch(json({ ...todo, completed: true }))

    await expect(api.update('1', { completed: true })).resolves.toMatchObject({ completed: true })
    expect(fetch).toHaveBeenCalledWith('/api/todos/1', expect.objectContaining({ method: 'PATCH', body: '{"completed":true}' }))
  })

  it('handles 204 No Content on delete', async () => {
    mockFetch(new Response(null, { status: 204 }))

    await expect(api.remove('1')).resolves.toBeUndefined()
  })

  it('throws on an error status', async () => {
    mockFetch(new Response(null, { status: 500, statusText: 'Internal Server Error' }))

    await expect(api.list()).rejects.toThrow('500 Internal Server Error')
  })

  it('reports an unreachable server instead of "Failed to fetch"', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(api.create('Test')).rejects.toThrow(NETWORK_ERROR_MESSAGE)
  })

  it.each([502, 503, 504])('reports an unreachable backend behind the proxy (%i)', async (status) => {
    mockFetch(new Response('Backend unavailable', { status }))

    await expect(api.list()).rejects.toThrow(NETWORK_ERROR_MESSAGE)
  })

  it("uses the backend's problem-detail message", async () => {
    mockFetch(json({ title: 'Invalid request', status: 400, detail: TITLE_TOO_LONG_MESSAGE }, 400))

    await expect(api.create('x'.repeat(300))).rejects.toThrow(TITLE_TOO_LONG_MESSAGE)
  })
})

describe('validateTitle', () => {
  it('accepts a normal title', () => {
    expect(validateTitle('Buy milk')).toBeNull()
  })

  it('rejects empty and whitespace-only titles', () => {
    expect(validateTitle('')).toBe(TITLE_BLANK_MESSAGE)
    expect(validateTitle(' \t\n ')).toBe(TITLE_BLANK_MESSAGE)
  })

  it('accepts exactly the max length and rejects one more', () => {
    expect(validateTitle('a'.repeat(TITLE_MAX_LENGTH))).toBeNull()
    expect(validateTitle('a'.repeat(TITLE_MAX_LENGTH + 1))).toBe(TITLE_TOO_LONG_MESSAGE)
  })

  it('measures length after trimming, like the backend', () => {
    expect(validateTitle(`  ${'a'.repeat(TITLE_MAX_LENGTH)}  `)).toBeNull()
  })
})
