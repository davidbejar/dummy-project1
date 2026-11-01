import { useSyncExternalStore } from 'react'
import { chooseTheme, currentTheme, subscribeToTheme, ThemeChoice } from '../lib/theme.ts'

const OPTIONS: { value: ThemeChoice; label: string }[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

/** Picks the light, dark or system theme, remembering the choice in this browser. */
export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeToTheme, currentTheme)

  return (
    <label className="theme-toggle">
      <span className="visually-hidden">Theme</span>
      <select value={theme} onChange={(event) => chooseTheme(event.target.value as ThemeChoice)}>
        {OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </label>
  )
}
