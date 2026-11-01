/** 'system' follows the operating system's setting, live; 'light' and 'dark' override it. */
export type ThemeChoice = 'light' | 'dark' | 'system'

// public/theme-init.js reads the same key before first paint; keep the two in step.
const STORAGE_KEY = 'theme'
const CHANGE_EVENT = 'themechange'

/**
 * The theme on the page now. The page's data-theme attribute is the single source:
 * public/theme-init.js sets it from storage before first paint, and without it
 * index.css follows the system.
 */
export function currentTheme(): ThemeChoice {
  return parse(document.documentElement.dataset.theme)
}

/** Applies a choice, remembers it in this browser, and tells subscribers in this tab. */
export function chooseTheme(choice: ThemeChoice) {
  save(choice)
  apply(choice)
  globalThis.dispatchEvent(new Event(CHANGE_EVENT))
}

/** Calls `onChange` when the theme changes in this tab or in another tab of this browser. */
export function subscribeToTheme(onChange: () => void): () => void {
  function onStorage(event: StorageEvent) {
    // key is null when another tab cleared storage, which resets to the system theme
    if (event.key !== STORAGE_KEY && event.key !== null) return
    apply(parse(event.key === null ? null : event.newValue))
    onChange()
  }
  globalThis.addEventListener(CHANGE_EVENT, onChange)
  globalThis.addEventListener('storage', onStorage)
  return () => {
    globalThis.removeEventListener(CHANGE_EVENT, onChange)
    globalThis.removeEventListener('storage', onStorage)
  }
}

function apply(choice: ThemeChoice) {
  if (choice === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = choice
}

function save(choice: ThemeChoice) {
  try {
    if (choice === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    // storage can be blocked; the choice then lasts until the page reloads
  }
}

function parse(value: string | null | undefined): ThemeChoice {
  return value === 'light' || value === 'dark' ? value : 'system'
}
