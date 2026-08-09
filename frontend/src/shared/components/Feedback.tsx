import { AlertCircle, CheckCircle2 } from 'lucide-react'

type FeedbackProps = {
  message?: string
  error?: string
}

export function Feedback({ message, error }: FeedbackProps) {
  if (!message && !error) return null

  return (
    <div className={error ? 'alert error' : 'alert'}>
      {error ? <AlertCircle aria-hidden="true" /> : <CheckCircle2 aria-hidden="true" />}
      <span>{error || message}</span>
    </div>
  )
}
