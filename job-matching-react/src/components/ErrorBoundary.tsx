import { Component, type ErrorInfo, type ReactNode } from 'react'

// Without this, an unexpected error while rendering any component unmounts the whole app and
// leaves a blank page. This keeps a way out on screen instead.
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="container container-form">
        <div className="header">
          <h1>Something went wrong</h1>
          <p>This page hit an unexpected error.</p>
        </div>
        <div className="content text-center">
          <button type="button" className="btn btn-primary" onClick={() => window.location.assign('/')}>
            Reload the app
          </button>
        </div>
      </div>
    )
  }
}
