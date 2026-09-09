import { useEffect, useMemo, useState } from 'react'
import type { OSMode } from '../types'
import { soundSynth } from '../audio'

interface MenuModeProps {
  onSelectMode: (mode: OSMode) => void
  soundEnabled: boolean
  onToggleSound: () => void
  onClose: () => void
}

interface MenuItem {
  id: string
  keyNum: string
  label: string
  action: () => void
  badge?: string
}

export function MenuMode({ onSelectMode, soundEnabled, onToggleSound, onClose }: MenuModeProps) {
  const [selectedIndex, setSelectedIndex] = useState(0)

  const items: MenuItem[] = useMemo(() => [
    {
      id: 'terminal',
      keyNum: '1',
      label: 'API WORKFLOW (RESEARCH TERMINAL)',
      action: () => onSelectMode('TERMINAL'),
      badge: 'DEFAULT',
    },
    {
      id: 'diagnostics',
      keyNum: '2',
      label: 'SYSTEM DIAGNOSTICS & TELEMETRY',
      action: () => onSelectMode('DIAGNOSTICS'),
    },
    {
      id: 'arcade',
      keyNum: '3',
      label: 'ARCADE DISK (4 MINI-GAMES)',
      action: () => onSelectMode('ARCADE'),
      badge: 'GAMES',
    },
    {
      id: 'achievements',
      keyNum: '4',
      label: 'LAB RECORDS & ACHIEVEMENTS',
      action: () => onSelectMode('ACHIEVEMENTS'),
    },
    {
      id: 'audio',
      keyNum: '5',
      label: `AUDIO SYNTH: [${soundEnabled ? 'ACTIVE (ON)' : 'MUTED (OFF)'}]`,
      action: onToggleSound,
      badge: soundEnabled ? 'LOUD' : 'MUTE',
    },
    {
      id: 'reboot',
      keyNum: '6',
      label: 'COLD REBOOT (BIOS POST SEQUENCE)',
      action: () => onSelectMode('BOOT'),
    },
  ], [onSelectMode, soundEnabled, onToggleSound])

  // Keyboard navigation when menu is open
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Direct number key shortcuts 1-6
      const num = parseInt(e.key, 10)
      if (num >= 1 && num <= items.length) {
        e.preventDefault()
        soundSynth.playSelect()
        items[num - 1].action()
        return
      }

      if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        e.preventDefault()
        soundSynth.playKey('normal')
        setSelectedIndex((prev) => (prev + 1) % items.length)
      } else if (e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        soundSynth.playKey('normal')
        setSelectedIndex((prev) => (prev - 1 + items.length) % items.length)
      } else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        soundSynth.playSelect()
        items[selectedIndex].action()
      } else if (e.key === 'Escape') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [items, selectedIndex, onClose])

  return (
    <div className="sl-ws-menu-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-orange" />
          <span>SMARTLAB WORKSTATION OS // v2.4</span>
        </div>
        <button
          type="button"
          className="sl-ws-header-btn"
          onClick={onClose}
          title="Return to Terminal"
        >
          CLOSE [ESC]
        </button>
      </div>

      <div className="sl-ws-menu-title">
        === MAIN SYSTEM CONTROL MENU ===
      </div>

      <div className="sl-ws-menu-list" role="menu">
        {items.map((item, index) => {
          const isSelected = index === selectedIndex
          return (
            <button
              key={item.id}
              type="button"
              role="menuitem"
              className={`sl-ws-menu-item ${isSelected ? 'is-selected' : ''}`}
              onClick={() => {
                soundSynth.playSelect()
                item.action()
              }}
              onMouseEnter={() => setSelectedIndex(index)}
            >
              <span className="sl-menu-key">[{item.keyNum}]</span>
              <span className="sl-menu-cursor">{isSelected ? '►' : ' '}</span>
              <span className="sl-menu-label">{item.label}</span>
              {item.badge && <span className="sl-menu-badge">{item.badge}</span>}
            </button>
          )
        })}
      </div>

      <div className="sl-ws-menu-footer">
        <span>USE [▲/▼] NAVIGATE</span>
        <span>•</span>
        <span>[ENTER] SELECT</span>
        <span>•</span>
        <span>[ESC] BACK</span>
      </div>
    </div>
  )
}
