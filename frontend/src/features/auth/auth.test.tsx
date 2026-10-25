import { screen, waitFor, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from '../../lib/http.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { todosApi } from '../todos/api.ts'
import { authApi, CurrentUser, Visitor } from './api.ts'

vi.mock('./api.ts', () => ({ authApi: { me: vi.fn(), anonymous: vi.fn(), login: vi.fn(), logout: vi.fn() } }))
vi.mock('../todos/api.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../todos/api.ts')>()),
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
const notSignedIn = () => new ApiError(401, '401 Unauthorized')

async function signIn(username: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
  return user
}

describe('authentication', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockedAuth.me.mockRejectedValue(notSignedIn())
    mockedAuth.anonymous.mockResolvedValue(anonymous)
    mockedTodos.list.mockResolvedValue([])
  })

  it('shows the login page as the landing page', async () => {
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    expect(within(screen.getByRole('banner')).getByText('Not logged')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
  })

  it('still offers the login page when the backend cannot describe anonymous access', async () => {
    mockedAuth.anonymous.mockRejectedValue(new Error('down'))
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('lets anonymous visitors open screens their role allows', async () => {
    mockedAuth.anonymous.mockResolvedValue({ ...anonymous, screens: ['LOGIN', 'TODO'] })
    renderApp('/todos')

    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()
    expect(within(screen.getByRole('banner')).getByText('Not logged')).toBeInTheDocument()
  })

  it('redirects to the login page when opening Todos without a session', async () => {
    const router = renderApp('/todos')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(mockedTodos.list).not.toHaveBeenCalled()
  })

  it('logs in and shows Todos with the user name in the header', async () => {
    mockedAuth.login.mockResolvedValue(david)
    const router = renderApp('/')

    await signIn('david', 'david')

    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/todos')
    expect(mockedAuth.login).toHaveBeenCalledWith('david', 'david')
    expect(within(screen.getByRole('banner')).getByText('David Bejar')).toBeInTheDocument()
  })

  it('shows the error and clears the password on wrong credentials', async () => {
    mockedAuth.login.mockRejectedValue(new ApiError(401, 'Invalid username or password'))
    renderApp('/')

    await signIn('david', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Username')).toHaveValue('david')
    expect(screen.queryByRole('heading', { name: 'Todos' })).not.toBeInTheDocument()
  })

  it('asks for both fields before calling the backend', async () => {
    renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter your username and password')
    expect(mockedAuth.login).not.toHaveBeenCalled()
  })

  it('keeps an existing session and skips the login page', async () => {
    mockedAuth.me.mockResolvedValue(david)
    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/todos')
  })

  it('opens Todos directly with an existing session', async () => {
    mockedAuth.me.mockResolvedValue(david)
    renderApp('/todos')

    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()
    expect(screen.getByText('David Bejar')).toBeInTheDocument()
  })

  it('logs out and returns to the login page', async () => {
    mockedAuth.me.mockResolvedValue(david)
    mockedAuth.logout.mockResolvedValue(undefined)
    const router = renderApp('/todos')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(mockedAuth.logout).toHaveBeenCalledTimes(1)
    expect(router.state.location.pathname).toBe('/')
    expect(screen.queryByText('David Bejar')).not.toBeInTheDocument()
    expect(screen.getByText('Not logged')).toBeInTheDocument()
  })

  it('returns to the login page when the session expires', async () => {
    mockedAuth.me.mockResolvedValue(david)
    renderApp('/todos')
    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()

    await expireSession()

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('redirects unknown paths to the login page', async () => {
    const router = renderApp('/no-such-page')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })
})

/** Simulates any API call getting a 401 by going through the real http client. */
async function expireSession() {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))
  await request('/anything').catch(() => {})
}
