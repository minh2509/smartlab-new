import { useEffect, useRef, type MouseEvent } from 'react'
import { ExternalLink, Minus, Square, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useChat } from '../chatContext'
import type { OpenPopup } from '../types'
import { MessageComposer } from './MessageComposer'
import { MessageList } from './MessageList'

type ChatPopupWindowProps = {
  popup: OpenPopup
  isActive: boolean
}

export function ChatPopupWindow({ popup, isActive }: ChatPopupWindowProps) {
  const { conversationId, minimized } = popup
  const { conversations, closePopup, minimizePopup, focusPopup } = useChat()
  const navigate = useNavigate()
  const windowRef = useRef<HTMLDivElement>(null)

  const conversation = conversations.find((c) => c.conversationId === conversationId)
  const title = conversation?.displayName || conversation?.title || 'Đoạn chat'

  const handleHeaderClick = () => {
    if (minimized) {
      minimizePopup(conversationId)
    }
    focusPopup(conversationId)
  }

  const handleExpand = (e: MouseEvent) => {
    e.stopPropagation()
    closePopup(conversationId)
    navigate(`/chat?id=${encodeURIComponent(conversationId)}`)
  }

  const handleMinimize = (e: MouseEvent) => {
    e.stopPropagation()
    minimizePopup(conversationId)
  }

  const handleClose = (e: MouseEvent) => {
    e.stopPropagation()
    closePopup(conversationId)
  }

  // Handle Escape key to close popup when focused inside
  useEffect(() => {
    const el = windowRef.current
    if (!el) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        closePopup(conversationId)
      }
    }
    el.addEventListener('keydown', handleKeyDown)
    return () => el.removeEventListener('keydown', handleKeyDown)
  }, [closePopup, conversationId])

  return (
    <div
      ref={windowRef}
      className={`chat-popup-window ${minimized ? 'is-minimized' : 'is-open'} ${
        isActive ? 'is-focused' : ''
      }`}
      onClick={() => focusPopup(conversationId)}
      role="region"
      aria-label={`Cửa sổ chat ${title}`}
      data-conversation-id={conversationId}
    >
      <header
        className="chat-popup-header"
        onClick={handleHeaderClick}
        title={minimized ? 'Nhấn để mở rộng' : 'Nhấn để kích hoạt'}
      >
        <div className="chat-popup-title-area">
          <div className="chat-popup-avatar-small" aria-hidden="true">
            {title.substring(0, 1).toUpperCase()}
          </div>
          <span className="chat-popup-title">{title}</span>
        </div>

        <div className="chat-popup-controls">
          <button
            type="button"
            className="chat-popup-ctrl-btn"
            title="Mở toàn màn hình"
            aria-label="Mở trang đầy đủ"
            onClick={handleExpand}
          >
            <ExternalLink size={13} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="chat-popup-ctrl-btn"
            title={minimized ? 'Mở lại' : 'Thu nhỏ'}
            aria-label={minimized ? 'Mở lại cửa sổ' : 'Thu nhỏ cửa sổ'}
            onClick={handleMinimize}
          >
            {minimized ? <Square size={12} aria-hidden="true" /> : <Minus size={13} aria-hidden="true" />}
          </button>
          <button
            type="button"
            className="chat-popup-ctrl-btn"
            title="Đóng chat"
            aria-label="Đóng cửa sổ chat"
            onClick={handleClose}
          >
            <X size={14} aria-hidden="true" />
          </button>
        </div>
      </header>

      {!minimized && (
        <div className="chat-popup-body">
          <MessageList conversationId={conversationId} />
          <MessageComposer conversationId={conversationId} />
        </div>
      )}
    </div>
  )
}
