import { useEffect, useRef, useState } from 'react'
import { ArrowRight, MessageSquarePlus, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { searchLabMembers } from '../api'
import { useChat } from '../chatContext'
import type { ChatConversationResponse } from '../types'
import { ChatAvatar } from './ChatAvatar'

type ConversationPopoverProps = {
  isOpen: boolean
  onClose: () => void
}

export function ConversationPopover({ isOpen, onClose }: ConversationPopoverProps) {
  const { token, profile } = useAuth()
  const { conversations, loadingConversations, openConversation, createDirectChat, createGroupChat } = useChat()

  const [searchQuery, setSearchQuery] = useState('')
  const [showNewModal, setShowNewModal] = useState(false)
  const [isGroupMode, setIsGroupMode] = useState(false)
  const [groupTitle, setGroupTitle] = useState('')
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([])
  const [memberCandidates, setMemberCandidates] = useState<{ userId: string; name: string }[]>([])
  const [loadingMembers, setLoadingMembers] = useState(false)
  const [creating, setCreating] = useState(false)

  const panelRef = useRef<HTMLDivElement>(null)

  // Handle Escape key to close popover
  useEffect(() => {
    if (!isOpen) return
    const handleKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  // Filter conversations by query
  const filteredConversations = conversations.filter((c) => {
    const q = searchQuery.toLowerCase().trim()
    if (!q) return true
    const titleMatch = (c.title ?? '').toLowerCase().includes(q)
    const displayMatch = (c.displayName ?? '').toLowerCase().includes(q)
    const memberMatch = c.members.some((m) => m.name.toLowerCase().includes(q))
    return titleMatch || displayMatch || memberMatch
  })

  // Load members when opening new chat modal
  const handleOpenNewModal = async () => {
    setShowNewModal(true)
    setIsGroupMode(false)
    setGroupTitle('')
    setSelectedUserIds([])
    if (token) {
      setLoadingMembers(true)
      try {
        const list = await searchLabMembers(token)
        const candidates = list
          .filter((m) => m.userId !== profile?.userId)
          .map((m) => ({ userId: m.userId, name: m.name }))
        setMemberCandidates(candidates)
      } catch (err) {
        console.error('Failed to load members:', err)
      } finally {
        setLoadingMembers(false)
      }
    }
  }

  const handleSelectConversation = (conv: ChatConversationResponse) => {
    openConversation(conv.conversationId)
    onClose()
  }

  const handleStartDirect = async (targetUserId: string) => {
    if (creating) return
    setCreating(true)
    try {
      const conv = await createDirectChat(targetUserId)
      setShowNewModal(false)
      openConversation(conv.conversationId)
      onClose()
    } catch (err) {
      console.error('Failed to create direct chat:', err)
    } finally {
      setCreating(false)
    }
  }

  const handleCreateGroup = async () => {
    if (creating || !groupTitle.trim() || selectedUserIds.length === 0) return
    setCreating(true)
    try {
      const conv = await createGroupChat({
        title: groupTitle.trim(),
        memberUserIds: selectedUserIds,
      })
      setShowNewModal(false)
      openConversation(conv.conversationId)
      onClose()
    } catch (err) {
      console.error('Failed to create group chat:', err)
    } finally {
      setCreating(false)
    }
  }

  const toggleSelectMember = (userId: string) => {
    setSelectedUserIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId],
    )
  }

  if (!isOpen) return null

  return (
    <section
      ref={panelRef}
      className="chat-popover-panel"
      id="chat-popover-panel"
      role="dialog"
      aria-label="Đoạn chat"
    >
      <header className="chat-popover-head">
        <div className="chat-popover-title-row">
          <h2>Đoạn chat</h2>
        </div>
        <div className="chat-popover-actions">
          <button
            type="button"
            className="chat-icon-action-btn"
            title="Đoạn chat mới"
            aria-label="Tạo đoạn chat mới"
            onClick={handleOpenNewModal}
          >
            <MessageSquarePlus size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="chat-icon-action-btn"
            title="Đóng bảng chat"
            aria-label="Đóng bảng chat"
            onClick={onClose}
          >
            <X size={15} aria-hidden="true" />
          </button>
        </div>
      </header>

      {showNewModal ? (
        <div style={{ padding: '14px', flex: 1, overflowY: 'auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <strong style={{ fontSize: 14 }}>
              {isGroupMode ? 'Tạo nhóm chat mới' : 'Bắt đầu chat 1:1'}
            </strong>
            <button
              type="button"
              className="chat-see-all-link"
              onClick={() => setIsGroupMode(!isGroupMode)}
            >
              {isGroupMode ? 'Chuyển sang 1:1' : 'Tạo nhóm nhiều người'}
            </button>
          </div>

          {isGroupMode && (
            <div style={{ marginBottom: 12 }}>
              <input
                className="chat-search-input"
                style={{ paddingLeft: 10 }}
                placeholder="Tên nhóm chat..."
                value={groupTitle}
                onChange={(e) => setGroupTitle(e.target.value)}
              />
            </div>
          )}

          {loadingMembers ? (
            <p style={{ textAlign: 'center', color: 'var(--text-3)', fontSize: 13 }}>Đang tải thành viên...</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {memberCandidates.map((m) => {
                const isSelected = selectedUserIds.includes(m.userId)
                return (
                  <button
                    key={m.userId}
                    type="button"
                    className="chat-conv-item"
                    style={{ padding: '8px 10px', borderRadius: 'var(--r-sm)' }}
                    onClick={() => (isGroupMode ? toggleSelectMember(m.userId) : void handleStartDirect(m.userId))}
                  >
                    <ChatAvatar name={m.name} size="sm" />
                    <span style={{ flex: 1, textAlign: 'left', fontSize: 13.5, fontWeight: 500 }}>
                      {m.name}
                    </span>
                    {isGroupMode && (
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => {}}
                        aria-label={`Chọn ${m.name}`}
                      />
                    )}
                  </button>
                )
              })}
            </div>
          )}

          {isGroupMode && (
            <button
              type="button"
              className="btn sm"
              style={{ width: '100%', marginTop: 14, justifyContent: 'center' }}
              disabled={creating || !groupTitle.trim() || selectedUserIds.length === 0}
              onClick={() => void handleCreateGroup()}
            >
              {creating ? 'Đang tạo...' : `Tạo nhóm (${selectedUserIds.length} thành viên)`}
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="chat-popover-search">
            <div className="chat-search-input-wrap">
              <Search size={15} className="chat-search-icon" aria-hidden="true" />
              <input
                type="search"
                className="chat-search-input"
                placeholder="Tìm kiếm đoạn chat hoặc thành viên..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Tìm kiếm cuộc hội thoại"
              />
            </div>
          </div>

          <div className="chat-popover-list" role="list" aria-label="Danh sách cuộc trò chuyện">
            {loadingConversations && conversations.length === 0 ? (
              <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-3)', fontSize: 13 }}>
                Đang tải đoạn chat...
              </p>
            ) : filteredConversations.length === 0 ? (
              <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-3)', fontSize: 13 }}>
                {searchQuery ? 'Không tìm thấy cuộc trò chuyện nào.' : 'Chưa có đoạn chat nào.'}
              </p>
            ) : (
              filteredConversations.map((conv) => {
                const displayName = conv.displayName || conv.title || 'Cuộc trò chuyện'
                const isUnread = conv.unreadCount > 0
                const isGroup = conv.type === 'GROUP'
                const preview = conv.lastMessage?.content
                  ? conv.lastMessage.content
                  : conv.lastMessage?.files?.length
                  ? '[Tệp đính kèm]'
                  : 'Bắt đầu cuộc trò chuyện'

                return (
                  <button
                    key={conv.conversationId}
                    type="button"
                    className={`chat-conv-item ${isUnread ? 'is-unread' : ''}`}
                    onClick={() => handleSelectConversation(conv)}
                    aria-label={`Mở chat với ${displayName}${isUnread ? `, có ${conv.unreadCount} tin nhắn mới` : ''}`}
                  >
                    <ChatAvatar name={displayName} type={conv.type} />
                    <div className="chat-conv-info">
                      <div className="chat-conv-top">
                        <span className="chat-conv-name">
                          {isGroup ? `[Nhóm] ${displayName}` : displayName}
                        </span>
                        {conv.lastMessage?.createdAt && (
                          <time className="chat-conv-time" dateTime={conv.lastMessage.createdAt}>
                            {formatShortTime(conv.lastMessage.createdAt)}
                          </time>
                        )}
                      </div>
                      <div className="chat-conv-bottom">
                        <span className="chat-conv-preview">{preview}</span>
                        {isUnread && <span className="chat-unread-dot" aria-hidden="true" />}
                      </div>
                    </div>
                  </button>
                )
              })
            )}
          </div>

          <footer className="chat-popover-foot">
            <Link to="/chat" className="chat-see-all-link" onClick={onClose}>
              <span>Xem tất cả trong Chat</span>
              <ArrowRight size={14} aria-hidden="true" />
            </Link>
          </footer>
        </>
      )}
    </section>
  )
}

function formatShortTime(iso: string): string {
  try {
    const d = new Date(iso)
    const now = new Date()
    if (d.toDateString() === now.toDateString()) {
      return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(d)
    }
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(d)
  } catch {
    return ''
  }
}
