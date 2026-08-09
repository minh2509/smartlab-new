import { DatabaseZap } from 'lucide-react'

type ApiPendingBlockProps = {
  title: string
  description: string
}

export function ApiPendingBlock({ title, description }: ApiPendingBlockProps) {
  return (
    <div className="empty public-empty">
      <DatabaseZap aria-hidden="true" />
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  )
}
