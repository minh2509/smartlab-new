import { useChat } from '../chatContext'
import { ChatHead } from './ChatHead'

export function ChatDock() {
  const { dockItems } = useChat()

  if (dockItems.length === 0) return null

  return (
    <aside className="chat-dock" aria-label="Lối tắt chat">
      {dockItems.map((item) => (
        <ChatHead key={item.conversationId} item={item} />
      ))}
    </aside>
  )
}
