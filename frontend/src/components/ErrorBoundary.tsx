import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from './ui/Button'

interface Props {
  children: ReactNode
  /** Smaller inline fallback for optional widgets such as the 3D scene. */
  inline?: boolean
  label?: string
}
interface State {
  failed: boolean
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Only safe diagnostics: no tokens, no request bodies.
    console.error('UI error:', error.name, error.message, info.componentStack?.split('\n').slice(0, 3).join(' '))
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className={`flex flex-col items-center gap-3 text-center ${this.props.inline ? 'p-6' : 'min-h-screen justify-center p-8'}`}>
        <p className="text-sm font-medium">{this.props.label ?? 'Something went wrong while rendering this view.'}</p>
        <Button onClick={() => this.setState({ failed: false })}>Try again</Button>
        {!this.props.inline && (
          <Button variant="ghost" onClick={() => window.location.assign('/')}>
            Back to overview
          </Button>
        )}
      </div>
    )
  }
}
