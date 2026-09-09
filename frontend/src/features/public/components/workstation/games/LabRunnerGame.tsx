import { useEffect, useRef, useState } from 'react'
import { soundSynth } from '../audio'
import { recordHighScore, unlockAchievement } from '../storage'

interface LabRunnerProps {
  onExit: () => void
  reducedMotion: boolean
}

interface Obstacle {
  x: number
  y: number
  w: number
  h: number
  type: 'bug' | 'rack' | 'drone'
}

interface Collectible {
  x: number
  y: number
  w: number
  h: number
  type: 'paper' | 'coffee'
  collected: boolean
}

export function LabRunnerGame({ onExit, reducedMotion }: LabRunnerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [score, setScore] = useState(0)
  const [isGameOver, setIsGameOver] = useState(false)
  const [isPaused, setIsPaused] = useState(false)

  const stateRef = useRef({
    score: 0,
    isGameOver: false,
    isPaused: false,
    playerY: 0,
    playerVy: 0,
    isGrounded: true,
    isDucking: false,
    speed: 2.2,
    frame: 0,
    obstacles: [] as Obstacle[],
    collectibles: [] as Collectible[],
    nextObstacleDist: 150,
    nextCollectDist: 90,
  })

  // Jump trigger
  const handleJump = () => {
    const s = stateRef.current
    if (s.isGameOver) {
      restartGame()
      return
    }
    if (s.isPaused) {
      s.isPaused = false
      setIsPaused(false)
      return
    }
    if (s.isGrounded) {
      s.playerVy = -9.2
      s.isGrounded = false
      soundSynth.playJump()
    }
  }

  const handleDuck = (ducking: boolean) => {
    stateRef.current.isDucking = ducking
  }

  const restartGame = () => {
    stateRef.current = {
      score: 0,
      isGameOver: false,
      isPaused: false,
      playerY: 0,
      playerVy: 0,
      isGrounded: true,
      isDucking: false,
      speed: 2.2,
      frame: 0,
      obstacles: [],
      collectibles: [],
      nextObstacleDist: 160,
      nextCollectDist: 100,
    }
    setScore(0)
    setIsGameOver(false)
    setIsPaused(false)
    soundSynth.playSelect()
  }

  // Keyboard controls
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onExit()
        return
      }
      if (e.key === 'KeyP') {
        e.preventDefault()
        stateRef.current.isPaused = !stateRef.current.isPaused
        setIsPaused(stateRef.current.isPaused)
        return
      }
      if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        handleJump()
      } else if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        e.preventDefault()
        handleDuck(true)
      } else if (e.key === 'Enter' && stateRef.current.isGameOver) {
        e.preventDefault()
        restartGame()
      }
    }

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        handleDuck(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('keyup', handleKeyUp)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('keyup', handleKeyUp)
    }
  }, [onExit])

  // Main game loop
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let animId: number
    const groundY = 175

    const loop = () => {
      const s = stateRef.current

      if (!s.isPaused && !s.isGameOver) {
        s.frame++
        // Distance score
        if (s.frame % 6 === 0) {
          s.score += 1
          setScore(s.score)
          if (s.score === 100) unlockAchievement('RUNNER_SPRINT')
          if (s.score === 300) unlockAchievement('RUNNER_MASTER')
        }

        // Difficulty progression (gentle and relaxed)
        s.speed = 2.2 + Math.min(s.score / 500, 1.4)

        // Physics (smooth retro float)
        const gravity = 0.46
        s.playerVy += gravity
        s.playerY += s.playerVy

        if (s.playerY >= 0) {
          s.playerY = 0
          s.playerVy = 0
          s.isGrounded = true
        }

        // Spawn obstacles
        s.nextObstacleDist -= s.speed
        if (s.nextObstacleDist <= 0) {
          const types: ('bug' | 'rack' | 'drone')[] = ['bug', 'rack', 'drone']
          const chosen = types[Math.floor(Math.random() * types.length)]
          if (chosen === 'bug') {
            s.obstacles.push({ x: 440, y: groundY - 18, w: 20, h: 18, type: 'bug' })
          } else if (chosen === 'rack') {
            s.obstacles.push({ x: 440, y: groundY - 32, w: 18, h: 32, type: 'rack' })
          } else {
            // Drone flies at jump/duck height
            s.obstacles.push({ x: 440, y: groundY - 38, w: 22, h: 14, type: 'drone' })
          }
          s.nextObstacleDist = 160 + Math.random() * 180
        }

        // Spawn collectibles
        s.nextCollectDist -= s.speed
        if (s.nextCollectDist <= 0) {
          const type = Math.random() > 0.4 ? 'paper' : 'coffee'
          const high = Math.random() > 0.5
          s.collectibles.push({
            x: 440,
            y: high ? groundY - 48 : groundY - 24,
            w: 14,
            h: 14,
            type,
            collected: false,
          })
          s.nextCollectDist = 90 + Math.random() * 140
        }

        // Move & filter obstacles
        for (let i = s.obstacles.length - 1; i >= 0; i--) {
          const obs = s.obstacles[i]
          obs.x -= s.speed
          if (obs.x + obs.w < 0) {
            s.obstacles.splice(i, 1)
          }
        }

        // Move & filter collectibles
        for (let i = s.collectibles.length - 1; i >= 0; i--) {
          const col = s.collectibles[i]
          col.x -= s.speed
          if (col.x + col.w < 0) {
            s.collectibles.splice(i, 1)
          }
        }

        // Player Hitbox
        const playerH = s.isDucking ? 18 : 34
        const playerW = 22
        const playerX = 40
        const playerBoxY = groundY - playerH + s.playerY

        // Check collision with obstacles
        for (const obs of s.obstacles) {
          if (
            playerX + playerW - 4 > obs.x &&
            playerX + 4 < obs.x + obs.w &&
            playerBoxY + playerH > obs.y + 3 &&
            playerBoxY < obs.y + obs.h
          ) {
            s.isGameOver = true
            setIsGameOver(true)
            soundSynth.playHit()
            soundSynth.playGameOver()
            recordHighScore('runner', s.score)
            break
          }
        }

        // Check collection
        for (const col of s.collectibles) {
          if (
            !col.collected &&
            playerX + playerW > col.x &&
            playerX < col.x + col.w &&
            playerBoxY + playerH > col.y &&
            playerBoxY < col.y + col.h
          ) {
            col.collected = true
            const bonus = col.type === 'paper' ? 30 : 20
            s.score += bonus
            setScore(s.score)
            soundSynth.playCollect()
          }
        }
      }

      // ── RENDERING ──────────────────────────────────────────────────
      const W = canvas.width
      const H = canvas.height

      // Dark retro lab background
      ctx.fillStyle = '#0F172A'
      ctx.fillRect(0, 0, W, H)

      // Perspective ceiling grid
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.08)'
      ctx.lineWidth = 1
      for (let y = 10; y < 70; y += 12) {
        ctx.beginPath()
        ctx.moveTo(0, y)
        ctx.lineTo(W, y)
        ctx.stroke()
      }

      // Lab Floor with scrolling markers
      ctx.fillStyle = '#1E293B'
      ctx.fillRect(0, groundY, W, H - groundY)

      ctx.fillStyle = '#38BDF8'
      ctx.fillRect(0, groundY, W, 2)

      const tileOffset = (s.frame * s.speed) % 30
      ctx.fillStyle = 'rgba(255, 255, 255, 0.15)'
      for (let x = -tileOffset; x < W; x += 30) {
        ctx.fillRect(x, groundY + 4, 12, 2)
      }

      // Draw Collectibles
      for (const col of s.collectibles) {
        if (col.collected) continue
        if (col.type === 'paper') {
          // Research paper document
          ctx.fillStyle = '#F8FAFC'
          ctx.fillRect(col.x, col.y, 12, 14)
          ctx.fillStyle = '#38BDF8'
          ctx.fillRect(col.x + 2, col.y + 3, 8, 2)
          ctx.fillRect(col.x + 2, col.y + 7, 6, 2)
        } else {
          // Coffee mug
          ctx.fillStyle = '#F59E0B'
          ctx.fillRect(col.x, col.y + 3, 10, 10)
          ctx.fillStyle = '#FBBF24'
          ctx.fillRect(col.x + 10, col.y + 5, 3, 6)
        }
      }

      // Draw Obstacles
      for (const obs of s.obstacles) {
        if (obs.type === 'bug') {
          // Software Bug
          ctx.fillStyle = '#EF4444'
          ctx.fillRect(obs.x + 4, obs.y + 4, 12, 10) // body
          ctx.fillStyle = '#FCA5A5'
          ctx.fillRect(obs.x + 6, obs.y + 6, 3, 3) // eyes
          ctx.fillRect(obs.x + 12, obs.y + 6, 3, 3)
          // legs
          ctx.fillStyle = '#DC2626'
          const legWiggle = s.frame % 8 > 4 ? 2 : 0
          ctx.fillRect(obs.x + 1, obs.y + 12 + legWiggle, 3, 4)
          ctx.fillRect(obs.x + 16, obs.y + 12 - legWiggle, 3, 4)
        } else if (obs.type === 'rack') {
          // Server Rack
          ctx.fillStyle = '#334155'
          ctx.fillRect(obs.x, obs.y, obs.w, obs.h)
          ctx.fillStyle = '#0EA5E9'
          ctx.fillRect(obs.x + 3, obs.y + 5, 4, 3)
          ctx.fillRect(obs.x + 3, obs.y + 14, 4, 3)
          ctx.fillStyle = '#22C55E'
          ctx.fillRect(obs.x + 10, obs.y + 5, 4, 3)
          ctx.fillRect(obs.x + 10, obs.y + 22, 4, 3)
        } else {
          // Drone
          ctx.fillStyle = '#A855F7'
          ctx.fillRect(obs.x, obs.y + 3, obs.w, 7)
          ctx.fillStyle = '#C084FC'
          ctx.fillRect(obs.x + 6, obs.y, 10, 3)
          ctx.fillStyle = '#E879F9'
          ctx.fillRect(obs.x + 9, obs.y + 10, 4, 4)
        }
      }

      // Draw Player (Researcher)
      const playerH = s.isDucking ? 18 : 34
      const playerBoxY = groundY - playerH + s.playerY
      const runCycle = Math.floor(s.frame / 4) % 2

      // Head & Hair
      ctx.fillStyle = '#FDBA74'
      ctx.fillRect(44, playerBoxY + 2, 12, 10)
      ctx.fillStyle = '#475569' // hair
      ctx.fillRect(44, playerBoxY, 12, 4)
      // Goggles
      ctx.fillStyle = '#38BDF8'
      ctx.fillRect(50, playerBoxY + 4, 6, 4)

      if (!s.isDucking) {
        // Lab coat body
        ctx.fillStyle = '#F8FAFC'
        ctx.fillRect(42, playerBoxY + 12, 16, 12)
        // Lab badge / tie
        ctx.fillStyle = '#F97316'
        ctx.fillRect(48, playerBoxY + 14, 3, 6)
        // Legs
        ctx.fillStyle = '#1E293B'
        if (s.isGrounded) {
          if (runCycle === 0) {
            ctx.fillRect(44, playerBoxY + 24, 5, 10)
            ctx.fillRect(52, playerBoxY + 24, 5, 8)
          } else {
            ctx.fillRect(44, playerBoxY + 24, 5, 8)
            ctx.fillRect(52, playerBoxY + 24, 5, 10)
          }
        } else {
          // In air legs tucked
          ctx.fillRect(43, playerBoxY + 24, 6, 6)
          ctx.fillRect(51, playerBoxY + 23, 6, 7)
        }
      } else {
        // Ducking lab coat
        ctx.fillStyle = '#F8FAFC'
        ctx.fillRect(40, playerBoxY + 10, 20, 8)
      }

      // HUD overlay: Score
      ctx.font = '700 11px ui-monospace, monospace'
      ctx.fillStyle = '#F8FAFC'
      ctx.fillText(`SCORE: ${s.score}`, 12, 20)

      ctx.fillStyle = '#38BDF8'
      ctx.fillText(`SPD: ${s.speed.toFixed(1)}x`, W - 80, 20)

      // Game Over Screen
      if (s.isGameOver) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.85)'
        ctx.fillRect(0, 0, W, H)

        ctx.fillStyle = '#EF4444'
        ctx.font = '900 16px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('RUNTIME EXCEPTION // CRASH', W / 2, H / 2 - 20)

        ctx.fillStyle = '#F8FAFC'
        ctx.font = '700 12px ui-monospace, monospace'
        ctx.fillText(`FINAL SCORE: ${s.score} PTS`, W / 2, H / 2 + 5)

        ctx.fillStyle = '#38BDF8'
        ctx.font = '600 10px ui-monospace, monospace'
        ctx.fillText('PRESS [SPACE / ENTER] TO RETRY', W / 2, H / 2 + 30)
        ctx.fillText('PRESS [ESC] TO RETURN TO ARCADE', W / 2, H / 2 + 48)
        ctx.textAlign = 'start'
      }

      // Paused Screen
      if (s.isPaused && !s.isGameOver) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.75)'
        ctx.fillRect(0, 0, W, H)
        ctx.fillStyle = '#FBBF24'
        ctx.font = '900 14px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('[PAUSED — PRESS P OR SPACE TO RESUME]', W / 2, H / 2)
        ctx.textAlign = 'start'
      }

      animId = requestAnimationFrame(loop)
    }

    animId = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(animId)
  }, [reducedMotion])

  return (
    <div className="sl-ws-game-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-orange" />
          <span>GAME: LAB RUNNER // SCORE: {score}</span>
        </div>
        <div className="sl-ws-game-header-actions">
          <button
            type="button"
            className="sl-ws-header-btn"
            onClick={() => {
              stateRef.current.isPaused = !stateRef.current.isPaused
              setIsPaused(stateRef.current.isPaused)
            }}
          >
            {isPaused ? 'RESUME [P]' : 'PAUSE [P]'}
          </button>
          <button
            type="button"
            className="sl-ws-header-btn"
            onClick={onExit}
          >
            EXIT [ESC]
          </button>
        </div>
      </div>

      <div
        className="sl-game-canvas-wrap"
        onClick={handleJump}
      >
        <canvas
          ref={canvasRef}
          width={420}
          height={200}
          className="sl-game-canvas"
        />
      </div>

      {/* On-screen touch/mouse controls */}
      <div className="sl-game-touch-bar">
        <button
          type="button"
          className="sl-touch-btn sl-touch-jump"
          onClick={(e) => {
            e.stopPropagation()
            handleJump()
          }}
        >
          {isGameOver ? 'RETRY' : '▲ JUMP [SPACE]'}
        </button>
        <button
          type="button"
          className="sl-touch-btn"
          onMouseDown={() => handleDuck(true)}
          onMouseUp={() => handleDuck(false)}
          onTouchStart={() => handleDuck(true)}
          onTouchEnd={() => handleDuck(false)}
        >
          ▼ DUCK
        </button>
        <button
          type="button"
          className="sl-touch-btn sl-touch-exit"
          onClick={(e) => {
            e.stopPropagation()
            onExit()
          }}
        >
          BACK [ESC]
        </button>
      </div>
    </div>
  )
}
