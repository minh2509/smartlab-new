import { Client, type IMessage, type StompHeaders } from '@stomp/stompjs'
import type {
  ChatEventEnvelope,
  ChatReadRequest,
  ChatSendMessageRequest,
  ChatTypingRequest,
} from './types'

export function getChatWebSocketUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  try {
    const url = new URL(base, typeof window !== 'undefined' ? window.location.href : 'http://localhost:8080')
    const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    const pathname = url.pathname.replace(/\/+$/, '') + '/ws/chat'
    return `${protocol}//${url.host}${pathname}`
  } catch {
    return 'ws://localhost:8080/api/v1.0/ws/chat'
  }
}

export type RealtimeEventHandler = (envelope: ChatEventEnvelope) => void
export type RealtimeStateListener = (state: 'CONNECTED' | 'DISCONNECTED' | 'ERROR') => void

export class ChatRealtimeClient {
  private client: Client | null = null
  private eventHandlers: Set<RealtimeEventHandler> = new Set()
  private stateListeners: Set<RealtimeStateListener> = new Set()
  private activeToken: string | null = null
  private userSubscription: { unsubscribe: () => void } | null = null

  get isConnected(): boolean {
    return this.client?.connected ?? false
  }

  onEvent(handler: RealtimeEventHandler): () => void {
    this.eventHandlers.add(handler)
    return () => {
      this.eventHandlers.delete(handler)
    }
  }

  onStateChange(listener: RealtimeStateListener): () => void {
    this.stateListeners.add(listener)
    return () => {
      this.stateListeners.delete(listener)
    }
  }

  private notifyState(state: 'CONNECTED' | 'DISCONNECTED' | 'ERROR') {
    this.stateListeners.forEach((listener) => {
      try {
        listener(state)
      } catch (err) {
        console.error('Error in state listener:', err)
      }
    })
  }

  private notifyEvent(envelope: ChatEventEnvelope) {
    this.eventHandlers.forEach((handler) => {
      try {
        handler(envelope)
      } catch (err) {
        console.error('Error in event handler:', err)
      }
    })
  }

  connect(token: string) {
    if (this.client?.active && this.activeToken === token) {
      return
    }

    this.disconnect()
    this.activeToken = token

    const brokerURL = getChatWebSocketUrl()
    const client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {
        // debug logging disabled in production
      },
      onConnect: () => {
        this.notifyState('CONNECTED')
        this.userSubscription = client.subscribe('/user/queue/chat', (message: IMessage) => {
          try {
            const envelope = JSON.parse(message.body) as ChatEventEnvelope
            this.notifyEvent(envelope)
          } catch (err) {
            console.error('Failed to parse chat event frame:', err, message.body)
          }
        })
      },
      onDisconnect: () => {
        this.notifyState('DISCONNECTED')
      },
      onStompError: (frame) => {
        console.warn('STOMP error frame:', frame)
        this.notifyState('ERROR')
      },
      onWebSocketError: (event) => {
        console.warn('WebSocket error:', event)
        this.notifyState('ERROR')
      },
      onWebSocketClose: () => {
        this.notifyState('DISCONNECTED')
      },
    })

    this.client = client
    client.activate()
  }

  disconnect() {
    if (this.userSubscription) {
      try {
        this.userSubscription.unsubscribe()
      } catch {
        // ignore
      }
      this.userSubscription = null
    }

    if (this.client) {
      try {
        this.client.deactivate()
      } catch {
        // ignore
      }
      this.client = null
    }
    this.activeToken = null
    this.notifyState('DISCONNECTED')
  }

  sendMessage(conversationId: string, request: ChatSendMessageRequest, headers?: StompHeaders): boolean {
    if (!this.client?.connected) return false
    this.client.publish({
      destination: `/app/chat/${conversationId}/messages`,
      body: JSON.stringify(request),
      headers,
    })
    return true
  }

  sendRead(conversationId: string, request: ChatReadRequest): boolean {
    if (!this.client?.connected) return false
    this.client.publish({
      destination: `/app/chat/${conversationId}/read`,
      body: JSON.stringify(request),
    })
    return true
  }

  sendTyping(conversationId: string, request: ChatTypingRequest): boolean {
    if (!this.client?.connected) return false
    this.client.publish({
      destination: `/app/chat/${conversationId}/typing`,
      body: JSON.stringify(request),
    })
    return true
  }
}

export const chatRealtimeClient = new ChatRealtimeClient()
