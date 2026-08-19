import { Check, ChevronDown } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useOverlayPortalTarget } from './overlayPortalStore'

export type PopupSelectOption = { value: string; label: string; description?: string }

type PopupSelectProps = {
  value: string
  options: PopupSelectOption[]
  onChange: (value: string) => void
  ariaLabel: string
  disabled?: boolean
  className?: string
}

export function PopupSelect({ value, options, onChange, ariaLabel, disabled = false, className = '' }: PopupSelectProps) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const id = useId()
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() => Math.max(0, options.findIndex((option) => option.value === value)))
  const [position, setPosition] = useState({ top: 0, left: 0, width: 0, maxHeight: 280 })
  const selected = options.find((option) => option.value === value) ?? options[0]
  const portalTarget = useOverlayPortalTarget()

  const updatePosition = useCallback(() => {
    const rect = triggerRef.current?.getBoundingClientRect()
    if (!rect) return
    const margin = 12
    const desiredHeight = Math.min(280, Math.max(120, options.length * 48 + 16))
    const below = window.innerHeight - rect.bottom - margin
    const above = rect.top - margin
    const opensUpward = below < Math.min(desiredHeight, 180) && above > below
    const maxHeight = Math.max(96, Math.min(desiredHeight, opensUpward ? above : below))
    const width = Math.min(Math.max(rect.width, 180), window.innerWidth - margin * 2)
    const left = Math.min(Math.max(margin, rect.left), window.innerWidth - width - margin)
    setPosition({ top: opensUpward ? Math.max(margin, rect.top - maxHeight - 6) : rect.bottom + 6, left, width, maxHeight })
  }, [options.length])

  useEffect(() => {
    if (!open) return
    updatePosition()
    const closeOnPointerDown = (event: PointerEvent) => {
      if (!triggerRef.current?.contains(event.target as Node) && !listRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const reposition = () => updatePosition()
    const closeForModal = () => setOpen(false)
    window.addEventListener('pointerdown', closeOnPointerDown)
    window.addEventListener('resize', reposition)
    window.addEventListener('scroll', reposition, true)
    window.addEventListener('smartlab:overlay-modal-open', closeForModal)
    return () => {
      window.removeEventListener('pointerdown', closeOnPointerDown)
      window.removeEventListener('resize', reposition)
      window.removeEventListener('scroll', reposition, true)
      window.removeEventListener('smartlab:overlay-modal-open', closeForModal)
    }
  }, [open, updatePosition])

  function openList() {
    if (disabled) return
    setActiveIndex(Math.max(0, options.findIndex((option) => option.value === value)))
    setOpen(true)
  }
  function choose(index: number) {
    const option = options[index]
    if (!option) return
    onChange(option.value)
    setOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }
  function onTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openList(); return }
    if (event.key === 'ArrowDown') { event.preventDefault(); openList(); setActiveIndex((current) => Math.min(options.length - 1, current + 1)); return }
    if (event.key === 'ArrowUp') { event.preventDefault(); openList(); setActiveIndex((current) => Math.max(0, current - 1)); return }
    if (event.key === 'Home') { event.preventDefault(); setActiveIndex(0); return }
    if (event.key === 'End') { event.preventDefault(); setActiveIndex(options.length - 1) }
  }
  function onListKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') { event.preventDefault(); setOpen(false); triggerRef.current?.focus(); return }
    if (event.key === 'ArrowDown') { event.preventDefault(); setActiveIndex((current) => Math.min(options.length - 1, current + 1)); return }
    if (event.key === 'ArrowUp') { event.preventDefault(); setActiveIndex((current) => Math.max(0, current - 1)); return }
    if (event.key === 'Home') { event.preventDefault(); setActiveIndex(0); return }
    if (event.key === 'End') { event.preventDefault(); setActiveIndex(options.length - 1); return }
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choose(activeIndex) }
  }

  return <>
    <button ref={triggerRef} className={`popup-select-trigger ${className}`} type="button" disabled={disabled} aria-label={ariaLabel} aria-haspopup="listbox" aria-expanded={open} aria-controls={`${id}-listbox`} onClick={() => open ? setOpen(false) : openList()} onKeyDown={onTriggerKeyDown}>
      <span>{selected?.label}</span><ChevronDown size={16} aria-hidden="true" />
    </button>
    {open && createPortal(
      <div ref={listRef} id={`${id}-listbox`} className="popup-select-menu" role="listbox" aria-label={ariaLabel} tabIndex={-1} style={{ top: position.top, left: position.left, width: position.width, maxHeight: position.maxHeight }} onKeyDown={onListKeyDown}>
        {options.map((option, index) => <button key={option.value} className={`popup-select-option ${index === activeIndex ? 'is-active' : ''}`} type="button" role="option" aria-selected={option.value === value} onMouseMove={() => setActiveIndex(index)} onClick={() => choose(index)}>
          <span><strong>{option.label}</strong>{option.description ? <small>{option.description}</small> : null}</span>{option.value === value ? <Check size={15} aria-hidden="true" /> : null}
        </button>)}
      </div>, portalTarget ?? document.body,
    )}
  </>
}
