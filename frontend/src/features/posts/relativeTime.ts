const rtf = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' })

export function relativeTime(value: string, now = Date.now()) {
  const deltaSeconds = Math.round((new Date(value).getTime() - now) / 1000)
  const absolute = Math.abs(deltaSeconds)
  if (absolute < 45) return 'Vừa xong'
  if (absolute < 3600) return rtf.format(Math.round(deltaSeconds / 60), 'minute')
  if (absolute < 86400) return rtf.format(Math.round(deltaSeconds / 3600), 'hour')
  if (absolute < 172800) return deltaSeconds < 0 ? 'Hôm qua' : 'Ngày mai'
  if (absolute < 604800) return rtf.format(Math.round(deltaSeconds / 86400), 'day')
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(value))
}

export function exactDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'full',
    timeStyle: 'short',
  }).format(new Date(value))
}
