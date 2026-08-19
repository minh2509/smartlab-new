export type ToastTone = 'success' | 'error' | 'info'

export type ToastOptions = {
  duration?: number
}

export type Toast = {
  id: string
  tone: ToastTone
  title: string
  message?: string
  duration?: number
}

export type ToastApi = {
  success: (title: string, message?: string, options?: ToastOptions) => void
  error: (title: string, message?: string, options?: ToastOptions) => void
  info: (title: string, message?: string, options?: ToastOptions) => void
}
