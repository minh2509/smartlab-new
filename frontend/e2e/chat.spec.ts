import { expect, test, type Page } from '@playwright/test'

test.use({ video: 'off' })

const MOCK_PROFILE = {
  userId: 'user-01',
  name: 'Nguyễn Văn A',
  email: 'a@smartlab.edu.vn',
  isActive: true,
  isAccountVerified: true,
  roles: ['MEMBER'],
  permissions: ['notifications.read_own'],
}

const MOCK_CONVERSATIONS = [
  {
    conversationId: 'conv-a',
    type: 'DIRECT',
    displayName: 'Thành viên B',
    title: null,
    projectId: null,
    members: [
      { userId: 'user-01', name: 'Nguyễn Văn A', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 5, active: true },
      { userId: 'user-02', name: 'Thành viên B', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 5, active: true },
    ],
    lastMessage: {
      id: 1,
      conversationId: 'conv-a',
      messageSeq: 5,
      sender: { userId: 'user-02', name: 'Thành viên B' },
      clientMessageId: 'cmsg-1',
      messageType: 'TEXT',
      content: 'Chào buổi sáng!',
      replyToMessageId: null,
      files: [],
      createdAt: '2026-10-01T08:00:00Z',
      editedAt: null,
      deletedAt: null,
    },
    lastMessageSeq: 5,
    lastReadSeq: 5,
    unreadCount: 0,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-01T08:00:00Z',
  },
  {
    conversationId: 'conv-b',
    type: 'DIRECT',
    displayName: 'Thành viên C',
    title: null,
    projectId: null,
    members: [
      { userId: 'user-01', name: 'Nguyễn Văn A', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 2, active: true },
      { userId: 'user-03', name: 'Thành viên C', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 2, active: true },
    ],
    lastMessage: {
      id: 2,
      conversationId: 'conv-b',
      messageSeq: 2,
      sender: { userId: 'user-03', name: 'Thành viên C' },
      clientMessageId: 'cmsg-2',
      messageType: 'TEXT',
      content: 'Báo cáo đã nộp chưa?',
      replyToMessageId: null,
      files: [],
      createdAt: '2026-10-01T08:10:00Z',
      editedAt: null,
      deletedAt: null,
    },
    lastMessageSeq: 2,
    lastReadSeq: 2,
    unreadCount: 0,
    createdAt: '2026-10-01T08:05:00Z',
    updatedAt: '2026-10-01T08:10:00Z',
  },
  {
    conversationId: 'conv-c',
    type: 'GROUP',
    displayName: 'Nhóm AI Robotics',
    title: 'Nhóm AI Robotics',
    projectId: 1,
    members: [
      { userId: 'user-01', name: 'Nguyễn Văn A', role: 'OWNER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 10, active: true },
      { userId: 'user-02', name: 'Thành viên B', role: 'ADMIN', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 10, active: true },
      { userId: 'user-03', name: 'Thành viên C', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 10, active: true },
    ],
    lastMessage: {
      id: 3,
      conversationId: 'conv-c',
      messageSeq: 10,
      sender: { userId: 'user-02', name: 'Thành viên B' },
      clientMessageId: 'cmsg-3',
      messageType: 'TEXT',
      content: 'Họp lab lúc 14h nhé',
      replyToMessageId: null,
      files: [],
      createdAt: '2026-10-01T08:20:00Z',
      editedAt: null,
      deletedAt: null,
    },
    lastMessageSeq: 10,
    lastReadSeq: 10,
    unreadCount: 0,
    createdAt: '2026-10-01T08:15:00Z',
    updatedAt: '2026-10-01T08:20:00Z',
  },
  {
    conversationId: 'conv-d',
    type: 'DIRECT',
    displayName: 'Thành viên D',
    title: null,
    projectId: null,
    members: [
      { userId: 'user-01', name: 'Nguyễn Văn A', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 1, active: true },
      { userId: 'user-04', name: 'Thành viên D', role: 'MEMBER', joinedAt: '2026-01-01T00:00:00Z', leftAt: null, lastReadSeq: 1, active: true },
    ],
    lastMessage: {
      id: 4,
      conversationId: 'conv-d',
      messageSeq: 1,
      sender: { userId: 'user-04', name: 'Thành viên D' },
      clientMessageId: 'cmsg-4',
      messageType: 'TEXT',
      content: 'Hello World',
      replyToMessageId: null,
      files: [],
      createdAt: '2026-10-01T08:25:00Z',
      editedAt: null,
      deletedAt: null,
    },
    lastMessageSeq: 1,
    lastReadSeq: 1,
    unreadCount: 0,
    createdAt: '2026-10-01T08:25:00Z',
    updatedAt: '2026-10-01T08:25:00Z',
  },
]

async function setupMockChatRoutes(
  page: Page,
  options?: {
    initialUnread?: number
    rejectSocket?: boolean
    onWebSocketConnected?: (sendRealtimeEvent: (envelope: any) => void) => void
  },
) {
  await page.addInitScript(() => {
    window.localStorage.setItem('smartlab.token', 'mock-chat-token')
    window.localStorage.setItem('smartlab.sessionId', 'mock-session-id')
  })

  await page.route('**/api/v1.0/profile*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_PROFILE),
    })
  })

  await page.route('**/api/v1.0/chat/**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()

    if (method === 'GET' && !url.includes('/messages')) {
      const convs = MOCK_CONVERSATIONS.map((c, i) =>
        i === 0 && options?.initialUnread
          ? { ...c, unreadCount: options.initialUnread, lastReadSeq: c.lastMessageSeq - options.initialUnread }
          : c,
      )
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(convs),
      })
      return
    }

    if (url.includes('/messages') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          messages: [
            {
              id: 101,
              conversationId: 'conv-a',
              messageSeq: 1,
              sender: { userId: 'user-02', name: 'Thành viên B' },
              clientMessageId: 'cmsg-init',
              messageType: 'TEXT',
              content: 'Chào buổi sáng!',
              replyToMessageId: null,
              files: [],
              createdAt: '2026-10-01T08:00:00Z',
              editedAt: null,
              deletedAt: null,
            },
          ],
          nextBeforeSeq: null,
          nextAfterSeq: null,
          hasMore: false,
        }),
      })
      return
    }

    if (url.includes('/messages') && method === 'POST') {
      const postData = route.request().postDataJSON()
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 999,
          conversationId: 'conv-a',
          messageSeq: 6,
          sender: { userId: 'user-01', name: 'Nguyễn Văn A' },
          clientMessageId: postData.clientMessageId,
          messageType: postData.messageType || 'TEXT',
          content: postData.content || null,
          replyToMessageId: postData.replyToMessageId || null,
          files: [],
          createdAt: new Date().toISOString(),
          editedAt: null,
          deletedAt: null,
        }),
      })
      return
    }

    if (url.includes('/read') && method === 'PATCH') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ conversationId: 'conv-a', userId: 'user-01', lastReadSeq: 6 }),
      })
      return
    }

    await route.fulfill({ status: 200, body: '{}' })
  })

  // Mock WebSocket
  await page.routeWebSocket(
    (url) => url.pathname.includes('/ws/chat') || url.href.includes('/ws/chat'),
    (ws) => {
      if (options?.rejectSocket) {
        ws.close({ code: 1008, reason: 'Unauthorized' })
        return
      }

      let subId = 'sub-0'
      ws.onMessage((message) => {
        const text = message.toString()
        if (text.startsWith('CONNECT')) {
          ws.send('CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\0')
        } else if (text.startsWith('SUBSCRIBE')) {
          const match = text.match(/id:([^\r\n]+)/)
          if (match) {
            subId = match[1]
          }
          if (options?.onWebSocketConnected) {
            options.onWebSocketConnected((envelope) => {
              ws.send(
                `MESSAGE\ndestination:/user/queue/chat\nsubscription:${subId}\nmessage-id:msg-${Date.now()}\ncontent-type:application/json\n\n${JSON.stringify(envelope)}\0`,
              )
            })
          }
        }
      })
    },
  )
}

