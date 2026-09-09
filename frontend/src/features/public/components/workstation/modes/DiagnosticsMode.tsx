import { useEffect, useState } from 'react'
import { soundSynth } from '../audio'
import { unlockAchievement } from '../storage'

interface DiagnosticsModeProps {
  onBack: () => void
}

export function DiagnosticsMode({ onBack }: DiagnosticsModeProps) {
  const [uptime, setUptime] = useState(1482)
  const [cpuLoad, setCpuLoad] = useState(14)
  const [ramFree, setRamFree] = useState(11240)

  useEffect(() => {
    unlockAchievement('DIAGNOSTICS_VIEW')
    const interval = setInterval(() => {
      setUptime((prev) => prev + 1)
      setCpuLoad(Math.floor(10 + Math.random() * 12))
      setRamFree(11200 + Math.floor(Math.random() * 80))
    }, 1000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === 'Enter') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onBack()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onBack])

  const hours = Math.floor(uptime / 3600).toString().padStart(2, '0')
  const minutes = Math.floor((uptime % 3600) / 60).toString().padStart(2, '0')
  const seconds = (uptime % 60).toString().padStart(2, '0')

  return (
    <div className="sl-ws-diag-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-cyan" />
          <span>HARDWARE TELEMETRY // SL-WS01</span>
        </div>
        <button
          type="button"
          className="sl-ws-header-btn"
          onClick={onBack}
        >
          MENU [ESC]
        </button>
      </div>

      <div className="sl-diag-content">
        <div className="sl-diag-ascii">
{`   _____ __  ______    ____  ______  __  ____  _____
  / ___//  |/  /   |  / __ \\/_  __/ / / / / / / /   |
  \\__ \\/ /|_/ / /| | / /_/ / / /   / / / / / / / /| |
 ___/ / /  / / ___ |/ _, _/ / /   / /_/ / /_/ / ___ |
/____/_/  /_/_/  |_/_/ |_| /_/    \\____/\\____/_/  |_|`}
        </div>

        <div className="sl-diag-grid">
          <div className="sl-diag-col">
            <div className="sl-diag-item">
              <span className="sl-diag-label">SYSTEM:</span>
              <span className="sl-diag-val">SMARTLAB WORKSTATION OS 2.4</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">KERNEL:</span>
              <span className="sl-diag-val">sl-rt-x86_64-v2 (Monolithic)</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">CPU:</span>
              <span className="sl-diag-val">Quad Neural-X9 @ 4.8GHz [{cpuLoad}%]</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">MEMORY:</span>
              <span className="sl-diag-val">16,384 KB ECC [{ramFree} KB FREE]</span>
            </div>
          </div>

          <div className="sl-diag-col">
            <div className="sl-diag-item">
              <span className="sl-diag-label">UPTIME:</span>
              <span className="sl-diag-val">{hours}:{minutes}:{seconds}</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">DRIVE A:</span>
              <span className="sl-diag-val">5.25" 1.2MB MFM READY</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">BUS I/O:</span>
              <span className="sl-diag-val sl-text-ok">SL-FASTBUS @ 133MHz [OK]</span>
            </div>
            <div className="sl-diag-item">
              <span className="sl-diag-label">LAB SENSORS:</span>
              <span className="sl-diag-val sl-text-ok">TEMP 21.4°C // VOLT 12.02V</span>
            </div>
          </div>
        </div>

        <div className="sl-diag-nodes">
          <div className="sl-diag-section-title">ACTIVE RESEARCH TELEMETRY NODES:</div>
          <div className="sl-diag-node-list">
            <div className="sl-diag-node">
              <span className="sl-node-dot sl-dot-green" />
              <span>NODE-AI-01: COMPUTER VISION CLUSTER [RUNNING]</span>
            </div>
            <div className="sl-diag-node">
              <span className="sl-node-dot sl-dot-green" />
              <span>NODE-ROBOT-04: KINEMATICS SIMULATOR [SYNCED]</span>
            </div>
            <div className="sl-diag-node">
              <span className="sl-node-dot sl-dot-green" />
              <span>NODE-SE-02: CI/CD PIPELINE & TESTS [HEALTHY]</span>
            </div>
          </div>
        </div>
      </div>

      <div className="sl-ws-diag-footer">
        <span>STATUS: ALL SUBSYSTEMS NOMINAL</span>
        <button
          type="button"
          className="sl-diag-action-btn"
          onClick={onBack}
        >
          RETURN TO MENU [ESC]
        </button>
      </div>
    </div>
  )
}
