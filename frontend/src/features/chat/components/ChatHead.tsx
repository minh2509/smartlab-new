import { type MouseEvent } from 'react'
import { X } from 'lucide-react'
import { useChat } from '../chatContext'
import type { ChatDockItem } from '../types'

type ChatHeadProps = {
  item: ChatDockItem
}

export function ChatHead({ item }: ChatHeadProps) {
  const { conversationId } = item
  const { conversations, openConversation, removeDockItem } = useChat()

  const conversation = conversations.find((c) => c.conversationId === conversationId)
  const name = conversation?.displayName || conversation?.title || 'Chat'
  const unreadCount = conversation?.unreadCount ?? 0
  const initial = name.substring(0, 1).toUpperCase()

  const handleClick = () => {
    openConversation(conversationId)
  }

  const handleRemove = (e: MouseEvent) => {
    e.stopPropagation()
    removeDockItem(conversationId)
  }

  return (
    <div className="chat-dock-head-wrap" data-dock-conversation-id={conversationId}>
      <button
        type="button"
        className="chat-dock-head-btn"
        onClick={handleClick}
        title={unreadCount > 0 ? `${name} (${unreadCount} tin nhắn mới)` : name}
        aria-label={`Mở chat với ${name}${unreadCount > 0 ? `, ${unreadCount} tin nhắn mới` : ''}`}
      >
        <span>{initial}</span>

        {unreadCount > 0 && (
          <span className="chat-dock-unread" aria-hidden="true">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      <button
        type="button"
        className="chat-dock-remove-btn"
        onClick={handleRemove}
        title={`Xóa lối tắt ${name}`}
        aria-label={`Xóa lối tắt chat ${name}`}
      >
        <X size={12} aria-hidden="true" />
      </button>
    </div>
  )
}
