import { useEffect, useState } from 'react'
import { soundSynth } from '../audio'
import { unlockAchievement } from '../storage'

interface BootModeProps {
  reducedMotion: boolean
  onBootComplete: () => void
}

export function BootMode({ reducedMotion, onBootComplete }: BootModeProps) {
  const [stage, setStage] = useState(0)
  const [memCount, setMemCount] = useState(0)

  useEffect(() => {
    unlockAchievement('BOOT_SYSTEM')
    soundSynth.playBootBeep()

    if (reducedMotion) {
      onBootComplete()
      return
    }

    // Memory count step
    const memTimer = setInterval(() => {
      setMemCount((prev) => {
        if (prev >= 16384) {
          clearInterval(memTimer)
          setStage(1)
          return 16384
        }
        return prev + 2048
      })
    }, 40)

    return () => clearInterval(memTimer)
  }, [reducedMotion, onBootComplete])

  useEffect(() => {
    if (stage === 1) {
      const t1 = setTimeout(() => setStage(2), 250)
      const t2 = setTimeout(() => setStage(3), 550)
      const t3 = setTimeout(() => setStage(4), 850)
      const t4 = setTimeout(() => {
        onBootComplete()
      }, 1300)

      return () => {
        clearTimeout(t1)
        clearTimeout(t2)
        clearTimeout(t3)
        clearTimeout(t4)
      }
    }
  }, [stage, onBootComplete])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === ' ' || e.key === 'Enter' || e.key === 'Escape') {
        e.preventDefault()
        onBootComplete()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onBootComplete])

  return (
    <div
      className="sl-ws-boot-container"
      onClick={onBootComplete}
      title="Click to skip boot sequence"
    >
      <div className="sl-boot-header">
        SMARTLAB WORKSTATION BIOS v1.08 (C) 1994 SMART LAB CORP
      </div>
      <div className="sl-boot-sub">ROM BUS REV 3.2 — ALLOCATING DMA CHANNELS...</div>

      <div className="sl-boot-body">
        <div className="sl-boot-line">
          CHECKING EXTENDED RAM: <span className="sl-boot-hl">{memCount} KB OK</span>
        </div>

        {stage >= 1 && (
          <div className="sl-boot-line">
            DETECTING DRIVE A: 5.25" HIGH-DENSITY MFM... <span className="sl-boot-ok">READY</span>
          </div>
        )}

        {stage >= 2 && (
          <div className="sl-boot-line">
            INITIALIZING SMARTLAB NEURAL CO-PROCESSOR... <span className="sl-boot-ok">ONLINE</span>
          </div>
        )}

        {stage >= 3 && (
          <div className="sl-boot-line">
            LOADING SMARTLAB WORKSTATION OS v2.4 KERNEL... <span className="sl-boot-ok">MOUNTED</span>
          </div>
        )}

        {stage >= 4 && (
          <div className="sl-boot-line sl-boot-ready">
            STARTING RESEARCH WORKSPACE_
          </div>
        )}
      </div>

      <div className="sl-boot-footer">
        PRESS [SPACE] OR CLICK TO SKIP BOOT
      </div>
    </div>
  )
}
