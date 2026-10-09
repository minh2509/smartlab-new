import type { ChatConversationType } from '../types'

type ChatAvatarProps = {
  name: string
  type?: ChatConversationType
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

export function ChatAvatar({ name, type = 'DIRECT', size = 'md', className = '' }: ChatAvatarProps) {
  const initials = getInitials(name)
  const isGroup = type === 'GROUP'

  const sizeClass =
    size === 'sm' ? 'chat-avatar-sm' : size === 'lg' ? 'chat-avatar-lg' : 'chat-avatar-md'

  return (
    <div
      className={`chat-avatar-wrap ${isGroup ? 'chat-avatar-group' : ''} ${sizeClass} ${className}`.trim()}
      aria-hidden="true"
    >
      {initials}
    </div>
  )
}

function getInitials(name: string): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}
