import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { EmptyState, ErrorState, LoadingState } from './States'

describe('state components', () => {
  it('shows a loading status', () => {
    render(<LoadingState label="Loading missions…" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading missions…')
  })

  it('shows an empty state with guidance', () => {
    render(<EmptyState title="No missions yet" description="Create your first mission." />)
    expect(screen.getByText('No missions yet')).toBeInTheDocument()
    expect(screen.getByText('Create your first mission.')).toBeInTheDocument()
  })

  it('shows the error message and retries on request', async () => {
    const onRetry = vi.fn()
    render(<ErrorState error={new ApiError('network', 'Cannot reach the CamSat backend.')} onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Cannot reach the CamSat backend.')
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
