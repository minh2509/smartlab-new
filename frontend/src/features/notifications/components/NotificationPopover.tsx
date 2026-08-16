import { useEffect, useRef, useState } from 'react'
import { Bell, CheckCheck } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/authContext'
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '../api'
import type { Notification } from '../types'

type PendingAction = `read:${number}` | 'read-all'
type SafeTarget = { kind: 'internal' | 'external'; url: string }

export function NotificationPopover() {
  const { token, profile } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const rootRef = useRef<HTMLDivElement>(null)
  const [isOpen, setOpen] = useState(false)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [error, setError] = useState<string | null>(null)

  const canMarkRead = profile?.permissions.includes('notifications.mark_read_own') ?? false
  const unreadCount = notifications.filter((notification) => !notification.isRead).length

  useEffect(() => {
    if (!token) {
      setNotifications([])
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    void listNotifications(token)
      .then((result) => {
        if (active) setNotifications(result)
      })
      .catch((value: unknown) => {
        if (active) setError(value instanceof Error ? value.message : 'Không thể tải thông báo.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [token])

  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!isOpen) return
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen])

  async function markAllRead() {
    if (!token || pending || !canMarkRead || unreadCount === 0) return
    setPending('read-all')
    setError(null)
    try {
      await markAllNotificationsRead(token)
      setNotifications((current) => current.map((notification) => ({ ...notification, isRead: true })))
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể cập nhật thông báo.')
    } finally {
      setPending(null)
    }
  }

  async function openNotification(notification: Notification) {
    if (!token || pending) return
    const target = safeTarget(notification.targetUrl)
    if (!notification.isRead && canMarkRead) {
      setPending(`read:${notification.id}`)
      setError(null)
      try {
        await markNotificationRead(token, notification.id)
        setNotifications((current) => current.map((item) =>
          item.id === notification.id ? { ...item, isRead: true } : item))
      } catch (value) {
        setError(value instanceof Error ? value.message : 'Không thể đánh dấu thông báo là đã đọc.')
        setPending(null)
        return
      }
      setPending(null)
    }

    if (!target) return
    setOpen(false)
    if (target.kind === 'internal') {
      navigate(target.url)
    } else {
      window.location.assign(target.url)
    }
  }

  return (
    <div className="notification-popover" ref={rootRef}>
      <button
        className={`icon-btn notification-popover-trigger${isOpen ? ' is-active' : ''}`}
        type="button"
        aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-controls="notification-popover-panel"
        title="Thông báo"
        onClick={() => setOpen((value) => !value)}
      >
        <Bell aria-hidden="true" />
        {unreadCount > 0 ? (
          <span className="notification-count" aria-hidden="true">{unreadCount > 99 ? '99+' : unreadCount}</span>
        ) : null}
      </button>

      {isOpen ? (
        <section
          className="notification-popover-panel"
          id="notification-popover-panel"
          role="dialog"
          aria-label="Thông báo"
        >
          <header className="notification-popover-head">
            <div>
              <h2>Thông báo</h2>
              <p>{unreadCount > 0 ? `${unreadCount} chưa đọc` : 'Không có thông báo mới'}</p>
            </div>
            {canMarkRead && unreadCount > 0 ? (
              <button
                className="notification-mark-all"
                type="button"
                disabled={Boolean(pending)}
                onClick={() => void markAllRead()}
              >
                <CheckCheck aria-hidden="true" />
                {pending === 'read-all' ? 'Đang cập nhật...' : 'Đánh dấu tất cả đã đọc'}
              </button>
            ) : null}
          </header>

          {error ? <p className="notification-popover-error" role="alert">{error}</p> : null}
          {loading ? <p className="notification-popover-state" aria-busy="true">Đang tải thông báo...</p> : null}
          {!loading && notifications.length === 0 ? (
            <p className="notification-popover-state">Chưa có thông báo dành cho bạn.</p>
          ) : null}

          {!loading && notifications.length > 0 ? (
            <div className="notification-popover-list" aria-label="Danh sách thông báo">
              {notifications.map((notification) => {
                const target = safeTarget(notification.targetUrl)
                const isInteractive = Boolean(target) || (!notification.isRead && canMarkRead)
                const content = (
                  <>
                    <span className="notification-unread-indicator" aria-hidden="true" />
                    <span className="notification-popover-copy">
                      <span className="notification-popover-meta">
                        {notification.actorName ? <strong>{notification.actorName}</strong> : <span />}
                        <time dateTime={notification.createdAt}>{formatDateTime(notification.createdAt)}</time>
                      </span>
                      <span className="notification-popover-message">{notification.message}</span>
                    </span>
                  </>
                )
                return isInteractive ? (
                  <button
                    className={`notification-popover-row${notification.isRead ? '' : ' is-unread'}`}
                    type="button"
                    disabled={Boolean(pending)}
                    aria-label={notification.message}
                    key={notification.id}
                    onClick={() => void openNotification(notification)}
                  >
                    {content}
                  </button>
                ) : (
                  <div className="notification-popover-row" key={notification.id}>{content}</div>
                )
              })}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}

function safeTarget(targetUrl: string | null): SafeTarget | null {
  if (!targetUrl) return null
  if (targetUrl.startsWith('/') && !targetUrl.startsWith('//')) {
    return { kind: 'internal', url: targetUrl }
  }
  try {
    const url = new URL(targetUrl)
    if (url.protocol === 'http:' || url.protocol === 'https:') {
      return { kind: 'external', url: url.toString() }
    }
  } catch {
    return null
  }
  return null
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
