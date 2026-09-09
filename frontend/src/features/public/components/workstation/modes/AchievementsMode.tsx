import { useEffect, useState } from 'react'
import { soundSynth } from '../audio'
import { LAB_ACHIEVEMENTS, loadWorkstationData, saveWorkstationData } from '../storage'
import type { WorkstationStorageData } from '../types'

interface AchievementsModeProps {
  onBack: () => void
}

export function AchievementsMode({ onBack }: AchievementsModeProps) {
  const [data, setData] = useState<WorkstationStorageData>(loadWorkstationData)
  const [resetConfirm, setResetConfirm] = useState(false)

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onBack()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onBack])

  const handleReset = () => {
    if (!resetConfirm) {
      setResetConfirm(true)
      return
    }
    const fresh: WorkstationStorageData = {
      highScores: { runner: 0, snake: 0, pong: 0, breakout: 0 },
      unlockedAchievementIds: [],
      settings: data.settings,
    }
    saveWorkstationData(fresh)
    setData(fresh)
    setResetConfirm(false)
    soundSynth.playSelect()
  }

  return (
    <div className="sl-ws-achieve-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-yellow" />
          <span>LAB RECORDS & ACHIEVEMENTS</span>
        </div>
        <button
          type="button"
          className="sl-ws-header-btn"
          onClick={onBack}
        >
          MENU [ESC]
        </button>
      </div>

      <div className="sl-achieve-scroll">
        <div className="sl-achieve-section">
          <div className="sl-achieve-title">=== LOCAL HIGH SCORES ===</div>
          <div className="sl-scores-grid">
            <div className="sl-score-card">
              <span className="sl-score-game">LAB RUNNER</span>
              <span className="sl-score-val">{data.highScores.runner} PTS</span>
            </div>
            <div className="sl-score-card">
              <span className="sl-score-game">NEURAL SNAKE</span>
              <span className="sl-score-val">{data.highScores.snake} PTS</span>
            </div>
            <div className="sl-score-card">
              <span className="sl-score-game">QUANTUM PONG</span>
              <span className="sl-score-val">{data.highScores.pong} WINS</span>
            </div>
            <div className="sl-score-card">
              <span className="sl-score-game">FIREWALL BREAKER</span>
              <span className="sl-score-val">{data.highScores.breakout} PTS</span>
            </div>
          </div>
        </div>

        <div className="sl-achieve-section">
          <div className="sl-achieve-title">=== LAB BADGES ({data.unlockedAchievementIds.length}/{LAB_ACHIEVEMENTS.length}) ===</div>
          <div className="sl-badges-list">
            {LAB_ACHIEVEMENTS.map((ach) => {
              const isUnlocked = data.unlockedAchievementIds.includes(ach.id)
              return (
                <div
                  key={ach.id}
                  className={`sl-badge-item ${isUnlocked ? 'is-unlocked' : 'is-locked'}`}
                >
                  <span className="sl-badge-icon">{isUnlocked ? ach.icon : '🔒'}</span>
                  <div className="sl-badge-info">
                    <div className="sl-badge-name">
                      {ach.title} {isUnlocked && <span className="sl-badge-tag">[UNLOCKED]</span>}
                    </div>
                    <div className="sl-badge-desc">{ach.desc}</div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </div>

      <div className="sl-ws-achieve-footer">
        <button
          type="button"
          className="sl-achieve-reset-btn"
          onClick={handleReset}
        >
          {resetConfirm ? 'CONFIRM ERASE ALL RECORDS?' : 'RESET RECORDS'}
        </button>
        <button
          type="button"
          className="sl-diag-action-btn"
          onClick={onBack}
        >
          RETURN [ESC]
        </button>
      </div>
    </div>
  )
}
