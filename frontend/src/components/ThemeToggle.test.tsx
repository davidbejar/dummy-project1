import { screen, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi, CurrentUser, Visitor } from '../features/auth/api.ts'
import { todosApi } from '../features/todos/api.ts'
import { ApiError } from '../lib/http.ts'
import { renderApp } from '../test/renderApp.tsx'

vi.mock('../features/auth/api.ts', () => ({ authApi: { me: vi.fn(), anonymous: vi.fn(), login: vi.fn(), logout: vi.fn() } }))
vi.mock('../features/todos/api.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../features/todos/api.ts')>()),
  todosApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

const mockedAuth = vi.mocked(authApi)
const mockedTodos = vi.mocked(todosApi)

const david: CurrentUser = {
  username: 'david',
  displayName: 'David Bejar',
  role: 'USER',
  roleDisplayName: 'User',
  screens: ['TODO'],
}
const anonymous: Visitor = { role: 'ANONYMOUS', roleDisplayName: 'Not logged', screens: ['LOGIN'] }

describe('theme toggle', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockedAuth.me.mockRejectedValue(new ApiError(401, '401 Unauthorized'))
    mockedAuth.anonymous.mockResolvedValue(anonymous)
    mockedTodos.list.mockResolvedValue([])
  })

  it('is in the header of the login page', async () => {
    renderApp('/')

    await screen.findByRole('heading', { name: 'Sign in' })
    expect(within(screen.getByRole('banner')).getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument()
  })

  it('is in the header of Todos for a signed-in user', async () => {
    mockedAuth.me.mockResolvedValue(david)
    renderApp('/todos')

    await screen.findByRole('heading', { name: 'Todos' })
    expect(within(screen.getByRole('banner')).getByRole('button', { name: 'Switch to dark theme' })).toBeInTheDocument()
  })

  it('switches the theme back and forth and remembers the choice', async () => {
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Switch to dark theme' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('theme')).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Switch to light theme' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('theme')).toBe('light')
  })

  it('starts from the stored choice', async () => {
    localStorage.setItem('theme', 'dark')
    renderApp('/')

    expect(await screen.findByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument()
  })
})
