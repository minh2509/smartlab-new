import { useEffect, useRef } from 'react'
import { registerOverlayPortalTarget } from './overlayPortalStore'

/** Registers one top-layer modal host for popup and toast portals. */
export function OverlayPortalHost() {
  const hostRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    return registerOverlayPortalTarget(host)
  }, [])
  return <div className="overlay-portal-host" ref={hostRef} />
}
