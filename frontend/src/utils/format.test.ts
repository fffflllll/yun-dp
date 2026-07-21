import { describe, expect, it } from 'vitest'
import { splitTags, statusLabel } from './format'

describe('MeetMate format helpers', () => {
  it('normalizes user-entered tags', () => {
    expect(splitTags('川菜，日料 川菜')).toEqual(['川菜', '日料'])
  })

  it('provides a room status label', () => {
    expect(statusLabel('READY_TO_PLAN')).toBe('可以规划')
  })
})
