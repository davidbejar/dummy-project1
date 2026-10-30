// Adds DOM matchers such as toBeInTheDocument() to Vitest's expect.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  clearCookies()
  clearTheme()
})

function clearCookies() {
  if (typeof document === 'undefined') return
  for (const cookie of document.cookie.split('; ').filter(Boolean)) {
    document.cookie = `${cookie.split('=')[0]}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

function clearTheme() {
  if (typeof document === 'undefined') return
  localStorage.clear()
  delete document.documentElement.dataset.theme
}
