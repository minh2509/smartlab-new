import { useCallback, useEffect, useRef, useState } from 'react'
import type { HardwareActionTrigger, OSMode } from './types'
import { soundSynth } from './audio'
import { loadWorkstationData, saveWorkstationData } from './storage'
import { TerminalMode } from './modes/TerminalMode'
import { MenuMode } from './modes/MenuMode'
import { DiagnosticsMode } from './modes/DiagnosticsMode'
import { AchievementsMode } from './modes/AchievementsMode'
import { BootMode } from './modes/BootMode'
import { OffMode } from './modes/OffMode'
import { ArcadeLauncher } from './games/ArcadeLauncher'
import { LabRunnerGame } from './games/LabRunnerGame'
import { SnakeGame } from './games/SnakeGame'
import { PongGame } from './games/PongGame'
import { BreakoutGame } from './games/BreakoutGame'

interface WorkstationOSProps {
  reducedMotion: boolean
  isPowered: boolean
  onPowerToggle: () => void
  hardwareTrigger: HardwareActionTrigger | null
  onTriggerHandled: () => void
  onFloppyActivity: (active: boolean) => void
}

export function WorkstationOS({
  reducedMotion,
  isPowered,
  onPowerToggle,
  hardwareTrigger,
  onTriggerHandled,
  onFloppyActivity,
}: WorkstationOSProps) {
  const [mode, setMode] = useState<OSMode>('TERMINAL')
  const [soundEnabled, setSoundEnabled] = useState(false)
  const [isFocused, setIsFocused] = useState(false)
  const crtContainerRef = useRef<HTMLDivElement | null>(null)

  // Load initial settings
  useEffect(() => {
    const data = loadWorkstationData()
    setSoundEnabled(data.settings.soundEnabled)
    soundSynth.setEnabled(data.settings.soundEnabled)
  }, [])

  const toggleSound = useCallback(() => {
    setSoundEnabled((prev) => {
      const next = !prev
      soundSynth.setEnabled(next)
      const data = loadWorkstationData()
      data.settings.soundEnabled = next
      saveWorkstationData(data)
      if (next) soundSynth.playSelect()
      return next
    })
  }, [])

  // Handle Power On / Off transition
  useEffect(() => {
    if (!isPowered) {
      setMode('OFF')
    } else if (mode === 'OFF') {
      setMode('BOOT')
    }
  }, [isPowered, mode])

  // Process hardware triggers from physical workstation buttons
  useEffect(() => {
    if (!hardwareTrigger) return

    if (hardwareTrigger.action === 'floppy') {
      onFloppyActivity(true)
      setTimeout(() => onFloppyActivity(false), 800)
      if (mode === 'ARCADE' || mode.startsWith('GAME_')) {
        soundSynth.playFloppySeek()
        setMode('TERMINAL')
      } else {
        soundSynth.playFloppySeek()
        setMode('ARCADE')
        // Automatically focus screen for instant gameplay
        crtContainerRef.current?.focus()
      }
    } else if (hardwareTrigger.action === 'esc') {
      soundSynth.playKey('esc')
      if (mode.startsWith('GAME_')) {
        setMode('ARCADE')
      } else if (mode === 'ARCADE' || mode === 'DIAGNOSTICS' || mode === 'ACHIEVEMENTS') {
        setMode('MENU')
      } else if (mode === 'MENU') {
        setMode('TERMINAL')
      } else if (mode === 'TERMINAL') {
        setMode('MENU')
      } else {
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
      }
    } else if (hardwareTrigger.action === 'enter') {
      soundSynth.playKey('enter')
      if (mode === 'TERMINAL') {
        setMode('MENU')
      } else {
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }))
      }
    } else if (hardwareTrigger.action === 'space') {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true }))
    } else if (hardwareTrigger.action === 'left') {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', bubbles: true }))
    } else if (hardwareTrigger.action === 'right') {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', code: 'ArrowRight', bubbles: true }))
    }

    onTriggerHandled()
  }, [hardwareTrigger, mode, onFloppyActivity, onTriggerHandled])

  // Central keyboard listener ONLY active when CRT is focused!
  useEffect(() => {
    const container = crtContainerRef.current
    if (!container) return

    const handleKeyDown = (e: KeyboardEvent) => {
      // If CRT is NOT focused, do not intercept keyboard (allow normal page scrolling)
      if (!isFocused && document.activeElement !== container && !container.contains(document.activeElement)) {
        return
      }

      // Keys to intercept when inside CRT to prevent webpage scrolling
      const keysToTrap = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' ', 'PageUp', 'PageDown']
      if (keysToTrap.includes(e.key)) {
        e.preventDefault()
      }

      // Global ESC key behavior
      if (e.key === 'Escape') {
        e.preventDefault()
        if (mode.startsWith('GAME_')) {
          setMode('ARCADE')
        } else if (mode === 'ARCADE' || mode === 'DIAGNOSTICS' || mode === 'ACHIEVEMENTS') {
          setMode('MENU')
        } else if (mode === 'MENU') {
          setMode('TERMINAL')
        } else if (mode === 'TERMINAL') {
          setMode('MENU')
        }
      } else if (e.key === 'Enter' && mode === 'TERMINAL') {
        e.preventDefault()
        soundSynth.playKey('enter')
        setMode('MENU')
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isFocused, mode])

  return (
    <div
      ref={crtContainerRef}
      className={`sl-ws-crt-screen ${isFocused ? 'is-crt-focused' : ''} ${!isPowered ? 'is-crt-off' : ''}`}
      tabIndex={0}
      role="region"
      aria-label="SmartLab Workstation CRT Screen — Click to interact"
      onFocus={() => setIsFocused(true)}
      onBlur={(e) => {
        // Only mark unfocused if focus moved outside the CRT container
        if (!e.currentTarget.contains(e.relatedTarget as Node)) {
          setIsFocused(false)
        }
      }}
      onClick={() => {
        if (!isFocused) {
          setIsFocused(true)
          crtContainerRef.current?.focus()
        }
      }}
    >
      {/* Scanline & glare overlays */}
      <div className="sl-ws-crt-scanlines" />
      <div className="sl-ws-crt-glare" />

      {/* Screen Focus Badge */}
      <div className="sl-crt-status-bar">
        <div className="sl-crt-status-left">
          <span className={`sl-status-dot ${isPowered ? 'is-on' : 'is-off'}`} />
          <span className="sl-status-mode">{isPowered ? mode.replace('GAME_', '') : 'OFFLINE'}</span>
        </div>
        <div className="sl-crt-status-right">
          <button
            type="button"
            className="sl-status-toggle"
            onClick={(e) => {
              e.stopPropagation()
              toggleSound()
            }}
            title="Toggle Sound Effects"
          >
            {soundEnabled ? '🔊 SOUND: ON' : '🔇 SOUND: OFF'}
          </button>
          {isFocused ? (
            <button
              type="button"
              className="sl-status-focus-badge is-active"
              onClick={(e) => {
                e.stopPropagation()
                setIsFocused(false)
                crtContainerRef.current?.blur()
              }}
              title="Click to release focus and restore normal page scroll"
            >
              ACTIVE [ESC=MENU / TAB=EXIT]
            </button>
          ) : (
            <span className="sl-status-focus-badge">
              CLICK SCREEN TO FOCUS
            </span>
          )}
        </div>
      </div>

      {/* Active Screen Mode Component */}
      <div className="sl-crt-content-viewport">
        {mode === 'OFF' && (
          <OffMode onPowerOn={onPowerToggle} />
        )}

        {mode === 'BOOT' && (
          <BootMode
            reducedMotion={reducedMotion}
            onBootComplete={() => setMode('TERMINAL')}
          />
        )}

        {mode === 'TERMINAL' && (
          <TerminalMode
            reducedMotion={reducedMotion}
            onOpenMenu={() => setMode('MENU')}
          />
        )}

        {mode === 'MENU' && (
          <MenuMode
            onSelectMode={(next) => setMode(next)}
            soundEnabled={soundEnabled}
            onToggleSound={toggleSound}
            onClose={() => setMode('TERMINAL')}
          />
        )}

        {mode === 'DIAGNOSTICS' && (
          <DiagnosticsMode onBack={() => setMode('MENU')} />
        )}

        {mode === 'ACHIEVEMENTS' && (
          <AchievementsMode onBack={() => setMode('MENU')} />
        )}

        {mode === 'ARCADE' && (
          <ArcadeLauncher
            onSelectGame={(gameMode) => setMode(gameMode)}
            onBack={() => setMode('MENU')}
          />
        )}

        {mode === 'GAME_RUNNER' && (
          <LabRunnerGame
            reducedMotion={reducedMotion}
            onExit={() => setMode('ARCADE')}
          />
        )}

        {mode === 'GAME_SNAKE' && (
          <SnakeGame onExit={() => setMode('ARCADE')} />
        )}

        {mode === 'GAME_PONG' && (
          <PongGame onExit={() => setMode('ARCADE')} />
        )}

        {mode === 'GAME_BREAKOUT' && (
          <BreakoutGame onExit={() => setMode('ARCADE')} />
        )}
      </div>
    </div>
  )
}
