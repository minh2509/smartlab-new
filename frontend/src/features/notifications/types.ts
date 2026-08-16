export type Notification = {
  id: number
  actorUserId: number | null
  actorName: string | null
  type: string
  message: string
  relatedType: string | null
  relatedId: number | null
  targetUrl: string | null
  isRead: boolean
  createdAt: string
}
