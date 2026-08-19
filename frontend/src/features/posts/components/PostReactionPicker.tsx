import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { ReactionType } from '../types'
import { REACTIONS, reactionPresentation } from '../reactions'

type Props = {
  selected: ReactionType | null
  disabled?: boolean
  onSelect: (reaction: ReactionType) => void
  onRemove: () => void
}

export function PostReactionPicker({ selected, disabled, onSelect, onRemove }: Props) {
  const [open, setOpen] = useState(false)
  const current = reactionPresentation(selected)
  const CurrentIcon = current.Icon

  return (
    <div className="reaction-control" onKeyDown={(event) => event.key === 'Escape' && setOpen(false)}>
      {open ? (
        <div className="reaction-picker" role="group" aria-label="Chọn cảm xúc">
          {REACTIONS.map((reaction) => (
            <button
              type="button"
              title={reaction.label}
              aria-label={reaction.label}
              aria-pressed={selected === reaction.type}
              className={selected === reaction.type ? 'is-selected' : ''}
              key={reaction.type}
              onClick={() => { onSelect(reaction.type); setOpen(false) }}
            >
              <reaction.Icon aria-hidden="true" />
            </button>
          ))}
        </div>
      ) : null}
      <button
        type="button"
        className={selected ? 'social-action is-selected' : 'social-action'}
        disabled={disabled}
        aria-label={selected ? `Gỡ cảm xúc ${current.label}` : 'Thích'}
        aria-pressed={Boolean(selected)}
        onClick={() => selected ? onRemove() : onSelect('LIKE')}
        onContextMenu={(event) => { event.preventDefault(); setOpen((value) => !value) }}
      >
        <CurrentIcon aria-hidden="true" />
        {selected ? current.label : 'Thích'}
      </button>
      <button
        type="button"
        className="reaction-picker-trigger"
        disabled={disabled}
        aria-label="Mở danh sách cảm xúc"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <ChevronDown aria-hidden="true" />
      </button>
    </div>
  )
}
