import { describe, expect, it } from 'vitest'
import { homePathFor } from './home-path.ts'

describe('homePathFor', () => {
  it('sends a course admin to the admin console', () => {
    expect(homePathFor(['COURSE_ADMIN'])).toBe('/admin/courses')
  })

  it('sends a student to their courses', () => {
    expect(homePathFor(['STUDENT'])).toBe('/courses')
  })
})
