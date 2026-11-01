import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// Paths from the frontend root, where Vitest runs: jsdom makes import.meta.url an http URL.
const script = readFileSync(resolve('public/theme-init.js'), 'utf8')
const indexHtml = readFileSync(resolve('index.html'), 'utf8')

function runThemeInit() {
  new Function(script)()
}

describe('public/theme-init.js', () => {
  it('applies a stored choice to the page', () => {
    localStorage.setItem('theme', 'dark')
    runThemeInit()
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('leaves the page following the system when nothing valid is stored', () => {
    runThemeInit()
    expect(document.documentElement.dataset.theme).toBeUndefined()

    localStorage.setItem('theme', 'purple')
    runThemeInit()
    expect(document.documentElement.dataset.theme).toBeUndefined()
  })

  it('is loaded as a blocking script in <head>, before the app', () => {
    const head = indexHtml.slice(0, indexHtml.indexOf('</head>'))
    expect(head).toContain('<script src="/theme-init.js"></script>')
  })
})
