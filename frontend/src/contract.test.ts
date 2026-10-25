// @vitest-environment node
/**
 * Contract test: the title rules in the frontend, the Java backend and the CockroachDB
 * schema must agree. Reads the backend sources directly so that changing the limit in
 * one place without the others fails the build.
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'
import { TITLE_BLANK_MESSAGE, TITLE_MAX_LENGTH, TITLE_TOO_LONG_MESSAGE } from './api.ts'

// Tests run from frontend/, so the backend sources are one level up.
const read = (path: string) => readFileSync(resolve(cwd(), '../backend/src/main', path), 'utf8')

describe('frontend/backend/database contract', () => {
  it('uses the same max title length as the Java entity', () => {
    const java = read('java/com/example/backend/todo/Todo.java')
    expect(java).toMatch(new RegExp(`TITLE_MAX_LENGTH = ${TITLE_MAX_LENGTH};`))
  })

  it('uses the same max title length as the database column', () => {
    const sql = read('resources/db/migration/V1__create_todo.sql')
    expect(sql).toMatch(new RegExp(`title\\s+VARCHAR\\(${TITLE_MAX_LENGTH}\\)`))
  })

  it('uses the same validation messages as the backend', () => {
    const java = read('java/com/example/backend/todo/TodoController.java')
    expect(java).toContain(`"${TITLE_BLANK_MESSAGE}"`)
    expect(TITLE_TOO_LONG_MESSAGE).toBe(`Title must be at most ${TITLE_MAX_LENGTH} characters`)
    expect(java).toContain('"Title must be at most " + Todo.TITLE_MAX_LENGTH + " characters"')
  })
})
