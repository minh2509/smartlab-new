import { useEffect, useRef, useState } from 'react'
import { MessageSquare } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { useChat } from '../chatContext'
import { ConversationPopover } from './ConversationPopover'

export function ChatHeaderButton() {
  const { totalUnreadCount } = useChat()
  const [isOpen, setIsOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const location = useLocation()

  // Close popover when route changes
  useEffect(() => {
    setIsOpen(false)
  }, [location.pathname])

  // Click outside to close
  useEffect(() => {
    if (!isOpen) return

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [isOpen])

  return (
    <div className="notification-popover" ref={rootRef}>
      <button
        className={`icon-btn notification-popover-trigger chat-header-btn${isOpen ? ' is-active' : ''}`}
        type="button"
        aria-label={
          totalUnreadCount > 0
            ? `Đoạn chat, ${totalUnreadCount} tin nhắn chưa đọc`
            : 'Đoạn chat'
        }
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-controls="chat-popover-panel"
        title="Đoạn chat"
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <MessageSquare aria-hidden="true" />
        {totalUnreadCount > 0 && (
          <span className="notification-count" aria-hidden="true">
            {totalUnreadCount > 99 ? '99+' : totalUnreadCount}
          </span>
        )}
      </button>

      <ConversationPopover isOpen={isOpen} onClose={() => setIsOpen(false)} />
    </div>
  )
}
