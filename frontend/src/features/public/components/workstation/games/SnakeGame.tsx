import { useEffect, useRef, useState } from 'react'
import { soundSynth } from '../audio'
import { recordHighScore, unlockAchievement } from '../storage'

interface SnakeGameProps {
  onExit: () => void
}

interface Point {
  x: number
  y: number
}

const GRID_COLS = 21
const GRID_ROWS = 10
const CELL_SIZE = 20

export function SnakeGame({ onExit }: SnakeGameProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [score, setScore] = useState(0)
  const [isGameOver, setIsGameOver] = useState(false)
  const [isPaused, setIsPaused] = useState(false)

  const stateRef = useRef({
    snake: [
      { x: 5, y: 5 },
      { x: 4, y: 5 },
      { x: 3, y: 5 },
    ] as Point[],
    dir: { x: 1, y: 0 } as Point,
    nextDir: { x: 1, y: 0 } as Point,
    food: { x: 12, y: 5 } as Point,
    score: 0,
    isGameOver: false,
    isPaused: false,
    tickInterval: 210,
  })

  const spawnFood = (snake: Point[]): Point => {
    let valid = false
    let newFood = { x: 0, y: 0 }
    while (!valid) {
      newFood = {
        x: Math.floor(Math.random() * GRID_COLS),
        y: Math.floor(Math.random() * GRID_ROWS),
      }
      valid = !snake.some((s) => s.x === newFood.x && s.y === newFood.y)
    }
    return newFood
  }

  const changeDirection = (dx: number, dy: number) => {
    const s = stateRef.current
    if (s.isGameOver) return
    // Prevent 180 degree instant turn into self
    if (dx !== 0 && s.dir.x !== 0) return
    if (dy !== 0 && s.dir.y !== 0) return
    s.nextDir = { x: dx, y: dy }
    soundSynth.playKey('normal')
  }

  const restartGame = () => {
    const initialSnake = [
      { x: 5, y: 5 },
      { x: 4, y: 5 },
      { x: 3, y: 5 },
    ]
    stateRef.current = {
      snake: initialSnake,
      dir: { x: 1, y: 0 },
      nextDir: { x: 1, y: 0 },
      food: spawnFood(initialSnake),
      score: 0,
      isGameOver: false,
      isPaused: false,
      tickInterval: 210,
    }
    setScore(0)
    setIsGameOver(false)
    setIsPaused(false)
    soundSynth.playSelect()
  }

  // Keyboard navigation
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

      if (e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        changeDirection(0, -1)
      } else if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        e.preventDefault()
        changeDirection(0, 1)
      } else if (e.key === 'ArrowLeft' || e.key === 'KeyA') {
        e.preventDefault()
        changeDirection(-1, 0)
      } else if (e.key === 'ArrowRight' || e.key === 'KeyD') {
        e.preventDefault()
        changeDirection(1, 0)
      } else if (e.key === 'Enter' && stateRef.current.isGameOver) {
        e.preventDefault()
        restartGame()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onExit])

  // Game tick loop
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let lastTick = performance.now()
    let animId: number

    const loop = (now: number) => {
      const s = stateRef.current

      if (!s.isPaused && !s.isGameOver && now - lastTick >= s.tickInterval) {
        lastTick = now
        s.dir = s.nextDir

        const head = { ...s.snake[0] }
        head.x += s.dir.x
        head.y += s.dir.y

        // Wall collisions
        if (head.x < 0 || head.x >= GRID_COLS || head.y < 0 || head.y >= GRID_ROWS) {
          s.isGameOver = true
          setIsGameOver(true)
          soundSynth.playHit()
          soundSynth.playGameOver()
          recordHighScore('snake', s.score)
        } else if (s.snake.some((seg) => seg.x === head.x && seg.y === head.y)) {
          // Self collision
          s.isGameOver = true
          setIsGameOver(true)
          soundSynth.playHit()
          soundSynth.playGameOver()
          recordHighScore('snake', s.score)
        } else {
          s.snake.unshift(head)
          // Check food
          if (head.x === s.food.x && head.y === s.food.y) {
            s.score += 1
            setScore(s.score)
            soundSynth.playCollect()
            if (s.score >= 10) unlockAchievement('SNAKE_DATA')
            s.food = spawnFood(s.snake)
            // Ramp speed gently
            s.tickInterval = Math.max(140, 210 - s.score * 3)
          } else {
            s.snake.pop()
          }
        }
      }

      // ── RENDERING ──────────────────────────────────────────
      const W = canvas.width
      const H = canvas.height

      // Background
      ctx.fillStyle = '#090D16'
      ctx.fillRect(0, 0, W, H)

      // Matrix grid lines
      ctx.strokeStyle = 'rgba(16, 185, 129, 0.08)'
      ctx.lineWidth = 1
      for (let x = 0; x <= W; x += CELL_SIZE) {
        ctx.beginPath()
        ctx.moveTo(x, 0)
        ctx.lineTo(x, H)
        ctx.stroke()
      }
      for (let y = 0; y <= H; y += CELL_SIZE) {
        ctx.beginPath()
        ctx.moveTo(0, y)
        ctx.lineTo(W, y)
        ctx.stroke()
      }

      // Draw Food (Neural Weight Tensor [W])
      ctx.fillStyle = '#10B981'
      ctx.fillRect(s.food.x * CELL_SIZE + 2, s.food.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4)
      ctx.fillStyle = '#064E3B'
      ctx.font = '900 11px ui-monospace, monospace'
      ctx.textAlign = 'center'
      ctx.fillText('W', s.food.x * CELL_SIZE + CELL_SIZE / 2, s.food.y * CELL_SIZE + CELL_SIZE / 2 + 4)

      // Draw Snake
      s.snake.forEach((seg, idx) => {
        if (idx === 0) {
          // Head (Bright Amber / Cyan)
          ctx.fillStyle = '#F59E0B'
          ctx.fillRect(seg.x * CELL_SIZE + 2, seg.y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4)
          // Eyes
          ctx.fillStyle = '#000000'
          const eyeOffset = s.dir.x !== 0 ? 3 : 5
          ctx.fillRect(seg.x * CELL_SIZE + eyeOffset, seg.y * CELL_SIZE + 4, 3, 3)
          ctx.fillRect(seg.x * CELL_SIZE + eyeOffset, seg.y * CELL_SIZE + 12, 3, 3)
        } else {
          // Body
          ctx.fillStyle = idx % 2 === 0 ? '#38BDF8' : '#0284C7'
          ctx.fillRect(seg.x * CELL_SIZE + 3, seg.y * CELL_SIZE + 3, CELL_SIZE - 6, CELL_SIZE - 6)
        }
      })

      // Game Over Screen
      if (s.isGameOver) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.85)'
        ctx.fillRect(0, 0, W, H)

        ctx.fillStyle = '#EF4444'
        ctx.font = '900 16px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('NEURAL DIVERGENCE // SEGFAULT', W / 2, H / 2 - 20)

        ctx.fillStyle = '#F8FAFC'
        ctx.font = '700 12px ui-monospace, monospace'
        ctx.fillText(`WEIGHTS COLLECTED: ${s.score}`, W / 2, H / 2 + 5)

        ctx.fillStyle = '#38BDF8'
        ctx.font = '600 10px ui-monospace, monospace'
        ctx.fillText('PRESS [ENTER / RETRY] TO PLAY AGAIN', W / 2, H / 2 + 30)
        ctx.fillText('PRESS [ESC] FOR ARCADE MENU', W / 2, H / 2 + 48)
      }

      // Paused Screen
      if (s.isPaused && !s.isGameOver) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.75)'
        ctx.fillRect(0, 0, W, H)
        ctx.fillStyle = '#FBBF24'
        ctx.font = '900 14px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('[PAUSED — PRESS P TO RESUME]', W / 2, H / 2)
      }

      animId = requestAnimationFrame(loop)
    }

    animId = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(animId)
  }, [])

  return (
    <div className="sl-ws-game-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-green" />
          <span>GAME: NEURAL SNAKE // NODES: {score}</span>
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

      <div className="sl-game-canvas-wrap">
        <canvas
          ref={canvasRef}
          width={GRID_COLS * CELL_SIZE}
          height={GRID_ROWS * CELL_SIZE}
          className="sl-game-canvas"
        />
      </div>

      {/* Touch virtual D-pad */}
      <div className="sl-game-touch-bar sl-dpad-bar">
        {isGameOver ? (
          <button
            type="button"
            className="sl-touch-btn sl-touch-jump"
            onClick={restartGame}
          >
            RETRY [ENTER]
          </button>
        ) : (
          <div className="sl-virtual-dpad">
            <button
              type="button"
              className="sl-dpad-btn sl-dpad-up"
              onClick={() => changeDirection(0, -1)}
            >
              ▲
            </button>
            <div className="sl-dpad-mid">
              <button
                type="button"
                className="sl-dpad-btn"
                onClick={() => changeDirection(-1, 0)}
              >
                ◀
              </button>
              <button
                type="button"
                className="sl-dpad-btn"
                onClick={() => changeDirection(0, 1)}
              >
                ▼
              </button>
              <button
                type="button"
                className="sl-dpad-btn"
                onClick={() => changeDirection(1, 0)}
              >
                ▶
              </button>
            </div>
          </div>
        )}
        <button
          type="button"
          className="sl-touch-btn sl-touch-exit"
          onClick={onExit}
        >
          BACK [ESC]
        </button>
      </div>
    </div>
  )
}
