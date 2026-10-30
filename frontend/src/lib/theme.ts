export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'theme'

/** The theme the user chose in this browser, or else the system's. */
export function initialTheme(): Theme {
  return storedTheme() ?? (globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
}

/** Switches the page's colors; index.css maps data-theme to color-scheme. */
export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
}

export function saveTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // storage can be blocked; the theme then lasts until the page reloads
  }
}

function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}
