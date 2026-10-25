import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TITLE_BLANK_MESSAGE, TITLE_MAX_LENGTH, TITLE_TOO_LONG_MESSAGE, Todo, todosApi, validateTitle } from './api.ts'

const todo: Todo = { id: '1', title: 'Test', completed: false, createdAt: '2026-01-01T00:00:00Z' }

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('todosApi', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
  })

  it('lists todos', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json([todo]))

    await expect(todosApi.list()).resolves.toEqual([todo])
    expect(fetch.mock.calls[0][0]).toBe('/api/todos')
  })

  it('creates a todo with a POST body', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(todo, 201))

    await todosApi.create('Test')

    expect(fetch).toHaveBeenCalledWith('/api/todos', expect.objectContaining({ method: 'POST', body: '{"title":"Test"}' }))
  })

  it('updates a todo with PATCH', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ ...todo, completed: true }))

    await expect(todosApi.update('1', { completed: true })).resolves.toMatchObject({ completed: true })
    expect(fetch).toHaveBeenCalledWith('/api/todos/1', expect.objectContaining({ method: 'PATCH', body: '{"completed":true}' }))
  })

  it('deletes a todo', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    await expect(todosApi.remove('1')).resolves.toBeUndefined()
    expect(fetch).toHaveBeenCalledWith('/api/todos/1', expect.objectContaining({ method: 'DELETE' }))
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
