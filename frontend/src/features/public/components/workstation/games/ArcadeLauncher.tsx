import { useEffect, useState } from 'react'
import type { OSMode } from '../types'
import { soundSynth } from '../audio'
import { loadWorkstationData, unlockAchievement } from '../storage'

interface ArcadeLauncherProps {
  onSelectGame: (gameMode: OSMode) => void
  onBack: () => void
}

interface GameOption {
  keyNum: string
  mode: OSMode
  title: string
  genre: string
  desc: string
  scoreKey: 'runner' | 'snake' | 'pong' | 'breakout'
  scoreUnit: string
}

const GAMES: GameOption[] = [
  {
    keyNum: '1',
    mode: 'GAME_RUNNER',
    title: 'LAB RUNNER',
    genre: 'ENDLESS SPRINT',
    desc: 'Jump over code bugs & cables, collect research papers & coffee!',
    scoreKey: 'runner',
    scoreUnit: 'PTS',
  },
  {
    keyNum: '2',
    mode: 'GAME_SNAKE',
    title: 'NEURAL SNAKE',
    genre: 'MATRIX STREAM',
    desc: 'Absorb neural weight tensors [W] to expand network length.',
    scoreKey: 'snake',
    scoreUnit: 'PTS',
  },
  {
    keyNum: '3',
    mode: 'GAME_PONG',
    title: 'QUANTUM PONG',
    genre: '1P VS AI BOT',
    desc: 'Duel the SmartLab AI co-processor in a high-speed paddle bounce.',
    scoreKey: 'pong',
    scoreUnit: 'WINS',
  },
  {
    keyNum: '4',
    mode: 'GAME_BREAKOUT',
    title: 'FIREWALL BREAKER',
    genre: 'SECURITY BRICK',
    desc: 'Bounce security probes to shatter multi-tier data firewalls.',
    scoreKey: 'breakout',
    scoreUnit: 'PTS',
  },
]

export function ArcadeLauncher({ onSelectGame, onBack }: ArcadeLauncherProps) {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const [highScores] = useState(() => loadWorkstationData().highScores)

  useEffect(() => {
    unlockAchievement('FLOPPY_DISK')
    soundSynth.playFloppySeek()
  }, [])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const num = parseInt(e.key, 10)
      if (num >= 1 && num <= GAMES.length) {
        e.preventDefault()
        soundSynth.playSelect()
        onSelectGame(GAMES[num - 1].mode)
        return
      }

      if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        e.preventDefault()
        soundSynth.playKey('normal')
        setSelectedIndex((prev) => (prev + 1) % GAMES.length)
      } else if (e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        soundSynth.playKey('normal')
        setSelectedIndex((prev) => (prev - 1 + GAMES.length) % GAMES.length)
      } else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        soundSynth.playSelect()
        onSelectGame(GAMES[selectedIndex].mode)
      } else if (e.key === 'Escape') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onBack()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectedIndex, onSelectGame, onBack])

  return (
    <div className="sl-ws-arcade-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-orange" />
          <span>DISK A:\ ARCADE // REV 1.4</span>
        </div>
        <button
          type="button"
          className="sl-ws-header-btn"
          onClick={onBack}
        >
          MENU [ESC]
        </button>
      </div>

      <div className="sl-arcade-disk-info">
        <span className="sl-disk-icon">💾</span>
        <span>5.25" SMARTLAB RESEARCH ENTERTAINMENT DISKETTE</span>
      </div>

      <div className="sl-arcade-list">
        {GAMES.map((game, idx) => {
          const isSelected = idx === selectedIndex
          const best = highScores[game.scoreKey]
          return (
            <button
              key={game.mode}
              type="button"
              className={`sl-arcade-item ${isSelected ? 'is-selected' : ''}`}
              onClick={() => {
                soundSynth.playSelect()
                onSelectGame(game.mode)
              }}
              onMouseEnter={() => setSelectedIndex(idx)}
            >
              <div className="sl-arcade-item-head">
                <span className="sl-arcade-key">[{game.keyNum}]</span>
                <span className="sl-arcade-cursor">{isSelected ? '►' : ' '}</span>
                <span className="sl-arcade-title">{game.title}</span>
                <span className="sl-arcade-genre">{game.genre}</span>
                <span className="sl-arcade-best">
                  BEST: {best} {game.scoreUnit}
                </span>
              </div>
              <div className="sl-arcade-desc">{game.desc}</div>
            </button>
          )
        })}
      </div>

      <div className="sl-ws-arcade-footer">
        <span>PRESS [1-4] OR CLICK TO LAUNCH</span>
        <span>•</span>
        <span>[ESC] BACK</span>
      </div>
    </div>
  )
}
