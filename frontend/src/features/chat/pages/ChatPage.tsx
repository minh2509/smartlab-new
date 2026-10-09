import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  Edit2,
  Info,
  Plus,
  Search,
  UserMinus,
} from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { searchLabMembers } from '../api'
import { useChat } from '../chatContext'
import { ChatAvatar } from '../components/ChatAvatar'
import { MessageComposer } from '../components/MessageComposer'
import { MessageList } from '../components/MessageList'
import type { ChatConversationResponse, ChatMembershipRole } from '../types'

export function ChatPage() {
  const { profile, token } = useAuth()
  const {
    conversations,
    loadingConversations,
    loadMessages,
    markAsRead,
    renameChatGroup,
    addMemberToGroup,
    removeMemberFromGroup,
    changeMemberRoleInGroup,
    createGroupChat,
    createDirectChat,
  } = useChat()

  const [searchParams, setSearchParams] = useSearchParams()
  const activeIdFromQuery = searchParams.get('id')

  const [activeConvId, setActiveConvId] = useState<string | null>(activeIdFromQuery)
  const [searchQuery, setSearchQuery] = useState('')
  const [showDetails, setShowDetails] = useState(true)

  // Group editing state
  const [isRenaming, setIsRenaming] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [showAddMemberModal, setShowAddMemberModal] = useState(false)
  const [candidateMembers, setCandidateMembers] = useState<{ userId: string; name: string }[]>([])
  const [selectedAddUserId, setSelectedAddUserId] = useState('')
  const [loadingCandidates, setLoadingCandidates] = useState(false)

  // New Chat Modal state
  const [showNewChatModal, setShowNewChatModal] = useState(false)
  const [newChatIsGroup, setNewChatIsGroup] = useState(false)
  const [newChatTitle, setNewChatTitle] = useState('')
  const [newChatSelectedUserIds, setNewChatSelectedUserIds] = useState<string[]>([])
  const [newChatCandidates, setNewChatCandidates] = useState<{ userId: string; name: string }[]>([])
  const [creatingChat, setCreatingChat] = useState(false)

  // Sync query param
  useEffect(() => {
    if (activeIdFromQuery && activeIdFromQuery !== activeConvId) {
      setActiveConvId(activeIdFromQuery)
    }
  }, [activeIdFromQuery, activeConvId])

  // When active conversation changes, load messages and mark read
  useEffect(() => {
    if (activeConvId) {
      void loadMessages(activeConvId)
      void markAsRead(activeConvId)
    }
  }, [activeConvId, loadMessages, markAsRead])

  const activeConversation: ChatConversationResponse | undefined = conversations.find(
    (c) => c.conversationId === activeConvId,
  )

  const handleSelectConversation = (convId: string) => {
    setActiveConvId(convId)
    setSearchParams({ id: convId })
    void loadMessages(convId)
    void markAsRead(convId)
  }

  const handleBackToList = () => {
    setActiveConvId(null)
    setSearchParams({})
  }

  // Filter conversations
  const filteredConversations = conversations.filter((c) => {
    const q = searchQuery.toLowerCase().trim()
    if (!q) return true
    const titleMatch = (c.title ?? '').toLowerCase().includes(q)
    const displayMatch = (c.displayName ?? '').toLowerCase().includes(q)
    const memberMatch = c.members.some((m) => m.name.toLowerCase().includes(q))
    return titleMatch || displayMatch || memberMatch
  })

  // Role permissions for current user in active conversation
  const currentMember = activeConversation?.members.find((m) => m.userId === profile?.userId)
  const userRole: ChatMembershipRole = currentMember?.role ?? 'MEMBER'
  const isOwner = userRole === 'OWNER'
  const isManager = isOwner || userRole === 'ADMIN'
  const isGroup = activeConversation?.type === 'GROUP'

  const handleSaveRename = async () => {
    if (!activeConvId || !newTitle.trim()) return
    await renameChatGroup(activeConvId, newTitle.trim())
    setIsRenaming(false)
  }

  const handleOpenAddMember = async () => {
    if (!token || !activeConversation) return
    setShowAddMemberModal(true)
    setLoadingCandidates(true)
    try {
      const list = await searchLabMembers(token)
      const existingUserIds = new Set(
        activeConversation.members.filter((m) => m.active).map((m) => m.userId),
      )
      const available = list
        .filter((m) => !existingUserIds.has(m.userId))
        .map((m) => ({ userId: m.userId, name: m.name }))
      setCandidateMembers(available)
      if (available.length > 0) setSelectedAddUserId(available[0].userId)
    } catch (err) {
      console.error('Failed to load candidate members:', err)
    } finally {
      setLoadingCandidates(false)
    }
  }

  const handleConfirmAddMember = async () => {
    if (!activeConvId || !selectedAddUserId) return
    await addMemberToGroup(activeConvId, selectedAddUserId)
    setShowAddMemberModal(false)
    setSelectedAddUserId('')
  }

  const handleRemoveMember = async (userId: string) => {
    if (!activeConvId) return
    const isSelf = userId === profile?.userId
    const promptMsg = isSelf
      ? 'Bạn có chắc chắn muốn rời nhóm chat này?'
      : 'Bạn có chắc chắn muốn xóa thành viên này khỏi nhóm?'
    if (!window.confirm(promptMsg)) return
    await removeMemberFromGroup(activeConvId, userId)
    if (isSelf) {
      setActiveConvId(null)
      setSearchParams({})
    }
  }

  const handleChangeRole = async (userId: string, targetRole: ChatMembershipRole) => {
    if (!activeConvId) return
    await changeMemberRoleInGroup(activeConvId, userId, targetRole)
  }

  // Handle open new chat modal
  const handleOpenNewChatModal = async () => {
    setShowNewChatModal(true)
    setNewChatIsGroup(false)
    setNewChatTitle('')
    setNewChatSelectedUserIds([])
    if (token) {
      try {
        const list = await searchLabMembers(token)
        const candidates = list
          .filter((m) => m.userId !== profile?.userId)
          .map((m) => ({ userId: m.userId, name: m.name }))
        setNewChatCandidates(candidates)
      } catch (err) {
        console.error('Failed to load members for new chat:', err)
      }
    }
  }

  const handleCreateNewDirect = async (targetUserId: string) => {
    if (creatingChat) return
    setCreatingChat(true)
    try {
      const conv = await createDirectChat(targetUserId)
      setShowNewChatModal(false)
      handleSelectConversation(conv.conversationId)
    } catch (err) {
      console.error('Failed to start chat:', err)
    } finally {
      setCreatingChat(false)
    }
  }

  const handleCreateNewGroup = async () => {
    if (creatingChat || !newChatTitle.trim() || newChatSelectedUserIds.length === 0) return
    setCreatingChat(true)
    try {
      const conv = await createGroupChat({
        title: newChatTitle.trim(),
        memberUserIds: newChatSelectedUserIds,
      })
      setShowNewChatModal(false)
      handleSelectConversation(conv.conversationId)
    } catch (err) {
      console.error('Failed to start group:', err)
    } finally {
      setCreatingChat(false)
    }
  }

  const toggleSelectNewMember = (userId: string) => {
    setNewChatSelectedUserIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId],
    )
  }

  return (
    <div className={`chat-page-shell ${activeConvId ? 'has-active-chat' : ''}`}>
      {/* 1. LEFT SIDEBAR: Conversations List */}
      <aside className="chat-page-sidebar" aria-label="Danh sách cuộc hội thoại">
        <div className="chat-popover-head" style={{ borderBottom: '1px solid var(--line)' }}>
          <div className="chat-popover-title-row">
            <h1 style={{ fontSize: 18, fontWeight: 700 }}>Đoạn chat</h1>
          </div>
          <button
            type="button"
            className="chat-icon-action-btn"
            title="Đoạn chat mới"
            aria-label="Tạo đoạn chat mới"
            onClick={handleOpenNewChatModal}
          >
            <Plus size={18} aria-hidden="true" />
          </button>
        </div>

        <div className="chat-popover-search">
          <div className="chat-search-input-wrap">
            <Search size={15} className="chat-search-icon" aria-hidden="true" />
            <input
              type="search"
              className="chat-search-input"
              placeholder="Tìm kiếm đoạn chat..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Tìm kiếm trong danh sách đoạn chat"
            />
          </div>
        </div>

        <div className="chat-popover-list" role="list">
          {loadingConversations && conversations.length === 0 ? (
            <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-3)', fontSize: 13 }}>
              Đang tải danh sách đoạn chat...
            </p>
          ) : filteredConversations.length === 0 ? (
            <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-3)', fontSize: 13 }}>
              {searchQuery ? 'Không tìm thấy cuộc trò chuyện nào.' : 'Chưa có đoạn chat nào.'}
            </p>
          ) : (
            filteredConversations.map((conv) => {
              const displayName = conv.displayName || conv.title || 'Cuộc trò chuyện'
              const isSelected = conv.conversationId === activeConvId
              const isUnread = conv.unreadCount > 0
              const isGrp = conv.type === 'GROUP'
              const preview = conv.lastMessage?.content
                ? conv.lastMessage.content
                : conv.lastMessage?.files?.length
                ? '[Tệp đính kèm]'
                : 'Bắt đầu cuộc trò chuyện'

              return (
                <button
                  key={conv.conversationId}
                  type="button"
                  className={`chat-conv-item ${isSelected ? 'is-active' : ''} ${
                    isUnread ? 'is-unread' : ''
                  }`}
                  onClick={() => handleSelectConversation(conv.conversationId)}
                  aria-label={`Mở đoạn chat ${displayName}`}
                >
                  <ChatAvatar name={displayName} type={conv.type} />
                  <div className="chat-conv-info">
                    <div className="chat-conv-top">
                      <span className="chat-conv-name">
                        {isGrp ? `[Nhóm] ${displayName}` : displayName}
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
      </aside>

      {/* 2. CENTER: Active Conversation Thread */}
      <main className="chat-page-main">
        {activeConversation ? (
          <>
            <header className="chat-popover-head" style={{ height: 56, padding: '0 16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {/* Back button for mobile */}
                <button
                  type="button"
                  className="chat-icon-action-btn md:hidden"
                  onClick={handleBackToList}
                  aria-label="Quay lại danh sách"
                >
                  <ArrowLeft size={16} aria-hidden="true" />
                </button>

                <ChatAvatar
                  name={activeConversation.displayName || activeConversation.title || 'Chat'}
                  type={activeConversation.type}
                  size="sm"
                />

                <div>
                  <h2 style={{ fontSize: 15, fontWeight: 700, margin: 0 }}>
                    {activeConversation.displayName || activeConversation.title || 'Đoạn chat'}
                  </h2>
                  <small style={{ color: 'var(--text-3)', fontSize: 12 }}>
                    {isGroup
                      ? `${activeConversation.members.filter((m) => m.active).length} thành viên`
                      : 'Đang hoạt động'}
                  </small>
                </div>
              </div>

              <button
                type="button"
                className={`chat-icon-action-btn ${showDetails ? 'is-active' : ''}`}
                title="Thông tin cuộc trò chuyện"
                aria-label="Bật tắt thông tin cuộc trò chuyện"
                onClick={() => setShowDetails(!showDetails)}
              >
                <Info size={16} aria-hidden="true" />
              </button>
            </header>

            <MessageList conversationId={activeConversation.conversationId} />
            <MessageComposer conversationId={activeConversation.conversationId} />
          </>
        ) : (
          <div className="chat-empty-thread">
            <ChatAvatar name="SmartLab" size="lg" />
            <h2 style={{ fontSize: 18, color: 'var(--text-1)' }}>Hệ thống tin nhắn SmartLab</h2>
            <p style={{ maxWidth: 360, textAlign: 'center' }}>
              Chọn một cuộc hội thoại từ danh sách bên trái hoặc tạo đoạn chat mới để bắt đầu trao đổi công việc và nghiên cứu.
            </p>
          </div>
        )}
      </main>

      {/* 3. RIGHT SIDEBAR: Conversation Details (Desktop only) */}
      {activeConversation && showDetails && (
        <aside className="chat-page-details" aria-label="Chi tiết cuộc trò chuyện">
          <div className="chat-page-details-head">
            <ChatAvatar
              name={activeConversation.displayName || activeConversation.title || 'Chat'}
              type={activeConversation.type}
              size="lg"
              className="chat-page-details-avatar"
            />

            {isRenaming ? (
              <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
                <input
                  className="chat-search-input"
                  style={{ paddingLeft: 8 }}
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="Tên nhóm mới..."
                  aria-label="Tên nhóm mới"
                />
                <button
                  type="button"
                  className="btn sm"
                  onClick={() => void handleSaveRename()}
                >
                  Lưu
                </button>
                <button
                  type="button"
                  className="chat-icon-action-btn"
                  onClick={() => setIsRenaming(false)}
                >
                  Hủy
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                <span className="chat-page-details-title">
                  {activeConversation.displayName || activeConversation.title}
                </span>
                {isGroup && isManager && (
                  <button
                    type="button"
                    className="chat-icon-action-btn"
                    style={{ width: 24, height: 24 }}
                    title="Đổi tên nhóm"
                    aria-label="Đổi tên nhóm"
                    onClick={() => {
                      setNewTitle(activeConversation.title || '')
                      setIsRenaming(true)
                    }}
                  >
                    <Edit2 size={13} aria-hidden="true" />
                  </button>
                )}
              </div>
            )}

            <p style={{ color: 'var(--text-3)', fontSize: 12, marginTop: 4 }}>
              {isGroup ? 'Nhóm thảo luận nội bộ' : 'Cuộc trò chuyện trực tiếp'}
            </p>
          </div>

          {/* Group Members Section */}
          {isGroup && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <strong style={{ fontSize: 13, color: 'var(--text-1)' }}>
                  Thành viên ({activeConversation.members.filter((m) => m.active).length})
                </strong>
                {isManager && (
                  <button
                    type="button"
                    className="chat-see-all-link"
                    style={{ fontSize: 12 }}
                    onClick={() => void handleOpenAddMember()}
                  >
                    + Thêm
                  </button>
                )}
              </div>

              {showAddMemberModal && (
                <div style={{ padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', margin: '8px 0' }}>
                  {loadingCandidates ? (
                    <small>Đang tải...</small>
                  ) : candidateMembers.length === 0 ? (
                    <small>Không còn thành viên khả dụng để thêm.</small>
                  ) : (
                    <>
                      <select
                        style={{ width: '100%', marginBottom: 6, padding: '4px 6px', fontSize: 12 }}
                        value={selectedAddUserId}
                        onChange={(e) => setSelectedAddUserId(e.target.value)}
                        aria-label="Chọn thành viên để thêm"
                      >
                        {candidateMembers.map((c) => (
                          <option key={c.userId} value={c.userId}>
                            {c.name}
                          </option>
                        ))}
                      </select>
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button
                          type="button"
                          className="btn sm"
                          style={{ padding: '2px 8px', fontSize: 11 }}
                          onClick={() => void handleConfirmAddMember()}
                        >
                          Xác nhận
                        </button>
                        <button
                          type="button"
                          className="chat-icon-action-btn"
                          style={{ width: 22, height: 22 }}
                          onClick={() => setShowAddMemberModal(false)}
                        >
                          ✕
                        </button>
                      </div>
                    </>
                  )}
                </div>
              )}

              <div className="chat-page-members-list">
                {activeConversation.members
                  .filter((m) => m.active)
                  .map((member) => {
                    const isTargetSelf = member.userId === profile?.userId
                    const canRemoveThisMember =
                      isTargetSelf ||
                      (isOwner && member.role !== 'OWNER') ||
                      (userRole === 'ADMIN' && member.role === 'MEMBER')
                    const canChangeRole = isOwner && !isTargetSelf

                    return (
                      <div key={member.userId} className="chat-page-member-row">
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                          <ChatAvatar name={member.name} size="sm" />
                          <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {member.name} {isTargetSelf ? '(Bạn)' : ''}
                          </span>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          {canChangeRole ? (
                            <select
                              value={member.role}
                              style={{ fontSize: 11, padding: '1px 4px', borderRadius: 4 }}
                              onChange={(e) =>
                                void handleChangeRole(member.userId, e.target.value as ChatMembershipRole)
                              }
                              aria-label={`Thay đổi vai trò của ${member.name}`}
                            >
                              <option value="MEMBER">MEMBER</option>
                              <option value="ADMIN">ADMIN</option>
                            </select>
                          ) : (
                            <span className="chat-page-role-badge">{member.role}</span>
                          )}

                          {canRemoveThisMember && (
                            <button
                              type="button"
                              className="chat-icon-action-btn"
                              style={{ width: 22, height: 22, color: 'var(--danger)' }}
                              title={isTargetSelf ? 'Rời nhóm' : 'Xóa khỏi nhóm'}
                              aria-label={isTargetSelf ? 'Rời nhóm' : `Xóa ${member.name} khỏi nhóm`}
                              onClick={() => void handleRemoveMember(member.userId)}
                            >
                              <UserMinus size={12} aria-hidden="true" />
                            </button>
                          )}
                        </div>
                      </div>
                    )
                  })}
              </div>
            </div>
          )}
        </aside>
      )}

      {/* New Chat Modal Dialog */}
      {showNewChatModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Tạo đoạn chat mới"
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 2000,
            background: 'rgba(10, 27, 51, 0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16,
          }}
        >
          <div
            style={{
              width: 420,
              maxWidth: '100%',
              background: 'var(--surface)',
              borderRadius: 'var(--r-lg)',
              boxShadow: 'var(--sh-3)',
              overflow: 'hidden',
              border: '1px solid var(--line-2)',
            }}
          >
            <div className="chat-popover-head">
              <h2>{newChatIsGroup ? 'Tạo nhóm chat' : 'Tin nhắn mới (1:1)'}</h2>
              <button
                type="button"
                className="chat-icon-action-btn"
                onClick={() => setShowNewChatModal(false)}
                aria-label="Đóng hộp thoại"
              >
                ✕
              </button>
            </div>

            <div style={{ padding: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
                <span style={{ fontSize: 13, color: 'var(--text-3)' }}>
                  {newChatIsGroup ? 'Chọn các thành viên tham gia nhóm:' : 'Chọn thành viên bạn muốn trò chuyện:'}
                </span>
                <button
                  type="button"
                  className="chat-see-all-link"
                  style={{ fontSize: 12 }}
                  onClick={() => setNewChatIsGroup(!newChatIsGroup)}
                >
                  {newChatIsGroup ? 'Chuyển sang chat 1:1' : 'Tạo nhóm nhiều người'}
                </button>
              </div>

              {newChatIsGroup && (
                <div style={{ marginBottom: 12 }}>
                  <input
                    className="chat-search-input"
                    style={{ paddingLeft: 10 }}
                    placeholder="Đặt tên nhóm thảo luận..."
                    value={newChatTitle}
                    onChange={(e) => setNewChatTitle(e.target.value)}
                    aria-label="Tên nhóm thảo luận"
                  />
                </div>
              )}

              <div style={{ maxHeight: 260, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
                {newChatCandidates.map((m) => {
                  const isSelected = newChatSelectedUserIds.includes(m.userId)
                  return (
                    <button
                      key={m.userId}
                      type="button"
                      className="chat-conv-item"
                      style={{ padding: '8px 10px', borderRadius: 'var(--r-sm)' }}
                      onClick={() =>
                        newChatIsGroup
                          ? toggleSelectNewMember(m.userId)
                          : void handleCreateNewDirect(m.userId)
                      }
                    >
                      <ChatAvatar name={m.name} size="sm" />
                      <span style={{ flex: 1, textAlign: 'left', fontSize: 13.5, fontWeight: 500 }}>
                        {m.name}
                      </span>
                      {newChatIsGroup && (
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

              {newChatIsGroup && (
                <button
                  type="button"
                  className="btn sm"
                  style={{ width: '100%', marginTop: 14, justifyContent: 'center' }}
                  disabled={creatingChat || !newChatTitle.trim() || newChatSelectedUserIds.length === 0}
                  onClick={() => void handleCreateNewGroup()}
                >
                  {creatingChat ? 'Đang tạo nhóm...' : `Tạo nhóm (${newChatSelectedUserIds.length} thành viên)`}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
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
