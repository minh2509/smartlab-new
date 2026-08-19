import { AlertTriangle } from 'lucide-react'
import { useEffect, useRef } from 'react'

export type ConfirmDialogOptions = { title: string; description: string; confirmLabel: string; cancelLabel?: string; destructive?: boolean }

export function ConfirmDialog({ title, description, confirmLabel, cancelLabel = 'Hủy', destructive = false, onClose }: ConfirmDialogOptions & { onClose: (confirmed: boolean) => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])
  return <dialog ref={dialogRef} className="confirm-dialog" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-description" onCancel={(event) => { event.preventDefault(); event.stopPropagation(); dialogRef.current?.close('cancel') }} onClose={() => onClose(dialogRef.current?.returnValue === 'confirm')} onClick={(event) => { if (event.target === event.currentTarget) dialogRef.current?.close('cancel') }}>
    <section className="confirm-dialog-content" role="alertdialog">
      <AlertTriangle className={destructive ? 'is-danger' : ''} aria-hidden="true" />
      <div><h2 id="confirm-dialog-title">{title}</h2><p id="confirm-dialog-description">{description}</p></div>
      <footer><button className="btn ghost" type="button" autoFocus onClick={() => dialogRef.current?.close('cancel')}>{cancelLabel}</button><button className={`btn ${destructive ? 'danger' : 'primary'}`} type="button" onClick={() => dialogRef.current?.close('confirm')}>{confirmLabel}</button></footer>
    </section>
  </dialog>
}
