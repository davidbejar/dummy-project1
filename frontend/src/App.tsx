import { FormEvent, useEffect, useState } from 'react'
import { api, TITLE_MAX_LENGTH, Todo, validateTitle } from './api.ts'

function message(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

export default function App() {
  const [todos, setTodos] = useState<Todo[]>([])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.list().then(setTodos).catch((e: unknown) => setError(message(e)))
  }, [])

  // Runs an API action, showing its error or clearing a previous one on success.
  async function run(action: () => Promise<void>) {
    try {
      await action()
      setError(null)
    } catch (e) {
      setError(message(e))
    }
  }

  function add(e: FormEvent) {
    e.preventDefault()
    const invalid = validateTitle(title)
    if (invalid) {
      setError(invalid)
      return
    }
    return run(async () => {
      const todo = await api.create(title.trim())
      setTodos((ts) => [...ts, todo])
      setTitle('')
    })
  }

  function toggle(todo: Todo) {
    return run(async () => {
      const updated = await api.update(todo.id, { completed: !todo.completed })
      setTodos((ts) => ts.map((t) => (t.id === updated.id ? updated : t)))
    })
  }

  function remove(id: string) {
    return run(async () => {
      await api.remove(id)
      setTodos((ts) => ts.filter((t) => t.id !== id))
    })
  }

  return (
    <main>
      <h1>Todos</h1>
      {error && <p className="error" role="alert">{error}</p>}
      <form onSubmit={add}>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="What needs doing?"
          aria-label="New todo title"
          maxLength={TITLE_MAX_LENGTH}
        />
        <button type="submit">Add</button>
      </form>
      <ul>
        {todos.map((t) => (
          <li key={t.id} className={t.completed ? 'done' : ''}>
            <label>
              <input type="checkbox" checked={t.completed} onChange={() => toggle(t)} />
              {t.title}
            </label>
            <button type="button" onClick={() => remove(t.id)} aria-label={`Delete ${t.title}`}>×</button>
          </li>
        ))}
      </ul>
    </main>
  )
}
