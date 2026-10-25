import { screen, waitFor, within } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../lib/http.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { authApi, CurrentUser } from '../auth/api.ts'
import { todosApi } from '../todos/api.ts'
import { AdminRole, AdminUser, rolesApi, usersApi } from './api.ts'

vi.mock('../auth/api.ts', () => ({ authApi: { me: vi.fn(), anonymous: vi.fn(), login: vi.fn(), logout: vi.fn() } }))
vi.mock('../todos/api.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../todos/api.ts')>()),
  todosApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('./api.ts', () => ({
  usersApi: { list: vi.fn(), roles: vi.fn(), create: vi.fn(), remove: vi.fn() },
  rolesApi: { list: vi.fn(), screens: vi.fn(), create: vi.fn(), remove: vi.fn(), assign: vi.fn(), unassign: vi.fn() },
}))

const mockedAuth = vi.mocked(authApi)
const mockedUsers = vi.mocked(usersApi)
const mockedRoles = vi.mocked(rolesApi)

const ALL_SCREENS = ['TODO', 'ADMINISTRATION', 'USER_ADMIN', 'ROLE_ADMIN']
const admin: CurrentUser = { username: 'admin', displayName: 'Administrator', role: 'ADMIN', roleDisplayName: 'Administrator', screens: ALL_SCREENS }
const david: CurrentUser = { username: 'david', displayName: 'David Bejar', role: 'USER', roleDisplayName: 'User', screens: ['TODO'] }
const miniadmin: CurrentUser = {
  username: 'miniadmin',
  displayName: 'Mini Admin',
  role: 'MINIADMIN',
  roleDisplayName: 'Mini admin',
  screens: ['TODO', 'ADMINISTRATION', 'USER_ADMIN'],
}

const user = (username: string, overrides: Partial<AdminUser> = {}): AdminUser => ({
  id: `id-${username}`,
  username,
  displayName: username,
  role: 'USER',
  roleDisplayName: 'User',
  deletable: true,
  ...overrides,
})
const adminRole: AdminRole = { code: 'ADMIN', displayName: 'Administrator', screens: ALL_SCREENS, lockedScreens: ALL_SCREENS, deletable: false }
const userRole: AdminRole = { code: 'USER', displayName: 'User', screens: ['TODO'], lockedScreens: [], deletable: true }

function signedInAs(current: CurrentUser) {
  mockedAuth.me.mockResolvedValue(current)
}

