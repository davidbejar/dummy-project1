// Applies the stored theme from <head>, before the page is painted, so a stored choice
// never flashes the other theme first. It is a file rather than an inline script because
// the Content-Security-Policy allows only 'self'. Keep the key and values in step with
// src/lib/theme.ts; with nothing stored, index.css follows the system setting.
try {
  const theme = localStorage.getItem('theme')
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
} catch {
  // storage blocked: follow the system setting
}
