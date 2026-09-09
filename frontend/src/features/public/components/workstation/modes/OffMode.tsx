interface OffModeProps {
  onPowerOn: () => void
}

export function OffMode({ onPowerOn }: OffModeProps) {
  return (
    <div
      className="sl-ws-off-container"
      onClick={onPowerOn}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onPowerOn()
        }
      }}
      title="Click or flip power switch to power on"
    >
      <div className="sl-off-screen-center">
        <div className="sl-off-dot" />
        <div className="sl-off-msg">
          <div className="sl-off-title">WORKSTATION POWER OFF</div>
          <div className="sl-off-hint">CLICK SCREEN OR FLIP POWER SWITCH TO BOOT</div>
        </div>
      </div>
    </div>
  )
}
