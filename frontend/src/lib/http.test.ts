import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NETWORK_ERROR_MESSAGE, onUnauthorized, request } from './http.ts'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function headersOf(call: unknown[]): Headers {
  return new Headers((call[1] as RequestInit).headers)
}

describe('request', () => {
  afterEach(() => vi.restoreAllMocks())

  it('prefixes /api and parses JSON', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ ok: true }))

    await expect(request('/things')).resolves.toEqual({ ok: true })
    expect(fetch.mock.calls[0][0]).toBe('/api/things')
  })

  it('does not send a CSRF token on GET', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json([]))

    await request('/things')

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(headersOf(fetch.mock.calls[0]).has('X-XSRF-TOKEN')).toBe(false)
  })

  it('echoes the XSRF-TOKEN cookie in a header on changes', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/'
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({}, 201))

    await request('/things', { method: 'POST', body: '{}' })

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(headersOf(fetch.mock.calls[0]).get('X-XSRF-TOKEN')).toBe('token-123')
    expect(headersOf(fetch.mock.calls[0]).get('Content-Type')).toBe('application/json')
  })

  it('fetches a CSRF token first when there is no cookie yet', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (url === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/' // what the browser does with Set-Cookie
        return new Response(null, { status: 204 })
      }
      return json({}, 201)
    })

    await request('/things', { method: 'POST', body: '{}' })

    expect(fetch.mock.calls.map((c) => c[0])).toEqual(['/api/auth/csrf', '/api/things'])
    expect(headersOf(fetch.mock.calls[1]).get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('leaves Content-Type to the browser for form bodies', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({}))

    await request('/login', { method: 'POST', body: new URLSearchParams({ a: 'b' }) })

    expect(headersOf(fetch.mock.calls[0]).has('Content-Type')).toBe(false)
  })

  it('reports an unreachable server instead of "Failed to fetch"', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(request('/things')).rejects.toThrow(NETWORK_ERROR_MESSAGE)
  })

  it.each([502, 503, 504])('reports an unreachable backend behind the proxy (%i)', async (status) => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('Backend unavailable', { status }))

    await expect(request('/things')).rejects.toThrow(NETWORK_ERROR_MESSAGE)
  })

  it("uses the backend's problem-detail message and status", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json({ status: 400, detail: 'Title must not be blank' }, 400))

    const error = await request('/things').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, message: 'Title must not be blank' })
  })

  it('falls back to the status line without a problem detail', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 500, statusText: 'Internal Server Error' }))

    await expect(request('/things')).rejects.toThrow('500 Internal Server Error')
  })

  it('returns undefined for 204 No Content', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    await expect(request('/things/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('notifies listeners on 401 so the app can return to the login page', async () => {
    const listener = vi.fn()
    const unsubscribe = onUnauthorized(listener)
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))

    await expect(request('/things')).rejects.toMatchObject({ status: 401 })
    expect(listener).toHaveBeenCalledTimes(1)

    unsubscribe()
    await request('/things').catch(() => {})
    expect(listener).toHaveBeenCalledTimes(1)
  })
})
