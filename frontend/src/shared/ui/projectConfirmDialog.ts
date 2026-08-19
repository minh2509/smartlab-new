import { createElement } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfirmDialog } from './ConfirmDialog'
import type { ConfirmDialogOptions } from './ConfirmDialog'
import { closeOverlayPopups } from './overlayPortalStore'

/** A portal-backed confirmation surface safe to invoke from native dialogs. */
export function confirmDialog(options: ConfirmDialogOptions): Promise<boolean> {
  return new Promise((resolve) => {
    closeOverlayPopups()
    const host = document.createElement('div')
    document.body.append(host)
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const root = createRoot(host)
    const finish = (confirmed: boolean) => {
      root.unmount()
      host.remove()
      resolve(confirmed)
      window.requestAnimationFrame(() => previousFocus?.isConnected && previousFocus.focus())
    }
    root.render(createElement(ConfirmDialog, { ...options, onClose: finish }))
  })
}
