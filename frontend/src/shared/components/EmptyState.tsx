import { Inbox } from 'lucide-react'

export function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="empty tight">
      <Inbox aria-hidden="true" />
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  )
}
