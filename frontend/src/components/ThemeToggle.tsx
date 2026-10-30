import { useState } from 'react'
import { applyTheme, initialTheme, saveTheme, Theme } from '../lib/theme.ts'

/** Switches between the light and dark theme, remembering the choice in this browser. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(initialTheme)
  const next: Theme = theme === 'dark' ? 'light' : 'dark'

  function toggle() {
    applyTheme(next)
    saveTheme(next)
    setTheme(next)
  }

  return (
    <button type="button" className="theme-toggle" onClick={toggle} aria-label={`Switch to ${next} theme`} title={`Switch to ${next} theme`}>
      {next === 'dark' ? '☾' : '☀'}
    </button>
  )
}