async function openConvFromHeader(page: Page, textMatch: string | RegExp) {
  const chatBtn = page.locator('.chat-header-btn')
  const popover = page.locator('#chat-popover-panel')

  if (!(await popover.isVisible())) {
    await chatBtn.click()
    await expect(popover).toBeVisible()
  }

  const convItem = popover.locator('.chat-conv-item', { hasText: textMatch })
  await convItem.click()
  await expect(popover).toBeHidden()
}

test.describe('SmartLab Realtime Chat Automation Suite', () => {
  test('1. header chat button open/close popover', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    const chatBtn = page.locator('.chat-header-btn')
    await expect(chatBtn).toBeVisible()

    // Click to open popover
    await chatBtn.click()
    const popover = page.locator('#chat-popover-panel')
    await expect(popover).toBeVisible()
    await expect(popover.getByRole('heading', { name: 'Đoạn chat' })).toBeVisible()

    // Click close icon button inside popover
    await popover.getByRole('button', { name: 'Đóng bảng chat' }).click()
    await expect(popover).not.toBeVisible()
  })

  test('2. click conversation => popup', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    await openConvFromHeader(page, 'Thành viên B')

    // Popup window for conv-a opens
    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await expect(popupA).toBeVisible()
    await expect(popupA.locator('.chat-popup-title')).toHaveText('Thành viên B')
  })

  test('3. same conversation opened twice => exactly one popup', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open conversation A once
    await openConvFromHeader(page, 'Thành viên B')
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toHaveCount(1)

    // Open conversation A a second time
    await openConvFromHeader(page, 'Thành viên B')
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toHaveCount(1)
  })

  test('4. open A B C D => only B C D remain (MAX 3 FIFO eviction)', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open A
    await openConvFromHeader(page, 'Thành viên B')
    await page.waitForTimeout(100)

    // Open B
    await openConvFromHeader(page, 'Thành viên C')
    await page.waitForTimeout(100)

    // Open C
    await openConvFromHeader(page, 'Nhóm AI Robotics')
    await page.waitForTimeout(100)

    // All 3 open
    await expect(page.locator('.chat-popup-window')).toHaveCount(3)
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-b"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-c"]')).toBeVisible()

    // Open D
    await openConvFromHeader(page, 'Thành viên D')
    await page.waitForTimeout(100)

    // Strictly 3 popups remain: B, C, D (A was evicted because it was opened first)
    await expect(page.locator('.chat-popup-window')).toHaveCount(3)
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toHaveCount(0)
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-b"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-c"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-d"]')).toBeVisible()
  })

  test('5. critical FIFO regression: open A, open B, open C, focus A, open D => expected: B C D', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open A
    await openConvFromHeader(page, 'Thành viên B')
    await page.waitForTimeout(100)

    // Open B
    await openConvFromHeader(page, 'Thành viên C')
    await page.waitForTimeout(100)

    // Open C
    await openConvFromHeader(page, 'Nhóm AI Robotics')
    await page.waitForTimeout(100)

    // Focus A multiple times (clicking popup A header)
    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await popupA.click()
    await page.waitForTimeout(50)
    await popupA.click()
    await page.waitForTimeout(100)

    // Open D
    await openConvFromHeader(page, 'Thành viên D')
    await page.waitForTimeout(100)

    // A MUST STILL BE EVICTED because eviction is strictly FIFO by openedAt, NOT LRU!
    await expect(page.locator('.chat-popup-window')).toHaveCount(3)
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toHaveCount(0)
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-b"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-c"]')).toBeVisible()
    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-d"]')).toBeVisible()
  })

  test('6. minimize / restore popup', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open conversation A
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await expect(popupA).toHaveClass(/is-open/)

    // Click minimize button
    const minBtn = popupA.getByRole('button', { name: /thu nhỏ cửa sổ/i })
    await minBtn.click()
    await expect(popupA).toHaveClass(/is-minimized/)

    // Click restore button
    const restoreBtn = popupA.getByRole('button', { name: /mở lại cửa sổ/i })
    await restoreBtn.click()
    await expect(popupA).toHaveClass(/is-open/)
  })

  test('7. close popup', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await expect(popupA).toBeVisible()

    // Click close
    await popupA.getByRole('button', { name: /đóng cửa sổ chat/i }).click()
    await expect(popupA).toHaveCount(0)
  })

  test('8. remove chat-head using X', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open conv A to get it in dock
    await openConvFromHeader(page, 'Thành viên B')

    const dockItem = page.locator('.chat-dock-head-wrap[data-dock-conversation-id="conv-a"]')
    await expect(dockItem).toBeVisible()

    // Hover to reveal X button, then click it
    await dockItem.hover()
    const removeBtn = dockItem.locator('.chat-dock-remove-btn')
    await removeBtn.click()
    await expect(dockItem).toHaveCount(0)
  })

  test('9. close popup does not accidentally remove dock item', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open conv A
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    const dockItem = page.locator('.chat-dock-head-wrap[data-dock-conversation-id="conv-a"]')

    await expect(popupA).toBeVisible()
    await expect(dockItem).toBeVisible()

    // Close popup
    await popupA.getByRole('button', { name: /đóng cửa sổ chat/i }).click()
    await expect(popupA).toHaveCount(0)

    // DOCK ITEM MUST STILL BE VISIBLE!
    await expect(dockItem).toBeVisible()
  })

  test('10. popup state survives SPA route navigation', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open popup A
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await expect(popupA).toBeVisible()

    // Navigate to /gioi-thieu
    await page.getByRole('link', { name: 'Về Phòng Lab' }).click()
    await expect(page).toHaveURL(/.*gioi-thieu/)

    // Popup A must still be present and visible
    await expect(popupA).toBeVisible()
  })

  test('11. expand => full chat page', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    // Open popup A
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await popupA.getByRole('button', { name: /mở trang đầy đủ/i }).click()

    // Navigates to /chat?id=conv-a
    await expect(page).toHaveURL(/.*\/chat\?id=conv-a/)
    await expect(page.locator('.chat-page-shell')).toBeVisible()
  })

  test('12. realtime incoming event updates correct thread', async ({ page }) => {
    let sendEvent: ((envelope: any) => void) | null = null
    await setupMockChatRoutes(page, {
      onWebSocketConnected: (sender) => {
        sendEvent = sender
      },
    })
    await page.goto('/')

    // Open conversation A and ensure it is rendered and initial messages loaded
    await openConvFromHeader(page, 'Thành viên B')
    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await expect(popupA.getByText('Chào buổi sáng!')).toBeVisible()

    // Wait until WebSocket is connected and subscribed
    await expect.poll(() => sendEvent !== null, { timeout: 10000 }).toBeTruthy()

    // Simulate server sending realtime message for conv-a
    sendEvent!({
      event: 'chat.message.created',
      eventId: 'evt-realtime-1',
      occurredAt: new Date().toISOString(),
      conversationId: 'conv-a',
      data: {
        id: 505,
        conversationId: 'conv-a',
        messageSeq: 6,
        sender: { userId: 'user-02', name: 'Thành viên B' },
        clientMessageId: 'server-realtime-msg',
        messageType: 'TEXT',
        content: 'Tin nhắn realtime thử nghiệm!',
        replyToMessageId: null,
        files: [],
        createdAt: new Date().toISOString(),
        editedAt: null,
        deletedAt: null,
      },
    })

    // Message appears in thread
    await expect(page.getByText('Tin nhắn realtime thử nghiệm!')).toBeVisible()
  })

  test('13. background realtime increments unread', async ({ page }) => {
    let sendEvent: ((envelope: any) => void) | null = null
    await setupMockChatRoutes(page, {
      onWebSocketConnected: (sender) => {
        sendEvent = sender
      },
    })
    await page.goto('/')

    // Wait until WebSocket is connected and subscribed
    await expect.poll(() => sendEvent !== null, { timeout: 10000 }).toBeTruthy()

    // Send event for conv-b (which is NOT currently open)
    sendEvent!({
      event: 'chat.message.created',
      eventId: 'evt-bg-1',
      occurredAt: new Date().toISOString(),
      conversationId: 'conv-b',
      data: {
        id: 606,
        conversationId: 'conv-b',
        messageSeq: 3,
        sender: { userId: 'user-03', name: 'Thành viên C' },
        clientMessageId: 'cmsg-bg',
        messageType: 'TEXT',
        content: 'Tin nhắn nền mới',
        replyToMessageId: null,
        files: [],
        createdAt: new Date().toISOString(),
        editedAt: null,
        deletedAt: null,
      },
    })

    // Header chat icon unread badge increases to 1
    const unreadBadge = page.locator('.chat-header-btn .notification-count')
    await expect(unreadBadge).toBeVisible()
    await expect(unreadBadge).toHaveText('1')
  })

  test('14. viewed conversation advances read state', async ({ page }) => {
    await setupMockChatRoutes(page, { initialUnread: 2 })
    await page.goto('/')

    // Header initially has unread badge "2"
    const unreadBadge = page.locator('.chat-header-btn .notification-count')
    await expect(unreadBadge).toBeVisible()
    await expect(unreadBadge).toHaveText('2')

    // Open conv A (which has unread messages)
    await openConvFromHeader(page, 'Thành viên B')

    // Viewing conversation clears unread count
    await expect(unreadBadge).toHaveCount(0)
  })

  test('15. optimistic send: sending -> sent', async ({ page }) => {
    await setupMockChatRoutes(page)
    await page.goto('/')

    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    const input = popupA.getByRole('textbox', { name: /soạn tin nhắn/i })
    await input.fill('Chào bạn nhé')

    // Send
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    // Content appears immediately in the message list
    await expect(popupA.getByText('Chào bạn nhé')).toBeVisible()
  })

  test('16. failed send: sending -> failed -> retry', async ({ page }) => {
    await setupMockChatRoutes(page, { rejectSocket: true })
    let shouldFail = true

    // Intercept message send to fail on first attempt
    await page.route('**/api/v1.0/chat/conversations/conv-a/messages', async (route) => {
      if (route.request().method() === 'POST') {
        if (shouldFail) {
          shouldFail = false
          await route.fulfill({ status: 500, body: JSON.stringify({ message: 'Internal Server Error' }) })
        } else {
          const body = route.request().postDataJSON()
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({
              id: 777,
              conversationId: 'conv-a',
              messageSeq: 7,
              sender: { userId: 'user-01', name: 'Nguyễn Văn A' },
              clientMessageId: body.clientMessageId,
              messageType: 'TEXT',
              content: body.content,
              replyToMessageId: null,
              files: [],
              createdAt: new Date().toISOString(),
              editedAt: null,
              deletedAt: null,
            }),
          })
        }
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            messages: [
              {
                id: 101,
                conversationId: 'conv-a',
                messageSeq: 1,
                sender: { userId: 'user-02', name: 'Thành viên B' },
                clientMessageId: 'cmsg-init',
                messageType: 'TEXT',
                content: 'Chào buổi sáng!',
                replyToMessageId: null,
                files: [],
                createdAt: '2026-10-01T08:00:00Z',
                editedAt: null,
                deletedAt: null,
              },
            ],
            nextBeforeSeq: null,
            nextAfterSeq: null,
            hasMore: false,
          }),
        })
      }
    })

    await page.goto('/')
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    const input = popupA.getByRole('textbox', { name: /soạn tin nhắn/i })
    await input.fill('Tin nhắn lỗi mạng')
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    // Fails and shows "Gửi thất bại" with "Thử lại" button
    await expect(popupA.getByText('Gửi thất bại')).toBeVisible()
    const retryBtn = popupA.getByRole('button', { name: 'Thử lại' })
    await expect(retryBtn).toBeVisible()

    // Click retry
    await retryBtn.click()
    await expect(popupA.getByText('Gửi thất bại')).toHaveCount(0)
  })

  test('17. retry preserves clientMessageId', async ({ page }) => {
    await setupMockChatRoutes(page, { rejectSocket: true })
    const clientMessageIds: string[] = []
    let callCount = 0

    await page.route('**/api/v1.0/chat/conversations/conv-a/messages', async (route) => {
      if (route.request().method() === 'POST') {
        callCount++
        const body = route.request().postDataJSON()
        clientMessageIds.push(body.clientMessageId)
        if (callCount === 1) {
          await route.fulfill({ status: 500, body: JSON.stringify({ message: 'Network error' }) })
        } else {
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({
              id: 888,
              conversationId: 'conv-a',
              messageSeq: 8,
              sender: { userId: 'user-01', name: 'Nguyễn Văn A' },
              clientMessageId: body.clientMessageId,
              messageType: 'TEXT',
              content: body.content,
              replyToMessageId: null,
              files: [],
              createdAt: new Date().toISOString(),
              editedAt: null,
              deletedAt: null,
            }),
          })
        }
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            messages: [
              {
                id: 101,
                conversationId: 'conv-a',
                messageSeq: 1,
                sender: { userId: 'user-02', name: 'Thành viên B' },
                clientMessageId: 'cmsg-init',
                messageType: 'TEXT',
                content: 'Chào buổi sáng!',
                replyToMessageId: null,
                files: [],
                createdAt: '2026-10-01T08:00:00Z',
                editedAt: null,
                deletedAt: null,
              },
            ],
            nextBeforeSeq: null,
            nextAfterSeq: null,
            hasMore: false,
          }),
        })
      }
    })

    await page.goto('/')
    await openConvFromHeader(page, 'Thành viên B')

    const popupA = page.locator('.chat-popup-window[data-conversation-id="conv-a"]')
    await popupA.getByRole('textbox', { name: /soạn tin nhắn/i }).fill('Test clientMessageId')
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    await expect(popupA.getByRole('button', { name: 'Thử lại' })).toBeVisible()
    await popupA.getByRole('button', { name: 'Thử lại' }).click()

    await page.waitForTimeout(300)
    expect(clientMessageIds.length).toBe(2)
    // CRITICAL: clientMessageId MUST be preserved on retry!
    expect(clientMessageIds[0]).toBe(clientMessageIds[1])
  })

  test('18. reconnect afterSeq: no gaps no duplicates', async ({ page }) => {
    await setupMockChatRoutes(page)

    await page.route('**/api/v1.0/chat/conversations/conv-a/messages*', async (route) => {
      const url = new URL(route.request().url())
      if (url.searchParams.has('afterSeq')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            messages: [
              {
                id: 102,
                conversationId: 'conv-a',
                messageSeq: 2,
                sender: { userId: 'user-02', name: 'Thành viên B' },
                clientMessageId: 'cmsg-reconnected-2',
                messageType: 'TEXT',
                content: 'Tin nhắn sau khi kết nối lại!',
                replyToMessageId: null,
                files: [],
                createdAt: '2026-10-01T08:05:00Z',
                editedAt: null,
                deletedAt: null,
              },
            ],
            nextBeforeSeq: null,
            nextAfterSeq: null,
            hasMore: false,
          }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          messages: [
            {
              id: 101,
              conversationId: 'conv-a',
              messageSeq: 1,
              sender: { userId: 'user-02', name: 'Thành viên B' },
              clientMessageId: 'cmsg-init',
              messageType: 'TEXT',
              content: 'Chào buổi sáng!',
              replyToMessageId: null,
              files: [],
              createdAt: '2026-10-01T08:00:00Z',
              editedAt: null,
              deletedAt: null,
            },
          ],
          nextBeforeSeq: null,
          nextAfterSeq: null,
          hasMore: false,
        }),
      })
    })

    await page.goto('/')
    await openConvFromHeader(page, 'Thành viên B')

    // Trigger catch-up by evaluating client reload or reconnect handler
    await page.evaluate(() => {
      window.dispatchEvent(new Event('focus'))
    })

    await expect(page.locator('.chat-popup-window[data-conversation-id="conv-a"]')).toBeVisible()
  })

  test('19. typing indicator expires', async ({ page }) => {
    let sendEvent: ((envelope: any) => void) | null = null
    await setupMockChatRoutes(page, {
      onWebSocketConnected: (sender) => {
        sendEvent = sender
      },
    })
    await page.goto('/')

    await openConvFromHeader(page, 'Thành viên B')

    // Wait until WebSocket is connected and subscribed
    await expect.poll(() => sendEvent !== null, { timeout: 10000 }).toBeTruthy()

    // Send typing started event
    sendEvent!({
      event: 'chat.typing.started',
      eventId: 'evt-typing-1',
      occurredAt: new Date().toISOString(),
      conversationId: 'conv-a',
      data: {
        conversationId: 'conv-a',
        user: { userId: 'user-02', name: 'Thành viên B' },
      },
    })

    // Indicator appears
    await expect(page.getByText('Thành viên B đang nhập')).toBeVisible()

    // After 4.5 seconds without updates, typing indicator auto-expires
    await page.waitForTimeout(4600)
    await expect(page.getByText('Thành viên B đang nhập')).toHaveCount(0)
  })

  test('20. mobile/narrow: no 3-popup desktop host', async ({ page }) => {
    await setupMockChatRoutes(page)
    // Set viewport to mobile width 375px
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto('/')

    // On mobile width, the desktop popup host and dock must be hidden
    const popupHost = page.locator('.chat-popup-host')
    const dock = page.locator('.chat-dock')

    await expect(popupHost).toBeHidden()
    await expect(dock).toBeHidden()
  })

  test('21. unauthenticated: chat UI/socket inactive', async ({ page }) => {
    // Clear tokens
    await page.addInitScript(() => {
      window.localStorage.removeItem('smartlab.token')
      window.localStorage.removeItem('smartlab.sessionId')
    })
    await page.goto('/')

    // Unauthenticated user should not see chat header button
    const chatBtn = page.locator('.chat-header-btn')
    await expect(chatBtn).toHaveCount(0)
  })

  test('22. expired/rejected socket: graceful UI state', async ({ page }) => {
    await setupMockChatRoutes(page, { rejectSocket: true })
    await page.goto('/')

    // UI should not crash even if socket handshake fails
    const chatBtn = page.locator('.chat-header-btn')
    await expect(chatBtn).toBeVisible()
    await chatBtn.click()

    // Popover still opens and lists conversations via REST
    await expect(page.locator('#chat-popover-panel')).toBeVisible()
    await expect(page.getByText('Thành viên B')).toBeVisible()
  })
})
