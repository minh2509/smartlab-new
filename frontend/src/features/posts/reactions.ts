import { Angry, Frown, Heart, Laugh, ThumbsUp, type LucideIcon } from 'lucide-react'
import type { ReactionType } from './types'

export type ReactionPresentation = {
  type: ReactionType
  label: string
  Icon: LucideIcon
}

export const REACTIONS: ReactionPresentation[] = [
  { type: 'LIKE', label: 'Thích', Icon: ThumbsUp },
  { type: 'LOVE', label: 'Yêu thích', Icon: Heart },
  { type: 'HAHA', label: 'Haha', Icon: Laugh },
  { type: 'SAD', label: 'Buồn', Icon: Frown },
  { type: 'ANGRY', label: 'Phẫn nộ', Icon: Angry },
]

export function reactionPresentation(type: ReactionType | null) {
  return REACTIONS.find((item) => item.type === type) ?? REACTIONS[0]
}

export function representativeReactions(reactionCounts: Record<ReactionType, number>) {
  return REACTIONS.filter((reaction) => reactionCounts[reaction.type] > 0).slice(0, 3)
}
