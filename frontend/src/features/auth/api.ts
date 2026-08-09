import { apiClient, toQuery } from '../../lib/apiClient'
import type { AccountResponse } from '../../shared/types/api'

export type AcceptInvitePayload = {
  token: string
  password: string
}

export type ResetPasswordPayload = {
  email: string
  otp: string
  newPassword: string
}

export type VerifyResetOtpPayload = {
  email: string
  otp: string
}

export function acceptInvitation(payload: AcceptInvitePayload) {
  return apiClient<AccountResponse>('/invitations/accept', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function sendResetOtp(email: string) {
  return apiClient<void>(`/send-reset-otp${toQuery({ email })}`, {
    method: 'POST',
  })
}

export function resetPassword(payload: ResetPasswordPayload) {
  return apiClient<void>('/reset-password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function verifyResetOtp(payload: VerifyResetOtpPayload) {
  return apiClient<void>('/verify-reset-otp', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
