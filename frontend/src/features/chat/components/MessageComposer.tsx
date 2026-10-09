import { useCallback, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { Loader2, Paperclip, Send, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { uploadFile } from '../../files/api'
import { useChat } from '../chatContext'

type MessageComposerProps = {
  conversationId: string
  placeholder?: string
}

type AttachedFile = {
  fileId: number
  name: string
}

export function MessageComposer({ conversationId, placeholder = 'Nhập tin nhắn...' }: MessageComposerProps) {
  const { token } = useAuth()
  const { sendMessage, sendTypingSignal } = useChat()

  const [text, setText] = useState('')
  const [attachments, setAttachments] = useState<AttachedFile[]>([])
  const [uploading, setUploading] = useState(false)
  const [sending, setSending] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastTypingSignalRef = useRef<number>(0)

  const handleTyping = useCallback(() => {
    const now = Date.now()
    if (now - lastTypingSignalRef.current > 3000) {
      lastTypingSignalRef.current = now
      sendTypingSignal(conversationId, true)
    }

    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current)
    }

    typingTimerRef.current = setTimeout(() => {
      sendTypingSignal(conversationId, false)
      typingTimerRef.current = null
    }, 2500)
  }, [conversationId, sendTypingSignal])

  const handleStopTyping = useCallback(() => {
    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current)
      typingTimerRef.current = null
    }
    sendTypingSignal(conversationId, false)
  }, [conversationId, sendTypingSignal])

  const handleFileUpload = async (e: ChangeEvent<HTMLInputElement>) => {
    if (!token || !e.target.files || e.target.files.length === 0) return
    const files = Array.from(e.target.files)
    setUploading(true)

    try {
      for (const file of files) {
        const uploaded = await uploadFile(token, file, 'LAB', 'Chat attachment')
        setAttachments((prev) => [...prev, { fileId: uploaded.id, name: uploaded.originalName }])
      }
    } catch (err) {
      console.error('File upload failed:', err)
      alert('Không thể tải tệp lên. Vui lòng thử lại.')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const removeAttachment = (fileId: number) => {
    setAttachments((prev) => prev.filter((a) => a.fileId !== fileId))
  }

  const handleSend = async () => {
    const content = text.trim()
    const fileIds = attachments.map((a) => a.fileId)

    if (!content && fileIds.length === 0) return
    if (uploading || sending) return

    handleStopTyping()
    setSending(true)

    try {
      await sendMessage(conversationId, {
        content: content || undefined,
        fileIds: fileIds.length > 0 ? fileIds : undefined,
      })
      setText('')
      setAttachments([])
    } catch (err) {
      console.error('Failed to send message:', err)
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void handleSend()
    }
  }

  const canSend = (text.trim().length > 0 || attachments.length > 0) && !uploading && !sending

  return (
    <div className="chat-composer">
      {attachments.length > 0 && (
        <div className="chat-composer-attachments-preview">
          {attachments.map((att) => (
            <span key={att.fileId} className="chat-composer-chip">
              <span>{att.name}</span>
              <button
                type="button"
                onClick={() => removeAttachment(att.fileId)}
                aria-label={`Xóa đính kèm ${att.name}`}
              >
                <X size={13} aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="chat-composer-row">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          style={{ display: 'none' }}
          onChange={(e) => void handleFileUpload(e)}
        />

        <button
          type="button"
          className="chat-composer-action-btn"
          title="Đính kèm tệp"
          aria-label="Đính kèm tệp"
          disabled={uploading}
          onClick={() => fileInputRef.current?.click()}
        >
          {uploading ? (
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          ) : (
            <Paperclip size={16} aria-hidden="true" />
          )}
        </button>

        <textarea
          className="chat-composer-input"
          placeholder={placeholder}
          rows={1}
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            handleTyping()
          }}
          onKeyDown={handleKeyDown}
          onBlur={handleStopTyping}
          aria-label="Soạn tin nhắn"
        />

        <button
          type="button"
          className="chat-composer-action-btn chat-composer-send-btn"
          disabled={!canSend}
          title="Gửi tin nhắn"
          aria-label="Gửi tin nhắn"
          onClick={() => void handleSend()}
        >
          <Send size={15} aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
