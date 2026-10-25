import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TodosPage } from './TodosPage.tsx'
import { NETWORK_ERROR_MESSAGE } from '../../lib/http.ts'
import { TITLE_BLANK_MESSAGE, TITLE_MAX_LENGTH, TITLE_TOO_LONG_MESSAGE, Todo, todosApi } from './api.ts'

// Mock only the network calls; keep the real constants and validateTitle.
vi.mock('./api.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api.ts')>()),
  todosApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

const mockedApi = vi.mocked(todosApi)

function todo(overrides: Partial<Todo> = {}): Todo {
  return { id: '1', title: 'Buy milk', completed: false, createdAt: '2026-01-01T00:00:00Z', ...overrides }
}

describe('TodosPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockedApi.list.mockResolvedValue([])
  })

  it('renders todos loaded from the API', async () => {
    mockedApi.list.mockResolvedValue([todo(), todo({ id: '2', title: 'Walk dog' })])

    render(<TodosPage />)

    expect(await screen.findByText('Buy milk')).toBeInTheDocument()
    expect(screen.getByText('Walk dog')).toBeInTheDocument()
  })

  it('adds a todo and clears the input', async () => {
    mockedApi.create.mockResolvedValue(todo({ title: 'New task' }))
    const user = userEvent.setup()
    render(<TodosPage />)

    const input = screen.getByPlaceholderText('What needs doing?')
    await user.type(input, '  New task  ')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(mockedApi.create).toHaveBeenCalledWith('New task')
    expect(await screen.findByText('New task')).toBeInTheDocument()
    expect(input).toHaveValue('')
  })

  it('does not submit a blank title and says why', async () => {
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.type(screen.getByPlaceholderText('What needs doing?'), '   ')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(mockedApi.create).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(TITLE_BLANK_MESSAGE)
  })

  it('limits the input to the max title length', () => {
    render(<TodosPage />)

    expect(screen.getByPlaceholderText('What needs doing?')).toHaveAttribute('maxLength', String(TITLE_MAX_LENGTH))
  })

  it('shows a clear message when the server is unreachable', async () => {
    mockedApi.create.mockRejectedValue(new Error(NETWORK_ERROR_MESSAGE))
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.type(screen.getByPlaceholderText('What needs doing?'), 'Task')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
    expect(screen.getByPlaceholderText('What needs doing?')).toHaveValue('Task')
  })

  it('shows the backend validation message', async () => {
    mockedApi.create.mockRejectedValue(new Error(TITLE_TOO_LONG_MESSAGE))
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.type(screen.getByPlaceholderText('What needs doing?'), 'Task')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(TITLE_TOO_LONG_MESSAGE)
  })

  it('clears the error after a successful action', async () => {
    mockedApi.create.mockRejectedValueOnce(new Error(NETWORK_ERROR_MESSAGE)).mockResolvedValueOnce(todo({ title: 'Task' }))
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.type(screen.getByPlaceholderText('What needs doing?'), 'Task')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Task')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows an error when toggling fails', async () => {
    mockedApi.list.mockResolvedValue([todo()])
    mockedApi.update.mockRejectedValue(new Error(NETWORK_ERROR_MESSAGE))
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.click(await screen.findByRole('checkbox'))

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
    expect(screen.getByRole('checkbox')).not.toBeChecked()
  })

  it('toggles completion', async () => {
    mockedApi.list.mockResolvedValue([todo()])
    mockedApi.update.mockResolvedValue(todo({ completed: true }))
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.click(await screen.findByRole('checkbox'))

    expect(mockedApi.update).toHaveBeenCalledWith('1', { completed: true })
    expect(screen.getByRole('checkbox')).toBeChecked()
  })

  it('deletes a todo', async () => {
    mockedApi.list.mockResolvedValue([todo()])
    mockedApi.remove.mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<TodosPage />)

    await user.click(await screen.findByRole('button', { name: 'Delete Buy milk' }))

    expect(mockedApi.remove).toHaveBeenCalledWith('1')
    expect(screen.queryByText('Buy milk')).not.toBeInTheDocument()
  })

  it('shows an error when loading fails', async () => {
    mockedApi.list.mockRejectedValue(new Error('500 Internal Server Error'))

    render(<TodosPage />)

    expect(await screen.findByText(/500 Internal Server Error/)).toBeInTheDocument()
  })
})
