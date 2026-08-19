import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties, PropsWithChildren } from 'react'
import { createPortal } from 'react-dom'
import { useOverlayPortalTarget } from '../ui/overlayPortalStore'
import type { Toast, ToastApi, ToastOptions, ToastTone } from './types'
import { ToastContext } from './ToastContext'

const TOAST_DURATION = {
  success: 4500,
  info: 4500,
} as const

export function ToastProvider({ children }: PropsWithChildren) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(0)
  const portalTarget = useOverlayPortalTarget()
  const laneStyle = useToastLane(portalTarget)

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const show = useCallback((tone: ToastTone, title: string, message?: string, options?: ToastOptions) => {
    const id = `toast-${++nextId.current}`
    const duration = options?.duration ?? (tone === 'error' ? undefined : TOAST_DURATION[tone])
    setToasts((current) => [...current, { id, tone, title, message, duration }].slice(-5))
  }, [])

  const toast = useMemo<ToastApi>(() => ({
    success: (title, message, options) => show('success', title, message, options),
    error: (title, message, options) => show('error', title, message, options),
    info: (title, message, options) => show('info', title, message, options),
  }), [show])

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {portalTarget
        ? createPortal(<ToastViewport toasts={toasts} onDismiss={dismiss} style={laneStyle} topLayer />, portalTarget)
        : <ToastViewport toasts={toasts} onDismiss={dismiss} style={laneStyle} />}
    </ToastContext.Provider>
  )
}

function useToastLane(portalTarget: HTMLElement | null) {
  const [style, setStyle] = useState<CSSProperties>({ '--toast-lane-top': '86px', '--toast-lane-right': '20px' } as CSSProperties)
  useEffect(() => {
    const update = () => {
      const anchor = document.querySelector<HTMLElement>('.admin-content')
      if (!anchor) return
      const rect = anchor.getBoundingClientRect()
      const computed = window.getComputedStyle(anchor)
      setStyle({
        '--toast-lane-top': `${Math.max(16, rect.top + Number.parseFloat(computed.paddingTop))}px`,
        '--toast-lane-right': `${Math.max(16, window.innerWidth - rect.right + Number.parseFloat(computed.paddingRight))}px`,
      } as CSSProperties)
    }
    update()
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true) }
  }, [portalTarget])
  return style
}

function ToastViewport({ toasts, onDismiss, style, topLayer = false }: { toasts: Toast[]; onDismiss: (id: string) => void; style: CSSProperties; topLayer?: boolean }) {
  const viewportRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const viewport = viewportRef.current
    if (!topLayer || !viewport || !('showPopover' in viewport)) return
    const isOpen = () => viewport.matches(':popover-open')
    try {
      if (toasts.length && !isOpen()) viewport.showPopover()
      if (!toasts.length && isOpen()) viewport.hidePopover()
    } catch {
      // The editor can close while a toast timer is settling; the normal DOM cleanup is sufficient.
    }
    return () => {
      try { if (isOpen()) viewport.hidePopover() } catch { /* already detached */ }
    }
  }, [topLayer, toasts.length])

  const content = toasts.map((toastItem) => <ToastItem key={toastItem.id} toast={toastItem} onDismiss={onDismiss} />)
  return topLayer
    ? <div ref={viewportRef} className="toast-viewport toast-viewport-top-layer" style={style} popover="manual" aria-label="Thông báo thao tác">{content}</div>
    : <div className="toast-viewport" style={style} aria-label="Thông báo thao tác">{content}</div>
}

function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: string) => void }) {
  const [closing, setClosing] = useState(false)
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const dismiss = useCallback(() => {
    if (closing || closeTimer.current) return
    setClosing(true)
    closeTimer.current = setTimeout(() => onDismiss(toast.id), 140)
  }, [closing, onDismiss, toast.id])

  useEffect(() => {
    if (!toast.duration) return
    timer.current = setTimeout(dismiss, toast.duration)
    return () => {
      if (timer.current) clearTimeout(timer.current)
      timer.current = null
    }
  }, [dismiss, toast.duration])

  useEffect(() => () => {
    if (closeTimer.current) clearTimeout(closeTimer.current)
  }, [])

  const Icon = toast.tone === 'success' ? CheckCircle2 : toast.tone === 'error' ? AlertCircle : Info

  return (
    <section className={`toast toast-${toast.tone}${closing ? ' is-closing' : ''}`} role={toast.tone === 'error' ? 'alert' : 'status'} aria-live={toast.tone === 'error' ? 'assertive' : 'polite'} aria-atomic="true">
      <Icon className="toast-icon" aria-hidden="true" />
      <div className="toast-copy">
        <strong>{toast.title}</strong>
        {toast.message ? <span>{toast.message}</span> : null}
      </div>
      <button className="toast-dismiss" type="button" aria-label={`Đóng thông báo: ${toast.title}`} onClick={dismiss}>
        <X aria-hidden="true" />
      </button>
    </section>
  )
}
