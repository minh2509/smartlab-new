import { MoreHorizontal } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useOverlayPortalTarget } from './overlayPortalStore'

export type ActionMenuItem = {
  key: string
  label: string
  icon?: ReactNode
  danger?: boolean
  disabled?: boolean
  onClick?: () => void
  href?: string
  target?: string
  rel?: string
}

export type ActionMenuProps = {
  triggerAriaLabel: string
  items: ActionMenuItem[]
  className?: string
  disabled?: boolean
  icon?: ReactNode
  align?: 'left' | 'right'
}

export function ActionMenu({
  triggerAriaLabel,
  items,
  className = '',
  disabled = false,
  icon,
  align = 'right',
}: ActionMenuProps) {
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const itemRefs = useRef<Array<HTMLButtonElement | HTMLAnchorElement | null>>([])
  const id = useId()
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState({ top: 0, left: 0, width: 200, maxHeight: 300 })
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
    const menuWidth = Math.min(220, window.innerWidth - margin * 2)
    const desiredHeight = items.length * 48 + 16
    const below = window.innerHeight - rect.bottom - margin
    const above = rect.top - margin
    const opensUpward = below < Math.min(desiredHeight, 160) && above > below
    const maxHeight = Math.max(96, Math.min(desiredHeight, opensUpward ? above : below))

    let left = align === 'left' ? rect.left : rect.right - menuWidth
    left = Math.min(Math.max(margin, left), window.innerWidth - menuWidth - margin)
    const top = opensUpward ? Math.max(margin, rect.top - maxHeight - 6) : rect.bottom + 6

    setPosition({
      top,
      left,
      width: menuWidth,
      maxHeight,
    })
  }, [align, items.length])

  function getFirstEnabledIndex(): number {
    const idx = items.findIndex((it) => !it.disabled)
    return idx >= 0 ? idx : 0
  }

  function getLastEnabledIndex(): number {
    for (let i = items.length - 1; i >= 0; i--) {
      if (!items[i]?.disabled) return i
    }
    return 0
  }

  function getNextIndex(current: number, direction: 1 | -1): number {
    if (items.length === 0) return 0
    let next = (current + direction + items.length) % items.length
    let attempts = 0
    while (items[next]?.disabled && attempts < items.length) {
      next = (next + direction + items.length) % items.length
      attempts++
    }
    return next
  }

  useEffect(() => {
    if (!open) return
    updatePosition()
    const closeOnPointerDown = (event: PointerEvent) => {
      if (
        !triggerRef.current?.contains(event.target as Node) &&
        !menuRef.current?.contains(event.target as Node)
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

  // Focus first item when opening
  useEffect(() => {
    if (open) {
      window.requestAnimationFrame(() => {
        const firstIdx = items.findIndex((it) => !it.disabled)
        const validIdx = firstIdx >= 0 ? firstIdx : 0
        itemRefs.current[validIdx]?.focus()
      })
    }
  }, [items, open])

  function handleTriggerClick() {
    if (disabled) return
    setOpen((prev) => !prev)
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      window.requestAnimationFrame(() => {
        const lastIdx = getLastEnabledIndex()
        itemRefs.current[lastIdx]?.focus()
      })
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const currentIndex = itemRefs.current.findIndex(
      (el) => el === document.activeElement,
    )

    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      triggerRef.current?.focus()
      return
    }

    if (event.key === 'Tab') {
      setOpen(false)
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      const next = getNextIndex(currentIndex >= 0 ? currentIndex : 0, 1)
      itemRefs.current[next]?.focus()
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      const prev = getNextIndex(
        currentIndex >= 0 ? currentIndex : items.length - 1,
        -1,
      )
      itemRefs.current[prev]?.focus()
      return
    }

    if (event.key === 'Home') {
      event.preventDefault()
      const first = getFirstEnabledIndex()
      itemRefs.current[first]?.focus()
      return
    }

    if (event.key === 'End') {
      event.preventDefault()
      const last = getLastEnabledIndex()
      itemRefs.current[last]?.focus()
    }
  }

  function handleItemClick(item: ActionMenuItem) {
    if (item.disabled) return
    setOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
    item.onClick?.()
  }

  return (
    <div className="action-menu">
      <button
        ref={triggerRef}
        type="button"
        className={`action-menu-trigger ${className}`}
        disabled={disabled}
        aria-label={triggerAriaLabel}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={`${id}-menu`}
        onClick={handleTriggerClick}
        onKeyDown={handleTriggerKeyDown}
        title={triggerAriaLabel}
      >
        {icon || <MoreHorizontal aria-hidden="true" size={20} strokeWidth={2.2} />}
      </button>

      {open &&
        createPortal(
          <div
            ref={menuRef}
            id={`${id}-menu`}
            className="action-menu-dropdown"
            role="menu"
            aria-label={triggerAriaLabel}
            tabIndex={-1}
            style={{
              top: position.top,
              left: position.left,
              width: position.width,
              maxHeight: position.maxHeight,
            }}
            onKeyDown={handleMenuKeyDown}
          >
            {items.map((item, index) => {
              const itemClass = `action-menu-item ${item.danger ? 'danger' : ''} ${item.disabled ? 'is-disabled' : ''}`

              if (item.href) {
                return (
                  <a
                    key={item.key}
                    ref={(node) => {
                      itemRefs.current[index] = node
                    }}
                    href={item.href}
                    target={item.target}
                    rel={item.rel}
                    className={itemClass}
                    role="menuitem"
                    aria-disabled={item.disabled}
                    tabIndex={item.disabled ? -1 : 0}
                    onClick={(e) => {
                      if (item.disabled) {
                        e.preventDefault()
                        return
                      }
                      setOpen(false)
                      item.onClick?.()
                    }}
                  >
                    {item.icon}
                    <span>{item.label}</span>
                  </a>
                )
              }

              return (
                <button
                  key={item.key}
                  ref={(node) => {
                    itemRefs.current[index] = node
                  }}
                  type="button"
                  className={itemClass}
                  role="menuitem"
                  disabled={item.disabled}
                  aria-disabled={item.disabled}
                  onClick={() => handleItemClick(item)}
                >
                  {item.icon}
                  <span>{item.label}</span>
                </button>
              )
            })}
          </div>,
          getTarget(),
        )}
    </div>
  )
}
