import { apiClient, toQuery } from '../../lib/apiClient'
import type { MemberProfile } from '../../shared/types/api'
import type {
  AddChatMemberRequest,
  ChangeChatMemberRoleRequest,
  ChatConversationResponse,
  ChatMemberResponse,
  ChatMessagePageResponse,
  ChatMessageResponse,
  ChatMembershipRole,
  ChatReadRequest,
  ChatReadResponse,
  ChatSendMessageRequest,
  CreateDirectChatRequest,
  CreateGroupChatRequest,
  EditChatMessageRequest,
  RenameChatRequest,
} from './types'

export function listConversations(token: string): Promise<ChatConversationResponse[]> {
  return apiClient<ChatConversationResponse[]>('/chat/conversations', { token })
}

export function createDirectConversation(
  token: string,
  userId: string,
): Promise<ChatConversationResponse> {
  const payload: CreateDirectChatRequest = { userId }
  return apiClient<ChatConversationResponse>('/chat/conversations/direct', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function createGroupConversation(
  token: string,
  request: CreateGroupChatRequest,
): Promise<ChatConversationResponse> {
  return apiClient<ChatConversationResponse>('/chat/conversations/group', {
    method: 'POST',
    token,
    body: JSON.stringify(request),
  })
}

export function getConversationMessages(
  token: string,
  conversationId: string,
  options?: { beforeSeq?: number; afterSeq?: number; limit?: number },
): Promise<ChatMessagePageResponse> {
  const query = toQuery({
    beforeSeq: options?.beforeSeq,
    afterSeq: options?.afterSeq,
    limit: options?.limit ?? 50,
  })
  return apiClient<ChatMessagePageResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/messages${query}`,
    { token },
  )
}

export function sendChatMessage(
  token: string,
  conversationId: string,
  request: ChatSendMessageRequest,
): Promise<ChatMessageResponse> {
  return apiClient<ChatMessageResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/messages`,
    {
      method: 'POST',
      token,
      body: JSON.stringify(request),
    },
  )
}

export function markConversationRead(
  token: string,
  conversationId: string,
  lastReadSeq: number,
): Promise<ChatReadResponse> {
  const payload: ChatReadRequest = { lastReadSeq }
  return apiClient<ChatReadResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/read`,
    {
      method: 'PATCH',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function renameConversation(
  token: string,
  conversationId: string,
  title: string,
): Promise<ChatConversationResponse> {
  const payload: RenameChatRequest = { title }
  return apiClient<ChatConversationResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}`,
    {
      method: 'PATCH',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function addConversationMember(
  token: string,
  conversationId: string,
  userId: string,
): Promise<ChatMemberResponse> {
  const payload: AddChatMemberRequest = { userId }
  return apiClient<ChatMemberResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/members`,
    {
      method: 'POST',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function removeConversationMember(
  token: string,
  conversationId: string,
  userId: string,
): Promise<void> {
  return apiClient<void>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/members/${encodeURIComponent(userId)}`,
    {
      method: 'DELETE',
      token,
    },
  )
}

export function changeMemberRole(
  token: string,
  conversationId: string,
  userId: string,
  role: ChatMembershipRole,
): Promise<ChatMemberResponse> {
  const payload: ChangeChatMemberRoleRequest = { role }
  return apiClient<ChatMemberResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/members/${encodeURIComponent(userId)}/role`,
    {
      method: 'PATCH',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function editChatMessage(
  token: string,
  conversationId: string,
  messageId: number,
  content: string,
): Promise<ChatMessageResponse> {
  const payload: EditChatMessageRequest = { content }
  return apiClient<ChatMessageResponse>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}`,
    {
      method: 'PATCH',
      token,
      body: JSON.stringify(payload),
    },
  )
}

export function deleteChatMessage(
  token: string,
  conversationId: string,
  messageId: number,
): Promise<void> {
  return apiClient<void>(
    `/chat/conversations/${encodeURIComponent(conversationId)}/messages/${messageId}`,
    {
      method: 'DELETE',
      token,
    },
  )
}

export function searchLabMembers(token: string, keyword?: string): Promise<MemberProfile[]> {
  const query = toQuery({ keyword: keyword?.trim() || undefined })
  return apiClient<MemberProfile[]>(`/members${query}`, { token })
}
