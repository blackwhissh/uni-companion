import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App.tsx'

describe('App', () => {
  it('shows the product name', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Uni Companion' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'From the lectures' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Practice' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Classmates' })).toBeInTheDocument()
  })
})
