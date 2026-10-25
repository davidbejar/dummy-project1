// @vitest-environment node
/** Contract test: the frontend's screen codes must be rows of app_screen in the backend. */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'
import { canOpenPath, homePath, Screen } from './screens.ts'

describe('screens', () => {
  it('match the screens created by the backend migration', () => {
    const sql = readFileSync(resolve(cwd(), '../backend/src/main/resources/db/migration/V5__create_app_screen.sql'), 'utf8')
    const inserted = [...sql.matchAll(/^\s+\('([A-Z_]+)', '[^']+', \d+, (?:NULL|'[^']+')\)/gm)].map((m) => m[1])

    expect(inserted.sort()).toEqual(Object.values(Screen).sort())
  })

  it('start signed-in users on the first screen they may open', () => {
    expect(homePath(['TODO', 'ADMINISTRATION'])).toBe('/todos')
    expect(homePath(['USER_ADMIN'])).toBe('/admin/users')
    expect(homePath([])).toBe('/todos')
  })

  it('tell whether a path is a screen the user may open', () => {
    expect(canOpenPath('/admin/users', ['USER_ADMIN'])).toBe(true)
    expect(canOpenPath('/admin/roles', ['USER_ADMIN'])).toBe(false)
    expect(canOpenPath('/nowhere', ['USER_ADMIN'])).toBe(false)
  })
})
