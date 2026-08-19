import { AlertCircle, CheckCircle2 } from 'lucide-react'

type FeedbackProps = {
  message?: string
  error?: string
}

export function Feedback({ message, error }: FeedbackProps) {
  const content = error || message || ''
  const isError = Boolean(error)

  if (!content) return null

  return (
    <div className={isError ? 'alert error' : 'alert'} role={isError ? 'alert' : 'status'} aria-live={isError ? 'assertive' : 'polite'} aria-atomic="true">
      {isError ? <AlertCircle aria-hidden="true" /> : <CheckCircle2 aria-hidden="true" />}
      <span>{content}</span>
    </div>
  )
}
