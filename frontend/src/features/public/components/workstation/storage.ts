import type { Achievement, HighScores, WorkstationStorageData } from './types'

const STORAGE_KEY = 'smartlab_workstation_os_v1'

export const LAB_ACHIEVEMENTS: Achievement[] = [
  {
    id: 'BOOT_SYSTEM',
    title: 'COLD BOOT',
    desc: 'Completed power cycle & BIOS POST sequence',
    icon: '⚡',
  },
  {
    id: 'FLOPPY_DISK',
    title: 'RETRO ARCHIVIST',
    desc: 'Loaded 5.25" High-Density Arcade Disk',
    icon: '💾',
  },
  {
    id: 'DIAGNOSTICS_VIEW',
    title: 'KERNEL HACKER',
    desc: 'Accessed system hardware telemetry & nodes',
    icon: '🔍',
  },
  {
    id: 'RUNNER_SPRINT',
    title: 'BUG SPRINT',
    desc: 'Scored over 100 points in Lab Runner',
    icon: '🏃',
  },
  {
    id: 'RUNNER_MASTER',
    title: 'SENIOR RESEARCHER',
    desc: 'Scored over 300 points in Lab Runner',
    icon: '🎓',
  },
  {
    id: 'SNAKE_DATA',
    title: 'NEURAL CONVERGENCE',
    desc: 'Collected 10 data weight packets in Snake',
    icon: '🧠',
  },
  {
    id: 'PONG_DEFEAT_AI',
    title: 'TURING TEST PASSED',
    desc: 'Defeated SmartLab AI in Quantum Pong',
    icon: '🤖',
  },
  {
    id: 'BREAKOUT_LAYER',
    title: 'FIREWALL BREACH',
    desc: 'Destroyed 10+ security blocks in Breakout',
    icon: '🛡️',
  },
]

const DEFAULT_DATA: WorkstationStorageData = {
  highScores: {
    runner: 0,
    snake: 0,
    pong: 0,
    breakout: 0,
  },
  unlockedAchievementIds: [],
  settings: {
    soundEnabled: false,
    crtCurvature: true,
  },
}

export function loadWorkstationData(): WorkstationStorageData {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_DATA
    const parsed = JSON.parse(raw) as Partial<WorkstationStorageData>
    return {
      highScores: {
        ...DEFAULT_DATA.highScores,
        ...(parsed.highScores || {}),
      },
      unlockedAchievementIds: Array.isArray(parsed.unlockedAchievementIds)
        ? parsed.unlockedAchievementIds
        : [],
      settings: {
        ...DEFAULT_DATA.settings,
        ...(parsed.settings || {}),
      },
    }
  } catch {
    return DEFAULT_DATA
  }
}

export function saveWorkstationData(data: WorkstationStorageData): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  } catch {
    // Ignore storage quota / private browsing errors
  }
}

export function recordHighScore(game: keyof HighScores, score: number): boolean {
  const data = loadWorkstationData()
  if (score > (data.highScores[game] || 0)) {
    data.highScores[game] = score
    saveWorkstationData(data)
    return true
  }
  return false
}

export function unlockAchievement(achievementId: string): boolean {
  const data = loadWorkstationData()
  if (!data.unlockedAchievementIds.includes(achievementId)) {
    data.unlockedAchievementIds.push(achievementId)
    saveWorkstationData(data)
    return true
  }
  return false
}
