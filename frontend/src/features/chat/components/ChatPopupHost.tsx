import { useChat } from '../chatContext'
import { ChatPopupWindow } from './ChatPopupWindow'

export function ChatPopupHost() {
  const { popups, activePopupId } = useChat()

  if (popups.length === 0) return null

  return (
    <aside className="chat-popup-host" aria-label="Các cuộc hội thoại đang mở">
      {popups.map((popup) => (
        <ChatPopupWindow
          key={popup.conversationId}
          popup={popup}
          isActive={activePopupId === popup.conversationId}
        />
      ))}
    </aside>
  )
}
