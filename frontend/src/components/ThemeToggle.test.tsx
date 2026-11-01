import { act, screen, within } from '@testing-library/react'
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

// The picker never consults matchMedia: "System" leaves data-theme unset and CSS follows the OS.
describe('theme picker', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockedAuth.me.mockRejectedValue(new ApiError(401, '401 Unauthorized'))
    mockedAuth.anonymous.mockResolvedValue(anonymous)
    mockedTodos.list.mockResolvedValue([])
  })

  function picker() {
    return within(screen.getByRole('banner')).getByRole('combobox', { name: 'Theme' })
  }

  it('is in the header of the login page and follows the system by default', async () => {
    renderApp('/')

    await screen.findByRole('heading', { name: 'Sign in' })
    expect(picker()).toHaveDisplayValue('System')
    expect(document.documentElement.dataset.theme).toBeUndefined()
  })

  it('is in the header of Todos for a signed-in user', async () => {
    mockedAuth.me.mockResolvedValue(david)
    renderApp('/todos')

    await screen.findByRole('heading', { name: 'Todos' })
    expect(picker()).toHaveDisplayValue('System')
  })

  it('applies and remembers a choice, and can go back to following the system', async () => {
    const user = userEvent.setup()
    renderApp('/')
    await screen.findByRole('heading', { name: 'Sign in' })

    await user.selectOptions(picker(), 'Dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('theme')).toBe('dark')

    await user.selectOptions(picker(), 'Light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('theme')).toBe('light')

    await user.selectOptions(picker(), 'System')
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(localStorage.getItem('theme')).toBeNull()
  })

  it('shows the theme already on the page', async () => {
    document.documentElement.dataset.theme = 'dark' // as public/theme-init.js leaves it
    renderApp('/')

    await screen.findByRole('heading', { name: 'Sign in' })
    expect(picker()).toHaveDisplayValue('Dark')
  })

  it('follows a choice made in another tab', async () => {
    renderApp('/')
    await screen.findByRole('heading', { name: 'Sign in' })

    act(() => dispatchEvent(new StorageEvent('storage', { key: 'theme', newValue: 'dark' })))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(picker()).toHaveDisplayValue('Dark')

    act(() => dispatchEvent(new StorageEvent('storage', { key: 'theme', newValue: null })))
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(picker()).toHaveDisplayValue('System')
  })
})