describe('screen permissions', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(todosApi).list.mockResolvedValue([])
    mockedUsers.list.mockResolvedValue([user('admin', { deletable: false }), user('david', { deletable: false })])
    mockedUsers.roles.mockResolvedValue([{ code: 'ADMIN', displayName: 'Administrator' }, { code: 'USER', displayName: 'User' }])
    mockedRoles.list.mockResolvedValue([adminRole, userRole])
    mockedRoles.screens.mockResolvedValue(ALL_SCREENS.map((code) => ({ code, displayName: code, parentCode: null })))
  })

  it('shows admins the administration menu with both options', async () => {
    signedInAs(admin)
    renderApp('/todos')
    const user = userEvent.setup()

    await user.click(await within(screen.getByRole('banner')).findByRole('link', { name: 'Administration' }))

    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Administration' })
    expect(within(menu).getByRole('link', { name: 'User administration' })).toHaveAttribute('href', '/admin/users')
    expect(within(menu).getByRole('link', { name: 'Role administration' })).toHaveAttribute('href', '/admin/roles')
  })

  it('tells a user without the screen that they lack permission', async () => {
    signedInAs(david)
    renderApp('/admin')

    expect(await screen.findByRole('heading', { name: 'Not enough permissions' })).toBeInTheDocument()
    expect(within(screen.getByRole('banner')).getByText('David Bejar')).toBeInTheDocument()
    expect(within(screen.getByRole('banner')).queryByRole('link', { name: 'Administration' })).not.toBeInTheDocument()
  })

  it('lets a mini admin manage users but not roles', async () => {
    signedInAs(miniadmin)
    renderApp('/admin')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'User administration' }))
    expect(await screen.findByRole('heading', { name: 'User administration' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '← Administration' }))
    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Role administration' })).not.toBeInTheDocument()
  })

  it('still refuses a mini admin who opens role administration by its address', async () => {
    signedInAs(miniadmin)
    renderApp('/admin/roles')

    expect(await screen.findByRole('heading', { name: 'Not enough permissions' })).toBeInTheDocument()
    expect(mockedRoles.list).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('sends visitors who are not signed in to the login page', async () => {
    mockedAuth.me.mockRejectedValue(new ApiError(401, 'Unauthorized'))
    mockedAuth.anonymous.mockResolvedValue({ role: 'ANONYMOUS', roleDisplayName: 'Not logged', screens: ['LOGIN'] })
    const router = renderApp('/admin/users')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('after login, skips a remembered screen the new user may not open', async () => {
    mockedAuth.me.mockRejectedValue(new ApiError(401, 'Unauthorized'))
    mockedAuth.anonymous.mockResolvedValue({ role: 'ANONYMOUS', roleDisplayName: 'Not logged', screens: ['LOGIN'] })
    mockedAuth.login.mockResolvedValue(david)
    const router = renderApp('/admin/users')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Username'), 'david')
    await user.type(screen.getByLabelText('Password'), 'david')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Todos' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/todos')
  })

  it('starts a signed-in user on the first screen they may open', async () => {
    signedInAs({ ...miniadmin, screens: ['USER_ADMIN'] })
    const router = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'User administration' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/users')
  })
})

describe('user administration', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    signedInAs(admin)
    mockedUsers.list.mockResolvedValue([user('admin', { deletable: false }), user('david', { deletable: false }), user('temp')])
    mockedUsers.roles.mockResolvedValue([{ code: 'ADMIN', displayName: 'Administrator' }, { code: 'USER', displayName: 'User' }])
  })

  it('lists users and only lets deletable ones be deleted', async () => {
    renderApp('/admin/users')

    expect(await screen.findByRole('button', { name: 'Delete temp' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Delete admin' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete david' })).toBeDisabled()
    expect(within(screen.getByRole('banner')).getByText('Administrator')).toBeInTheDocument()
  })

  it('deletes a user', async () => {
    mockedUsers.remove.mockResolvedValue(undefined)
    renderApp('/admin/users')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete temp' }))

    expect(mockedUsers.remove).toHaveBeenCalledWith('id-temp')
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Delete temp' })).not.toBeInTheDocument())
  })

  it('adds a user with a password and role', async () => {
    mockedUsers.create.mockResolvedValue(user('newbie', { role: 'ADMIN', roleDisplayName: 'Administrator' }))
    renderApp('/admin/users')
    const user_ = userEvent.setup()

    await user_.type(await screen.findByLabelText('Username'), ' newbie ')
    await user_.type(screen.getByLabelText('Display name'), 'New Bie')
    await user_.type(screen.getByLabelText('Password'), 'secret')
    expect(screen.getByLabelText('Role')).toHaveValue('USER')
    await user_.selectOptions(screen.getByLabelText('Role'), 'ADMIN')
    await user_.click(screen.getByRole('button', { name: 'Add user' }))

    expect(mockedUsers.create).toHaveBeenCalledWith({ username: 'newbie', displayName: 'New Bie', password: 'secret', role: 'ADMIN' })
    expect(await screen.findByRole('button', { name: 'Delete newbie' })).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('requires a password', async () => {
    renderApp('/admin/users')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Username'), 'newbie')
    await user.type(screen.getByLabelText('Display name'), 'New Bie')
    await user.click(screen.getByRole('button', { name: 'Add user' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter a username, display name, password and role')
    expect(mockedUsers.create).not.toHaveBeenCalled()
  })

  it("shows the backend's reason when a delete is refused", async () => {
    mockedUsers.remove.mockRejectedValue(new ApiError(409, 'You cannot delete yourself'))
    renderApp('/admin/users')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete temp' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('You cannot delete yourself')
    expect(screen.getByRole('button', { name: 'Delete temp' })).toBeInTheDocument()
  })
})

describe('role administration', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    signedInAs(admin)
    mockedRoles.list.mockResolvedValue([adminRole, userRole])
    mockedRoles.screens.mockResolvedValue([
      { code: 'TODO', displayName: 'Todos', parentCode: null },
      { code: 'ADMINISTRATION', displayName: 'Administration', parentCode: null },
      { code: 'USER_ADMIN', displayName: 'User administration', parentCode: 'ADMINISTRATION' },
      { code: 'ROLE_ADMIN', displayName: 'Role administration', parentCode: 'ADMINISTRATION' },
    ])
  })

  it("shows each role's screens and keeps ADMIN's fixed", async () => {
    renderApp('/admin/roles')

    const adminTodos = await screen.findByRole('checkbox', { name: 'Administrator can open Todos' })
    expect(adminTodos).toBeChecked()
    expect(adminTodos).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'User can open Todos' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'User can open Role administration' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'User can open Role administration' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Delete role ADMIN' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Delete role USER' })).toBeEnabled()
  })

  it('does not offer the login screen, and shows which screen each one is reached through', async () => {
    renderApp('/admin/roles')

    expect(await screen.findByRole('checkbox', { name: 'User can open Todos' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Login/ })).not.toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Role administration in Administration' })).toBeInTheDocument()
  })

  it("shows the parent screen the backend gave along with a child", async () => {
    mockedRoles.assign.mockResolvedValue({ ...userRole, screens: ['TODO', 'ADMINISTRATION', 'ROLE_ADMIN'] })
    renderApp('/admin/roles')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: 'User can open Role administration' }))

    expect(mockedRoles.assign).toHaveBeenCalledWith('USER', 'ROLE_ADMIN')
    expect(await screen.findByRole('checkbox', { name: 'User can open Administration' })).toBeChecked()
  })

  it('assigns and unassigns screens', async () => {
    mockedRoles.assign.mockResolvedValue({ ...userRole, screens: ['TODO', 'ADMINISTRATION'] })
    mockedRoles.unassign.mockResolvedValue({ ...userRole, screens: ['ADMINISTRATION'] })
    renderApp('/admin/roles')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: 'User can open Administration' }))
    expect(mockedRoles.assign).toHaveBeenCalledWith('USER', 'ADMINISTRATION')
    expect(await screen.findByRole('checkbox', { name: 'User can open Administration' })).toBeChecked()

    await user.click(screen.getByRole('checkbox', { name: 'User can open Todos' }))
    expect(mockedRoles.unassign).toHaveBeenCalledWith('USER', 'TODO')
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'User can open Todos' })).not.toBeChecked())
  })

  it('adds and deletes a role', async () => {
    mockedRoles.create.mockResolvedValue({ code: 'MINIADMIN', displayName: 'Mini admin', screens: [], lockedScreens: [], deletable: true })
    mockedRoles.remove.mockResolvedValue(undefined)
    renderApp('/admin/roles')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Code'), 'miniadmin')
    await user.type(screen.getByLabelText('Display name'), 'Mini admin')
    await user.click(screen.getByRole('button', { name: 'Add role' }))

    expect(mockedRoles.create).toHaveBeenCalledWith('miniadmin', 'Mini admin')
    await user.click(await screen.findByRole('button', { name: 'Delete role MINIADMIN' }))
    expect(mockedRoles.remove).toHaveBeenCalledWith('MINIADMIN')
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Delete role MINIADMIN' })).not.toBeInTheDocument())
  })

  it("reloads the user's own screens after changing their role", async () => {
    const roleAdmin: CurrentUser = { ...miniadmin, role: 'USER', screens: ['TODO', 'ROLE_ADMIN'] }
    signedInAs(roleAdmin)
    mockedRoles.assign.mockResolvedValue({ ...userRole, screens: ['TODO', 'ADMINISTRATION'] })
    renderApp('/admin/roles')
    const user = userEvent.setup()
    mockedAuth.me.mockResolvedValue({ ...roleAdmin, screens: ['TODO', 'ADMINISTRATION', 'ROLE_ADMIN'] })

    await user.click(await screen.findByRole('checkbox', { name: 'User can open Administration' }))

    expect(await within(screen.getByRole('banner')).findByRole('link', { name: 'Administration' })).toBeInTheDocument()
  })
})
