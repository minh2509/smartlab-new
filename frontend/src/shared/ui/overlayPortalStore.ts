import { useSyncExternalStore } from 'react'

let activeTarget: HTMLElement | null = null
const targets: HTMLElement[] = []
const listeners = new Set<() => void>()
const publish = () => listeners.forEach((listener) => listener())

function setActiveTarget() {
  activeTarget = targets.at(-1) ?? null
  publish()
}

/** Registers a portal host and restores the previous host when it unmounts. */
export function registerOverlayPortalTarget(target: HTMLElement) {
  targets.push(target)
  setActiveTarget()
  return () => {
    const index = targets.lastIndexOf(target)
    if (index >= 0) targets.splice(index, 1)
    setActiveTarget()
  }
}

/** @deprecated Use registerOverlayPortalTarget so nested modal hosts restore safely. */
export function setOverlayPortalTarget(target: HTMLElement | null) {
  targets.length = 0
  if (target) targets.push(target)
  setActiveTarget()
}
export function useOverlayPortalTarget() { return useSyncExternalStore((listener) => { listeners.add(listener); return () => listeners.delete(listener) }, () => activeTarget, () => null) }
export function closeOverlayPopups() { window.dispatchEvent(new Event('smartlab:overlay-modal-open')) }
