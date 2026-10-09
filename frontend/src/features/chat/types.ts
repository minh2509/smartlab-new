export type ChatConversationType = 'DIRECT' | 'GROUP'
export type ChatMessageType = 'TEXT' | 'FILE' | 'MIXED' | 'SYSTEM'
export type ChatMembershipRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export type ChatMessageFileResponse = {
  fileId: number
  originalName: string
  mimeType: string
  sizeBytes: number
  accessScope: string
}

export type ChatUserSummary = {
  userId: string
  name: string
}

export type ChatMemberResponse = {
  userId: string
  name: string
  role: ChatMembershipRole
  joinedAt: string
  leftAt: string | null
  lastReadSeq: number
  active: boolean
}

export type ChatMessageResponse = {
  id: number
  conversationId: string
  messageSeq: number
  sender: ChatUserSummary
  clientMessageId: string
  messageType: ChatMessageType
  content: string | null
  replyToMessageId: number | null
  files: ChatMessageFileResponse[]
  createdAt: string
  editedAt: string | null
  deletedAt: string | null
}

export type ChatMessagePageResponse = {
  messages: ChatMessageResponse[]
  nextBeforeSeq: number | null
  nextAfterSeq: number | null
  hasMore: boolean
}

export type ChatConversationResponse = {
  conversationId: string
  type: ChatConversationType
  displayName: string
  title: string | null
  projectId: number | null
  members: ChatMemberResponse[]
  lastMessage: ChatMessageResponse | null
  lastMessageSeq: number
  lastReadSeq: number
  unreadCount: number
  createdAt: string
  updatedAt: string
}

export type ChatReadResponse = {
  conversationId: string
  userId: string
  lastReadSeq: number
}

export type ChatEventEnvelope<T = unknown> = {
  event: string
  eventId: string
  occurredAt: string
  conversationId: string
  data: T
}

export type ChatConversationEventData = {
  conversationId: string
  type: ChatConversationType
  title: string | null
  projectId: number | null
  members: ChatMemberResponse[]
  lastMessageSeq: number
  updatedAt: string
}

export type ChatMemberEventData = {
  conversationId: string
  member: ChatMemberResponse
}

export type ChatTypingEventData = {
  conversationId: string
  user: ChatUserSummary
}

// REST & STOMP requests
export type ChatSendMessageRequest = {
  clientMessageId: string
  messageType: ChatMessageType
  content?: string
  replyToMessageId?: number
  fileIds?: number[]
}

export type CreateDirectChatRequest = {
  userId: string
}

export type CreateGroupChatRequest = {
  title: string
  memberUserIds: string[]
  projectId?: number
}

export type ChatReadRequest = {
  lastReadSeq: number
}

export type ChatTypingRequest = {
  started: boolean
}

export type RenameChatRequest = {
  title: string
}

export type AddChatMemberRequest = {
  userId: string
}

export type ChangeChatMemberRoleRequest = {
  role: ChatMembershipRole
}

export type EditChatMessageRequest = {
  content: string
}

// Client UI & State models
export type MessageSendStatus = 'SENDING' | 'SENT' | 'FAILED'

export type ChatMessageItem = ChatMessageResponse & {
  status: MessageSendStatus
  errorMessage?: string
}

export type OpenPopup = {
  conversationId: string
  openedAt: number
  lastFocusedAt: number
  minimized: boolean
}

export type ChatDockItem = {
  conversationId: string
  addedAt: number
}

export type TypingUser = {
  userId: string
  name: string
  expiresAt: number
}

export type ChatConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR'

export type ChatContextValue = {
  conversations: ChatConversationResponse[]
  activeConversationId: string | null
  popups: OpenPopup[]
  dockItems: ChatDockItem[]
  activePopupId: string | null
  connectionState: ChatConnectionState
  totalUnreadCount: number
  loadingConversations: boolean
  messagesByConversation: Record<string, ChatMessageItem[]>
  typingUsers: Record<string, TypingUser[]>
  hasMoreOlderMessages: (conversationId: string) => boolean
  isLoadingOlder: (conversationId: string) => boolean

  // Actions
  refreshConversations: () => Promise<void>
  loadMessages: (conversationId: string) => Promise<void>
  loadOlderMessages: (conversationId: string) => Promise<void>
  openConversation: (conversationId: string) => void
  closePopup: (conversationId: string) => void
  minimizePopup: (conversationId: string) => void
  focusPopup: (conversationId: string) => void
  removeDockItem: (conversationId: string) => void
  markAsRead: (conversationId: string) => Promise<void>
  sendMessage: (
    conversationId: string,
    payload: { content?: string; fileIds?: number[]; replyToMessageId?: number },
  ) => Promise<void>
  retrySendMessage: (conversationId: string, clientMessageId: string) => Promise<void>
  sendTypingSignal: (conversationId: string, started: boolean) => void
  createDirectChat: (targetUserId: string) => Promise<ChatConversationResponse>
  createGroupChat: (request: CreateGroupChatRequest) => Promise<ChatConversationResponse>
  renameChatGroup: (conversationId: string, title: string) => Promise<void>
  addMemberToGroup: (conversationId: string, userId: string) => Promise<void>
  removeMemberFromGroup: (conversationId: string, userId: string) => Promise<void>
  changeMemberRoleInGroup: (conversationId: string, userId: string, role: ChatMembershipRole) => Promise<void>
  editMessage: (conversationId: string, messageId: number, content: string) => Promise<void>
  deleteMessage: (conversationId: string, messageId: number) => Promise<void>
}
