import { describe, expect, it } from 'vitest'
import { identityApiUrl, learningApiUrl } from './config.ts'

describe('api config', () => {
  it('points at the local identity and learning services', () => {
    expect(identityApiUrl).toBe('http://localhost:8081')
    expect(learningApiUrl).toBe('http://localhost:8082')
  })
})
