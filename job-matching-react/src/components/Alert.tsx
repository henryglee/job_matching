import type { ReactNode } from 'react'

interface AlertProps {
  type: 'error' | 'success' | 'info'
  children: ReactNode
}

export function Alert({ type, children }: AlertProps) {
  return <div className={`alert alert-${type}`}>{children}</div>
}
