import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { AlertCircle, ArrowDown, FileText } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { useChat } from '../chatContext'
import type { ChatMessageItem } from '../types'

type MessageListProps = {
  conversationId: string
}

export function MessageList({ conversationId }: MessageListProps) {
  const { profile, token } = useAuth()
  const {
    messagesByConversation,
    typingUsers,
    hasMoreOlderMessages,
    isLoadingOlder,
    loadOlderMessages,
    retrySendMessage,
  } = useChat()

  const containerRef = useRef<HTMLDivElement>(null)
  const prevScrollHeightRef = useRef<number>(0)
  const prevScrollTopRef = useRef<number>(0)
  const isNearBottomRef = useRef(true)

  const [unseenCount, setUnseenCount] = useState(0)
  const lastMessageCountRef = useRef(0)

  const messages: ChatMessageItem[] = messagesByConversation[conversationId] ?? []
  const conversationTyping = typingUsers[conversationId] ?? []
  const hasMore = hasMoreOlderMessages(conversationId)
  const loadingOlder = isLoadingOlder(conversationId)

  // Handle scroll events
  const handleScroll = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    const near = distanceFromBottom < 70
    isNearBottomRef.current = near
    if (near) {
      setUnseenCount(0)
    }
  }, [])

  // Preserve scroll position when older messages are loaded
  useLayoutEffect(() => {
    const el = containerRef.current
    if (!el) return

    if (prevScrollHeightRef.current > 0) {
      const addedHeight = el.scrollHeight - prevScrollHeightRef.current
      if (addedHeight > 0) {
        el.scrollTop = prevScrollTopRef.current + addedHeight
      }
      prevScrollHeightRef.current = 0
      prevScrollTopRef.current = 0
    }
  }, [messages.length])

  // Handle auto-scroll on new messages or show affordance
  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const diff = messages.length - lastMessageCountRef.current
    lastMessageCountRef.current = messages.length

    if (diff > 0) {
      if (isNearBottomRef.current) {
        el.scrollTop = el.scrollHeight
        setUnseenCount(0)
      } else {
        setUnseenCount((c) => c + diff)
      }
    } else {
      // First load: scroll to bottom
      el.scrollTop = el.scrollHeight
    }
  }, [messages.length])

  const scrollToBottom = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
    isNearBottomRef.current = true
    setUnseenCount(0)
  }, [])

  const handleLoadOlder = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    prevScrollHeightRef.current = el.scrollHeight
    prevScrollTopRef.current = el.scrollTop
    void loadOlderMessages(conversationId)
  }, [conversationId, loadOlderMessages])

  return (
    <div
      ref={containerRef}
      className="chat-message-list"
      onScroll={handleScroll}
      role="log"
      aria-label="Danh sách tin nhắn"
      aria-live="polite"
    >
      {hasMore && (
        <button
          className="chat-load-older-btn"
          type="button"
          disabled={loadingOlder}
          onClick={handleLoadOlder}
        >
          {loadingOlder ? 'Đang tải tin nhắn cũ...' : 'Tải thêm tin nhắn cũ'}
        </button>
      )}

      {messages.length === 0 && !hasMore && (
        <div className="chat-empty-thread">
          <p>Chưa có tin nhắn nào. Bắt đầu cuộc trò chuyện!</p>
        </div>
      )}

      {messages.map((msg) => {
        const isOwn = profile ? msg.sender.userId === profile.userId : false
        const isDeleted = Boolean(msg.deletedAt)

        return (
          <div
            key={msg.id || msg.clientMessageId}
            className={`chat-msg-row ${isOwn ? 'is-own' : 'is-other'}`}
          >
            {!isOwn && (
              <span className="chat-msg-sender-name">{msg.sender.name}</span>
            )}

            <div className="chat-bubble">
              {isDeleted ? (
                <em style={{ opacity: 0.7 }}>Tin nhắn đã bị thu hồi</em>
              ) : (
                <>
                  {msg.content && <p>{msg.content}</p>}
                  {msg.files && msg.files.length > 0 && (
                    <div className="chat-bubble-files">
                      {msg.files.map((file) => (
                        <a
                          key={file.fileId}
                          href={getFileDownloadUrl(token, file.fileId)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="chat-file-chip"
                          download={file.originalName}
                        >
                          <FileText size={16} aria-hidden="true" />
                          <span>{file.originalName}</span>
                        </a>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="chat-msg-meta">
              <time dateTime={msg.createdAt}>{formatTime(msg.createdAt)}</time>

              {isOwn && msg.status === 'SENDING' && (
                <span className="chat-msg-sending">Đang gửi...</span>
              )}

              {isOwn && msg.status === 'FAILED' && (
                <span className="chat-msg-failed">
                  <AlertCircle size={12} aria-hidden="true" />
                  <span>Gửi thất bại.</span>
                  <button
                    type="button"
                    className="chat-msg-retry-btn"
                    onClick={() => void retrySendMessage(conversationId, msg.clientMessageId)}
                  >
                    Thử lại
                  </button>
                </span>
              )}
            </div>
          </div>
        )
      })}

      {conversationTyping.length > 0 && (
        <div className="chat-typing-indicator" aria-live="polite">
          <span>{conversationTyping.map((u) => u.name).join(', ')} đang nhập</span>
          <span className="chat-typing-dots" aria-hidden="true">
            <span />
            <span />
            <span />
          </span>
        </div>
      )}

      {unseenCount > 0 && (
        <button
          className="chat-new-messages-pill"
          type="button"
          onClick={scrollToBottom}
          aria-label="Cuộn xuống tin nhắn mới nhất"
        >
          <ArrowDown size={14} aria-hidden="true" />
          <span>{unseenCount} tin nhắn mới</span>
        </button>
      )}
    </div>
  )
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    return new Intl.DateTimeFormat('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  } catch {
    return ''
  }
}

function getFileDownloadUrl(token: string | null, fileId: number): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  return `${base}/files/${fileId}${token ? `?token=${encodeURIComponent(token)}` : ''}`
}
