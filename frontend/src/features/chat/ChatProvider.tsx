import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/authContext'
import * as chatApi from './api'
import { ChatContext } from './chatContext'
import { chatRealtimeClient } from './realtime'
import type {
  ChatConnectionState,
  ChatContextValue,
  ChatConversationResponse,
  ChatDockItem,
  ChatEventEnvelope,
  ChatMessageFileResponse,
  ChatMessageItem,
  ChatMessagePageResponse,
  ChatMessageResponse,
  ChatMembershipRole,
  ChatMessageType,
  ChatReadResponse,
  ChatTypingEventData,
  CreateGroupChatRequest,
  OpenPopup,
  TypingUser,
} from './types'

export function ChatProvider({ children }: { children: ReactNode }) {
  const { token, profile, isAuthenticated } = useAuth()

  const [conversations, setConversations] = useState<ChatConversationResponse[]>([])
  const [loadingConversations, setLoadingConversations] = useState(false)
  const [messagesByConversation, setMessagesByConversation] = useState<Record<string, ChatMessageItem[]>>({})
  const [historyMetadata, setHistoryMetadata] = useState<
    Record<
      string,
      {
        nextBeforeSeq: number | null
        nextAfterSeq: number | null
        hasMore: boolean
        highestSeq: number
        loadingOlder: boolean
      }
    >
  >({})
  const [popups, setPopups] = useState<OpenPopup[]>([])
  const [dockItems, setDockItems] = useState<ChatDockItem[]>([])
  const [activePopupId, setActivePopupId] = useState<string | null>(null)
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [typingUsers, setTypingUsers] = useState<Record<string, TypingUser[]>>({})
  const [connectionState, setConnectionState] = useState<ChatConnectionState>('DISCONNECTED')

  // Refs for callbacks & active states
  const activePopupIdRef = useRef<string | null>(null)
  activePopupIdRef.current = activePopupId
  const popupsRef = useRef<OpenPopup[]>([])
  popupsRef.current = popups
  const activeConversationIdRef = useRef<string | null>(null)
  activeConversationIdRef.current = activeConversationId
  const tokenRef = useRef<string | null>(null)
  tokenRef.current = token
  const historyMetadataRef = useRef(historyMetadata)
  historyMetadataRef.current = historyMetadata
  const profileRef = useRef(profile)
  profileRef.current = profile
  const conversationsRef = useRef(conversations)
  conversationsRef.current = conversations

  // 1. Fetch conversations list
  const refreshConversations = useCallback(async () => {
    if (!tokenRef.current) return
    try {
      setLoadingConversations(true)
      const list = await chatApi.listConversations(tokenRef.current)
      setConversations((prev) => {
        if (prev.length === 0) return list
        return list.map((serverConv) => {
          const localConv = prev.find((c) => c.conversationId === serverConv.conversationId)
          if (!localConv) return serverConv
          if (localConv.lastMessageSeq > serverConv.lastMessageSeq) {
            return {
              ...serverConv,
              lastMessage: localConv.lastMessage,
              lastMessageSeq: localConv.lastMessageSeq,
              unreadCount: Math.max(serverConv.unreadCount, localConv.unreadCount),
            }
          }
          return serverConv
        })
      })
    } catch (err) {
      console.error('Failed to load chat conversations:', err)
    } finally {
      setLoadingConversations(false)
    }
  }, [])

  // 2. Load initial messages for a conversation
  const loadMessages = useCallback(async (conversationId: string) => {
    if (!tokenRef.current) return
    try {
      const page = await chatApi.getConversationMessages(tokenRef.current, conversationId, { limit: 50 })
      const sorted = [...page.messages].sort((a, b) => a.messageSeq - b.messageSeq)
      const items: ChatMessageItem[] = sorted.map((m) => ({ ...m, status: 'SENT' }))
      const highest = items.reduce((max, m) => Math.max(max, m.messageSeq), 0)

      setMessagesByConversation((prev) => ({
        ...prev,
        [conversationId]: items,
      }))
      setHistoryMetadata((prev) => ({
        ...prev,
        [conversationId]: {
          nextBeforeSeq: page.nextBeforeSeq,
          nextAfterSeq: page.nextAfterSeq,
          hasMore: page.hasMore,
          highestSeq: highest,
          loadingOlder: false,
        },
      }))
    } catch (err) {
      console.error(`Failed to load messages for conversation ${conversationId}:`, err)
    }
  }, [])

  // 3. Load older messages (cursor beforeSeq)
  const loadOlderMessages = useCallback(async (conversationId: string) => {
    if (!tokenRef.current) return
    const meta = historyMetadataRef.current[conversationId]
    if (!meta || !meta.hasMore || meta.nextBeforeSeq == null || meta.loadingOlder) return

    setHistoryMetadata((prev) => ({
      ...prev,
      [conversationId]: { ...prev[conversationId], loadingOlder: true },
    }))

    try {
      const page = await chatApi.getConversationMessages(tokenRef.current, conversationId, {
        beforeSeq: meta.nextBeforeSeq,
        limit: 50,
      })
      const olderSorted = [...page.messages].sort((a, b) => a.messageSeq - b.messageSeq)
      const olderItems: ChatMessageItem[] = olderSorted.map((m) => ({ ...m, status: 'SENT' }))

      setMessagesByConversation((prev) => {
        const existing = prev[conversationId] ?? []
        const existingIds = new Set(existing.map((m) => m.id))
        const filteredNewOlder = olderItems.filter((m) => !existingIds.has(m.id))
        return {
          ...prev,
          [conversationId]: [...filteredNewOlder, ...existing],
        }
      })

      setHistoryMetadata((prev) => ({
        ...prev,
        [conversationId]: {
          ...prev[conversationId],
          nextBeforeSeq: page.nextBeforeSeq,
          hasMore: page.hasMore,
          loadingOlder: false,
        },
      }))
    } catch (err) {
      console.error(`Failed to load older messages for ${conversationId}:`, err)
      setHistoryMetadata((prev) => ({
        ...prev,
        [conversationId]: { ...prev[conversationId], loadingOlder: false },
      }))
    }
  }, [])

  // 4. Mark conversation as read
  const markAsRead = useCallback(async (conversationId: string) => {
    if (!tokenRef.current) return
    const conv = conversationsRef.current.find((c) => c.conversationId === conversationId)
    const currentSeq = conv ? conv.lastMessageSeq : 0

    // Send read signal via socket or REST
    const sent = chatRealtimeClient.sendRead(conversationId, { lastReadSeq: currentSeq })
    if (!sent) {
      try {
        await chatApi.markConversationRead(tokenRef.current, conversationId, currentSeq)
      } catch (err) {
        console.error('Failed to mark read via REST:', err)
      }
    }

    // Optimistically update conversation unread state
    setConversations((prev) =>
      prev.map((c) =>
        c.conversationId === conversationId
          ? { ...c, unreadCount: 0, lastReadSeq: Math.max(c.lastReadSeq, currentSeq) }
          : c,
      ),
    )
  }, [])

  // 5. Popup Manager: STRICT FIFO EVICTION by openedAt (max 3)
  const openConversation = useCallback((conversationId: string) => {
    setPopups((current) => {
      const existingIdx = current.findIndex((p) => p.conversationId === conversationId)
      if (existingIdx >= 0) {
        // Already open: restore if minimized, focus it, keep openedAt UNCHANGED!
        const existing = current[existingIdx]
        const updated: OpenPopup = {
          ...existing,
          minimized: false,
          lastFocusedAt: Date.now(),
        }
        const next = [...current]
        next[existingIdx] = updated
        return next
      }

      const newPopup: OpenPopup = {
        conversationId,
        openedAt: Date.now(),
        lastFocusedAt: Date.now(),
        minimized: false,
      }

      if (current.length < 3) {
        return [...current, newPopup]
      }

      // Evict popup with SMALLEST openedAt (strict FIFO)
      let oldestIdx = 0
      for (let i = 1; i < current.length; i++) {
        if (current[i].openedAt < current[oldestIdx].openedAt) {
          oldestIdx = i
        }
      }

      const next = current.filter((_, idx) => idx !== oldestIdx)
      next.push(newPopup)
      return next
    })

    // Also ensure it is in the dock
    setDockItems((prev) =>
      prev.some((d) => d.conversationId === conversationId)
        ? prev
        : [...prev, { conversationId, addedAt: Date.now() }],
    )

    setActivePopupId(conversationId)
    setActiveConversationId(conversationId)

    // Ensure messages are loaded
    void loadMessages(conversationId)
    void markAsRead(conversationId)
  }, [loadMessages, markAsRead])

  const closePopup = useCallback((conversationId: string) => {
    setPopups((current) => current.filter((p) => p.conversationId !== conversationId))
    setActivePopupId((current) => (current === conversationId ? null : current))
    // Note: dock item is preserved!
  }, [])

  const minimizePopup = useCallback((conversationId: string) => {
    setPopups((current) =>
      current.map((p) =>
        p.conversationId === conversationId ? { ...p, minimized: !p.minimized } : p,
      ),
    )
  }, [])

  const focusPopup = useCallback((conversationId: string) => {
    setPopups((current) =>
      current.map((p) =>
        p.conversationId === conversationId ? { ...p, lastFocusedAt: Date.now() } : p,
      ),
    )
    setActivePopupId(conversationId)
    setActiveConversationId(conversationId)
    void markAsRead(conversationId)
  }, [markAsRead])

  const removeDockItem = useCallback((conversationId: string) => {
    setDockItems((prev) => prev.filter((d) => d.conversationId !== conversationId))
  }, [])

  // 6. Optimistic Send Message
  const sendMessage = useCallback(
    async (
      conversationId: string,
      payload: { content?: string; fileIds?: number[]; replyToMessageId?: number },
    ) => {
      const currentUser = profileRef.current
      if (!tokenRef.current || !currentUser) return

      const clientMessageId =
        typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `client-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`

      const hasFiles = Boolean(payload.fileIds && payload.fileIds.length > 0)
      const hasContent = Boolean(payload.content && payload.content.trim())
      const messageType: ChatMessageType = hasFiles ? (hasContent ? 'MIXED' : 'FILE') : 'TEXT'

      // Construct temporary local optimistic message
      const optimisticItem: ChatMessageItem = {
        id: -Date.now(),
        conversationId,
        messageSeq: 0,
        sender: {
          userId: currentUser.userId,
          name: currentUser.name,
        },
        clientMessageId,
        messageType,
        content: payload.content ?? null,
        replyToMessageId: payload.replyToMessageId ?? null,
        files: (payload.fileIds ?? []).map(
          (fid): ChatMessageFileResponse => ({
            fileId: fid,
            originalName: `file-${fid}`,
            mimeType: 'application/octet-stream',
            sizeBytes: 0,
            accessScope: 'LAB',
          }),
        ),
        createdAt: new Date().toISOString(),
        editedAt: null,
        deletedAt: null,
        status: 'SENDING',
      }

      // Add to state immediately
      setMessagesByConversation((prev) => ({
        ...prev,
        [conversationId]: [...(prev[conversationId] ?? []), optimisticItem],
      }))

      // Prepare request payload
      const request = {
        clientMessageId,
        messageType,
        content: payload.content?.trim() || undefined,
        replyToMessageId: payload.replyToMessageId,
        fileIds: payload.fileIds,
      }

      // Attempt sending via STOMP if connected, else fallback to REST
      const sentStomp = chatRealtimeClient.sendMessage(conversationId, request)
      if (!sentStomp) {
        try {
          const res = await chatApi.sendChatMessage(tokenRef.current, conversationId, request)
          // Mark sent and replace temporary ID with canonical ID
          setMessagesByConversation((prev) => {
            const list = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: list.map((m) =>
                m.clientMessageId === clientMessageId ? { ...res, status: 'SENT' } : m,
              ),
            }
          })
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Gửi tin nhắn thất bại'
          setMessagesByConversation((prev) => {
            const list = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: list.map((m) =>
                m.clientMessageId === clientMessageId
                  ? { ...m, status: 'FAILED', errorMessage: msg }
                  : m,
              ),
            }
          })
        }
      }
    },
    [],
  )

  // 7. Retry Send Message
  const retrySendMessage = useCallback(
    async (conversationId: string, clientMessageId: string) => {
      if (!tokenRef.current) return
      const list = messagesByConversation[conversationId] ?? []
      const target = list.find((m) => m.clientMessageId === clientMessageId)
      if (!target) return

      // Set status back to SENDING with the EXACT SAME clientMessageId
      setMessagesByConversation((prev) => {
        const currentList = prev[conversationId] ?? []
        return {
          ...prev,
          [conversationId]: currentList.map((m) =>
            m.clientMessageId === clientMessageId
              ? { ...m, status: 'SENDING', errorMessage: undefined }
              : m,
          ),
        }
      })

      const request = {
        clientMessageId: target.clientMessageId,
        messageType: target.messageType,
        content: target.content ?? undefined,
        replyToMessageId: target.replyToMessageId ?? undefined,
        fileIds: target.files.map((f) => f.fileId),
      }

      const sentStomp = chatRealtimeClient.sendMessage(conversationId, request)
      if (!sentStomp) {
        try {
          const res = await chatApi.sendChatMessage(tokenRef.current, conversationId, request)
          setMessagesByConversation((prev) => {
            const currentList = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: currentList.map((m) =>
                m.clientMessageId === clientMessageId ? { ...res, status: 'SENT' } : m,
              ),
            }
          })
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Gửi tin nhắn thất bại'
          setMessagesByConversation((prev) => {
            const currentList = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: currentList.map((m) =>
                m.clientMessageId === clientMessageId
                  ? { ...m, status: 'FAILED', errorMessage: msg }
                  : m,
              ),
            }
          })
        }
      }
    },
    [messagesByConversation],
  )

  // 8. Typing signal
  const sendTypingSignal = useCallback((conversationId: string, started: boolean) => {
    chatRealtimeClient.sendTyping(conversationId, { started })
  }, [])

  // 9. Conversation mutations
  const createDirectChat = useCallback(async (targetUserId: string) => {
    if (!tokenRef.current) throw new Error('Not authenticated')
    const conv = await chatApi.createDirectConversation(tokenRef.current, targetUserId)
    setConversations((prev) => {
      const exists = prev.some((c) => c.conversationId === conv.conversationId)
      return exists ? prev : [conv, ...prev]
    })
    return conv
  }, [])

  const createGroupChat = useCallback(async (request: CreateGroupChatRequest) => {
    if (!tokenRef.current) throw new Error('Not authenticated')
    const conv = await chatApi.createGroupConversation(tokenRef.current, request)
    setConversations((prev) => [conv, ...prev])
    return conv
  }, [])

  const renameChatGroup = useCallback(async (conversationId: string, title: string) => {
    if (!tokenRef.current) return
    const updated = await chatApi.renameConversation(tokenRef.current, conversationId, title)
    setConversations((prev) =>
      prev.map((c) => (c.conversationId === conversationId ? updated : c)),
    )
  }, [])

  const addMemberToGroup = useCallback(async (conversationId: string, userId: string) => {
    if (!tokenRef.current) return
    const member = await chatApi.addConversationMember(tokenRef.current, conversationId, userId)
    setConversations((prev) =>
      prev.map((c) =>
        c.conversationId === conversationId
          ? { ...c, members: [...c.members.filter((m) => m.userId !== userId), member] }
          : c,
      ),
    )
  }, [])

  const removeMemberFromGroup = useCallback(async (conversationId: string, userId: string) => {
    if (!tokenRef.current) return
    await chatApi.removeConversationMember(tokenRef.current, conversationId, userId)
    setConversations((prev) =>
      prev.map((c) =>
        c.conversationId === conversationId
          ? {
              ...c,
              members: c.members.map((m) =>
                m.userId === userId ? { ...m, active: false, leftAt: new Date().toISOString() } : m,
              ),
            }
          : c,
      ),
    )
  }, [])

  const changeMemberRoleInGroup = useCallback(
    async (conversationId: string, userId: string, role: ChatMembershipRole) => {
      if (!tokenRef.current) return
      const updated = await chatApi.changeMemberRole(tokenRef.current, conversationId, userId, role)
      setConversations((prev) =>
        prev.map((c) =>
          c.conversationId === conversationId
            ? {
                ...c,
                members: c.members.map((m) => (m.userId === userId ? updated : m)),
              }
            : c,
        ),
      )
    },
    [],
  )

  const editMessage = useCallback(
    async (conversationId: string, messageId: number, content: string) => {
      if (!tokenRef.current) return
      const updated = await chatApi.editChatMessage(tokenRef.current, conversationId, messageId, content)
      setMessagesByConversation((prev) => {
        const list = prev[conversationId] ?? []
        return {
          ...prev,
          [conversationId]: list.map((m) => (m.id === messageId ? { ...updated, status: 'SENT' } : m)),
        }
      })
    },
    [],
  )

  const deleteMessage = useCallback(async (conversationId: string, messageId: number) => {
    if (!tokenRef.current) return
    await chatApi.deleteChatMessage(tokenRef.current, conversationId, messageId)
    setMessagesByConversation((prev) => {
      const list = prev[conversationId] ?? []
      return {
        ...prev,
        [conversationId]: list.map((m) =>
          m.id === messageId ? { ...m, deletedAt: new Date().toISOString() } : m,
        ),
      }
    })
  }, [])

  // 10. Reconnect catch-up: fetches afterSeq for all loaded conversations
  const catchUpAfterReconnect = useCallback(async () => {
    if (!tokenRef.current) return
    // Refresh conversation list to get latest unread counts & last messages
    void refreshConversations()

    // For every loaded conversation, fetch missed messages after highestSeq
    const loadedConvIds = Object.keys(historyMetadataRef.current)
    for (const convId of loadedConvIds) {
      const meta = historyMetadataRef.current[convId]
      if (meta && meta.highestSeq > 0) {
        try {
          const page: ChatMessagePageResponse = await chatApi.getConversationMessages(
            tokenRef.current,
            convId,
            { afterSeq: meta.highestSeq, limit: 100 },
          )
          if (page.messages.length > 0) {
            const newSorted = [...page.messages].sort((a, b) => a.messageSeq - b.messageSeq)
            const newItems: ChatMessageItem[] = newSorted.map((m) => ({ ...m, status: 'SENT' }))
            const maxSeq = newItems.reduce((max, m) => Math.max(max, m.messageSeq), meta.highestSeq)

            setMessagesByConversation((prev) => {
              const currentList = prev[convId] ?? []
              const knownIds = new Set(currentList.map((m) => m.id))
              const knownClientIds = new Set(
                currentList.map((m) => m.clientMessageId).filter(Boolean),
              )
              const nonDuplicate = newItems.filter(
                (m) => !knownIds.has(m.id) && !knownClientIds.has(m.clientMessageId),
              )
              return {
                ...prev,
                [convId]: [...currentList, ...nonDuplicate],
              }
            })

            setHistoryMetadata((prev) => ({
              ...prev,
              [convId]: {
                ...prev[convId],
                highestSeq: maxSeq,
              },
            }))
          }
        } catch (err) {
          console.error(`Failed to catch-up conversation ${convId}:`, err)
        }
      }
    }
  }, [refreshConversations])

  // 11. Process incoming Realtime Events
  const handleRealtimeEvent = useCallback(
    (envelope: ChatEventEnvelope) => {
      const { event, conversationId, data } = envelope

      switch (event) {
        case 'chat.message.created': {
          const message = data as ChatMessageResponse
          // 1. Update message list
          setMessagesByConversation((prev) => {
            const list = prev[conversationId] ?? []
            // Check if matching optimistic message exists
            const optIndex = list.findIndex(
              (m) => m.clientMessageId && m.clientMessageId === message.clientMessageId,
            )
            if (optIndex >= 0) {
              const next = [...list]
              next[optIndex] = { ...message, status: 'SENT' }
              return { ...prev, [conversationId]: next }
            }
            // Check if duplicate canonical id or sequence exists
            if (list.some((m) => m.id === message.id || m.messageSeq === message.messageSeq)) {
              return prev
            }
            return {
              ...prev,
              [conversationId]: [...list, { ...message, status: 'SENT' }],
            }
          })

          // Update highestSeq metadata
          setHistoryMetadata((prev) => {
            const existing = prev[conversationId]
            if (!existing) return prev
            return {
              ...prev,
              [conversationId]: {
                ...existing,
                highestSeq: Math.max(existing.highestSeq, message.messageSeq),
              },
            }
          })

          // 2. Check if conversation is currently actively viewed
          const isPopupOpenAndActive = popupsRef.current.some(
            (p) => p.conversationId === conversationId && !p.minimized,
          )
          const isFullActive = activeConversationIdRef.current === conversationId
          const isViewed = isPopupOpenAndActive || isFullActive

          // Update conversation lastMessage & unread count
          setConversations((prev) => {
            const idx = prev.findIndex((c) => c.conversationId === conversationId)
            const currentUser = profileRef.current
            const isOwnMessage = Boolean(currentUser && message.sender.userId === currentUser.userId)

            if (idx === -1) {
              void refreshConversations()
              const newConv: ChatConversationResponse = {
                conversationId,
                type: 'DIRECT',
                displayName: message.sender.name,
                title: null,
                projectId: null,
                members: [],
                lastMessage: message,
                lastMessageSeq: message.messageSeq,
                lastReadSeq: isOwnMessage ? message.messageSeq : 0,
                unreadCount: isViewed || isOwnMessage ? 0 : 1,
                createdAt: message.createdAt,
                updatedAt: message.createdAt,
              }
              return [newConv, ...prev]
            }
            const conv = prev[idx]
            const newUnread = isViewed || isOwnMessage ? conv.unreadCount : conv.unreadCount + 1
            const updated: ChatConversationResponse = {
              ...conv,
              lastMessage: message,
              lastMessageSeq: message.messageSeq,
              unreadCount: newUnread,
              updatedAt: message.createdAt,
            }
            const next = [...prev]
            next[idx] = updated
            // Re-sort with most recently updated first
            return next.sort(
              (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
            )
          })

          // If viewed, automatically acknowledge read
          if (isViewed && profileRef.current && message.sender.userId !== profileRef.current.userId) {
            void markAsRead(conversationId)
          }
          break
        }

        case 'chat.message.updated': {
          const message = data as ChatMessageResponse
          setMessagesByConversation((prev) => {
            const list = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: list.map((m) =>
                m.id === message.id ? { ...message, status: 'SENT' } : m,
              ),
            }
          })
          break
        }

        case 'chat.message.deleted': {
          const message = data as ChatMessageResponse
          setMessagesByConversation((prev) => {
            const list = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: list.map((m) =>
                m.id === message.id ? { ...message, status: 'SENT' } : m,
              ),
            }
          })
          break
        }

        case 'chat.read.updated': {
          const readData = data as ChatReadResponse
          setConversations((prev) =>
            prev.map((c) => {
              if (c.conversationId !== conversationId) return c
              // If read belongs to current user, update lastReadSeq & clear unreadCount
              if (profileRef.current && readData.userId === profileRef.current.userId) {
                return {
                  ...c,
                  lastReadSeq: Math.max(c.lastReadSeq, readData.lastReadSeq),
                  unreadCount: Math.max(0, c.lastMessageSeq - readData.lastReadSeq),
                }
              }
              // Update member lastReadSeq
              return {
                ...c,
                members: c.members.map((m) =>
                  m.userId === readData.userId
                    ? { ...m, lastReadSeq: Math.max(m.lastReadSeq, readData.lastReadSeq) }
                    : m,
                ),
              }
            }),
          )
          break
        }

        case 'chat.typing.started': {
          const typingData = data as ChatTypingEventData
          if (profileRef.current && typingData.user.userId === profileRef.current.userId) return
          setTypingUsers((prev) => {
            const current = prev[conversationId] ?? []
            const filtered = current.filter((u) => u.userId !== typingData.user.userId)
            return {
              ...prev,
              [conversationId]: [
                ...filtered,
                {
                  userId: typingData.user.userId,
                  name: typingData.user.name,
                  expiresAt: Date.now() + 4000,
                },
              ],
            }
          })
          break
        }

        case 'chat.typing.stopped': {
          const typingData = data as ChatTypingEventData
          setTypingUsers((prev) => {
            const current = prev[conversationId] ?? []
            return {
              ...prev,
              [conversationId]: current.filter((u) => u.userId !== typingData.user.userId),
            }
          })
          break
        }

        case 'chat.conversation.created':
        case 'chat.conversation.updated': {
          void refreshConversations()
          break
        }

        case 'chat.member.added':
        case 'chat.member.removed':
        case 'chat.member.role_changed': {
          void refreshConversations()
          break
        }

        default:
          break
      }
    },
    [markAsRead, refreshConversations],
  )

  // 12. Typing indicators auto-expiration cleanup
  useEffect(() => {
    const timer = setInterval(() => {
      const now = Date.now()
      setTypingUsers((prev) => {
        let changed = false
        const next: Record<string, TypingUser[]> = {}
        for (const [convId, users] of Object.entries(prev)) {
          const valid = users.filter((u) => u.expiresAt > now)
          if (valid.length !== users.length) {
            changed = true
          }
          if (valid.length > 0) {
            next[convId] = valid
          }
        }
        return changed ? next : prev
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  // 13. Socket lifecycle based on authentication
  useEffect(() => {
    if (!isAuthenticated || !token) {
      chatRealtimeClient.disconnect()
      setConnectionState('DISCONNECTED')
      setConversations([])
      setMessagesByConversation({})
      setPopups([])
      setDockItems([])
      return
    }

    setConnectionState('CONNECTING')
    chatRealtimeClient.connect(token)

    const unsubscribeEvent = chatRealtimeClient.onEvent(handleRealtimeEvent)
    const unsubscribeState = chatRealtimeClient.onStateChange((state) => {
      setConnectionState(state)
      if (state === 'CONNECTED') {
        void catchUpAfterReconnect()
      }
    })

    void refreshConversations()

    return () => {
      unsubscribeEvent()
      unsubscribeState()
      chatRealtimeClient.disconnect()
    }
  }, [catchUpAfterReconnect, handleRealtimeEvent, isAuthenticated, refreshConversations, token])

  // Total unread count
  const totalUnreadCount = useMemo(() => {
    return conversations.reduce((acc, c) => acc + Math.max(0, c.unreadCount), 0)
  }, [conversations])

  const hasMoreOlderMessages = useCallback(
    (conversationId: string) => {
      return historyMetadata[conversationId]?.hasMore ?? false
    },
    [historyMetadata],
  )

  const isLoadingOlder = useCallback(
    (conversationId: string) => {
      return historyMetadata[conversationId]?.loadingOlder ?? false
    },
    [historyMetadata],
  )

  const contextValue: ChatContextValue = useMemo(
    () => ({
      conversations,
      activeConversationId,
      popups,
      dockItems,
      activePopupId,
      connectionState,
      totalUnreadCount,
      loadingConversations,
      messagesByConversation,
      typingUsers,
      hasMoreOlderMessages,
      isLoadingOlder,
      refreshConversations,
      loadMessages,
      loadOlderMessages,
      openConversation,
      closePopup,
      minimizePopup,
      focusPopup,
      removeDockItem,
      markAsRead,
      sendMessage,
      retrySendMessage,
      sendTypingSignal,
      createDirectChat,
      createGroupChat,
      renameChatGroup,
      addMemberToGroup,
      removeMemberFromGroup,
      changeMemberRoleInGroup,
      editMessage,
      deleteMessage,
    }),
    [
      conversations,
      activeConversationId,
      popups,
      dockItems,
      activePopupId,
      connectionState,
      totalUnreadCount,
      loadingConversations,
      messagesByConversation,
      typingUsers,
      hasMoreOlderMessages,
      isLoadingOlder,
      refreshConversations,
      loadMessages,
      loadOlderMessages,
      openConversation,
      closePopup,
      minimizePopup,
      focusPopup,
      removeDockItem,
      markAsRead,
      sendMessage,
      retrySendMessage,
      sendTypingSignal,
      createDirectChat,
      createGroupChat,
      renameChatGroup,
      addMemberToGroup,
      removeMemberFromGroup,
      changeMemberRoleInGroup,
      editMessage,
      deleteMessage,
    ],
  )

  return <ChatContext.Provider value={contextValue}>{children}</ChatContext.Provider>
}
