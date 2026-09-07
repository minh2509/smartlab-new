import { X } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'
import type { FormEvent, KeyboardEvent, ReactNode, RefObject } from 'react'

type AdminContentDialogProps = {
  open: boolean
  title: string
  description: string
  submitLabel: string
  busy: boolean
  triggerRef: RefObject<HTMLButtonElement | null>
  onClose: () => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  children: ReactNode
}

export function AdminContentDialog({
  open,
  title,
  description,
  submitLabel,
  busy,
  triggerRef,
  onClose,
  onSubmit,
  children,
}: AdminContentDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const closeRequestedRef = useRef(false)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!open) {
      closeRequestedRef.current = false
      return
    }

    closeRequestedRef.current = false
    if (dialog && !dialog.open) dialog.showModal()
  }, [open])

  if (!open) return null

  function close() {
    if (busy || closeRequestedRef.current) return
    closeRequestedRef.current = true
    onClose()
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key !== 'Escape') return
    event.preventDefault()
    event.stopPropagation()
    close()
  }

  return (
    <dialog
      ref={dialogRef}
      className="admin-content-dialog"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => { event.preventDefault(); close() }}
      onKeyDown={handleKeyDown}
      onClick={(event) => { if (event.target === event.currentTarget) close() }}
    >
      <form className="admin-content-dialog-form" onSubmit={onSubmit} noValidate>
        <header className="admin-content-dialog-head">
          <div>
            <span className="eyebrow">Không gian quản trị</span>
            <h2 id={titleId}>{title}</h2>
            <p id={descriptionId}>{description}</p>
          </div>
          <button className="admin-content-dialog-close" type="button" aria-label={`Đóng ${title}`} onClick={close} disabled={busy}>
            <X aria-hidden="true" />
          </button>
        </header>
        <div className="admin-content-dialog-body">{children}</div>
        <footer className="admin-content-dialog-actions">
          <button className="btn ghost" type="button" onClick={close} disabled={busy}>Hủy</button>
          <button className="btn primary" type="submit" disabled={busy}>
            {busy ? 'Đang lưu...' : submitLabel}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
