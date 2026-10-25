import { useCallback, useState } from 'react'

/** A message to show for anything thrown, e.g. an ApiError. */
export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

/** Runs API actions for a page, showing the last action's error or clearing it on success. */
export function useApiAction() {
  const [error, setError] = useState<string | null>(null)

  const run = useCallback(async (action: () => Promise<void>) => {
    try {
      await action()
      setError(null)
    } catch (e) {
      setError(errorText(e))
    }
  }, [])

  return { error, setError, run }
}
