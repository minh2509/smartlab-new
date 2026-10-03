import { Check, ChevronDown } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useOverlayPortalTarget } from './overlayPortalStore'

export type PopupSelectOption = {
  value: string
  label: string
  description?: string
  disabled?: boolean
}

type PopupSelectProps = {
  value: string
  options: PopupSelectOption[]
  onChange: (value: string) => void
  ariaLabel: string
  disabled?: boolean
  className?: string
}

export function PopupSelect({
  value,
  options,
  onChange,
  ariaLabel,
  disabled = false,
  className = '',
}: PopupSelectProps) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const id = useId()
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() => {
    const idx = options.findIndex((opt) => opt.value === value)
    return idx >= 0 ? idx : 0
  })
  const [position, setPosition] = useState({ top: 0, left: 0, width: 0, maxHeight: 280 })
  const selected = options.find((opt) => opt.value === value) ?? options[0]
  const portalTargetFromStore = useOverlayPortalTarget()

  const getTarget = useCallback(() => {
    if (portalTargetFromStore) return portalTargetFromStore
    const dialog = triggerRef.current?.closest('dialog')
    return dialog ?? document.body
  }, [portalTargetFromStore])

  const updatePosition = useCallback(() => {
    const trigger = triggerRef.current
    if (!trigger) return
    const rect = trigger.getBoundingClientRect()

    const margin = 12
    const desiredHeight = Math.min(280, Math.max(120, options.length * 48 + 16))
    const below = window.innerHeight - rect.bottom - margin
    const above = rect.top - margin
    const opensUpward = below < Math.min(desiredHeight, 180) && above > below
    const maxHeight = Math.max(96, Math.min(desiredHeight, opensUpward ? above : below))
    const width = Math.min(Math.max(rect.width, 280), window.innerWidth - margin * 2)
    const leftViewport = Math.min(Math.max(margin, rect.left), window.innerWidth - width - margin)
    const topViewport = opensUpward ? Math.max(margin, rect.top - maxHeight - 6) : rect.bottom + 6

    setPosition({
      top: topViewport,
      left: leftViewport,
      width,
      maxHeight,
    })
  }, [options.length])

  useEffect(() => {
    if (!open) return
    updatePosition()
    const closeOnPointerDown = (event: PointerEvent) => {
      if (
        !triggerRef.current?.contains(event.target as Node) &&
        !listRef.current?.contains(event.target as Node)
      ) {
        setOpen(false)
      }
    }
    const closeOnKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    const reposition = () => updatePosition()
    const closeForModal = () => setOpen(false)

    window.addEventListener('pointerdown', closeOnPointerDown)
    window.addEventListener('keydown', closeOnKeyDown)
    window.addEventListener('resize', reposition)
    window.addEventListener('scroll', reposition, true)
    window.addEventListener('smartlab:overlay-modal-open', closeForModal)
    return () => {
      window.removeEventListener('pointerdown', closeOnPointerDown)
      window.removeEventListener('keydown', closeOnKeyDown)
      window.removeEventListener('resize', reposition)
      window.removeEventListener('scroll', reposition, true)
      window.removeEventListener('smartlab:overlay-modal-open', closeForModal)
    }
  }, [open, updatePosition])

  // Scroll active option into view when activeIndex changes
  useEffect(() => {
    if (open && activeIndex >= 0 && optionRefs.current[activeIndex]) {
      optionRefs.current[activeIndex]?.scrollIntoView({ block: 'nearest' })
    }
  }, [activeIndex, open])

  function getNextIndex(current: number, direction: 1 | -1): number {
    if (options.length === 0) return 0
    let next = current + direction
    while (next >= 0 && next < options.length) {
      if (!options[next]?.disabled) return next
      next += direction
    }
    return current
  }

  function getFirstEnabledIndex(): number {
    const idx = options.findIndex((opt) => !opt.disabled)
    return idx >= 0 ? idx : 0
  }

  function getLastEnabledIndex(): number {
    for (let i = options.length - 1; i >= 0; i--) {
      if (!options[i]?.disabled) return i
    }
    return 0
  }

  function openList() {
    if (disabled) return
    const currentIdx = options.findIndex((opt) => opt.value === value)
    setActiveIndex(
      currentIdx >= 0 && !options[currentIdx]?.disabled
        ? currentIdx
        : getFirstEnabledIndex(),
    )
    setOpen(true)
  }

  function choose(index: number) {
    const option = options[index]
    if (!option || option.disabled) return
    onChange(option.value)
    setOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  function onTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      if (open) {
        choose(activeIndex)
      } else {
        openList()
      }
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!open) {
        openList()
      } else {
        setActiveIndex((curr) => getNextIndex(curr, 1))
      }
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      if (!open) {
        openList()
      } else {
        setActiveIndex((curr) => getNextIndex(curr, -1))
      }
      return
    }
    if (event.key === 'Home' && open) {
      event.preventDefault()
      setActiveIndex(getFirstEnabledIndex())
      return
    }
    if (event.key === 'End' && open) {
      event.preventDefault()
      setActiveIndex(getLastEnabledIndex())
      return
    }
    if (event.key === 'Tab' && open) {
      setOpen(false)
    }
  }

  function onListKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      triggerRef.current?.focus()
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((curr) => getNextIndex(curr, 1))
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((curr) => getNextIndex(curr, -1))
      return
    }
    if (event.key === 'Home') {
      event.preventDefault()
      setActiveIndex(getFirstEnabledIndex())
      return
    }
    if (event.key === 'End') {
      event.preventDefault()
      setActiveIndex(getLastEnabledIndex())
      return
    }
    if (event.key === 'Tab') {
      setOpen(false)
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      choose(activeIndex)
    }
  }

  return (
    <>
      <button
        ref={triggerRef}
        className={`popup-select-trigger ${className}`}
        type="button"
        disabled={disabled}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${id}-listbox`}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={onTriggerKeyDown}
        title={selected?.label}
      >
        <span>{selected?.label}</span>
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {open &&
        createPortal(
          <div
            ref={listRef}
            id={`${id}-listbox`}
            className="popup-select-menu"
            role="listbox"
            aria-label={ariaLabel}
            tabIndex={-1}
            style={{
              top: position.top,
              left: position.left,
              width: position.width,
              maxHeight: position.maxHeight,
            }}
            onKeyDown={onListKeyDown}
          >
            {options.map((option, index) => (
              <button
                key={option.value}
                ref={(node) => {
                  optionRefs.current[index] = node
                }}
                className={`popup-select-option ${index === activeIndex ? 'is-active' : ''} ${option.disabled ? 'is-disabled' : ''}`}
                type="button"
                role="option"
                disabled={option.disabled}
                aria-disabled={option.disabled}
                aria-selected={option.value === value}
                onMouseMove={() => {
                  if (!option.disabled) setActiveIndex(index)
                }}
                onClick={() => choose(index)}
                title={option.label}
              >
                <span>
                  <strong>{option.label}</strong>
                  {option.description ? <small>{option.description}</small> : null}
                </span>
                {option.value === value ? <Check size={15} aria-hidden="true" /> : null}
              </button>
            ))}
          </div>,
          getTarget(),
        )}
    </>
  )
}
