import { expect, test } from '@playwright/test'

// Live test suite against real Spring Boot backend running on smartlab_chat_it database.
// NO API OR WEBSOCKET MOCKING ALLOWED HERE.

const API_BASE = (process.env.E2E_API_BASE_URL ?? 'http://127.0.0.1:8080/api/v1.0').replace(/\/+$/, '')

type AuthenticatedUser = {
  token: string
  sessionId: string
  email: string
  userId: string
  name: string
  roles: string[]
}

function authorizationHeaders(auth: AuthenticatedUser) {
  return { Authorization: `Bearer ${auth.token}` }
}

async function loginUser(email: string, password = 'Admin@123456'): Promise<AuthenticatedUser> {
  const res = await fetch(`${API_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!res.ok) {
    throw new Error(`Failed to login ${email}: ${res.status} ${await res.text()}`)
  }
  const data = await res.json()
  const profileRes = await fetch(`${API_BASE}/profile`, {
    headers: { Authorization: `Bearer ${data.token}` },
  })
  if (!profileRes.ok) {
    throw new Error(`Failed to load profile for ${email}: ${profileRes.status} ${await profileRes.text()}`)
  }
  const profile = await profileRes.json()
  return {
    token: data.token as string,
    sessionId: data.sessionId as string,
    email: data.email as string,
    userId: profile.userId as string,
    name: profile.name as string,
    roles: profile.roles as string[],
  }
}

async function ensureDirectConversation(auth: AuthenticatedUser, peerUserId: string) {
  const res = await fetch(`${API_BASE}/chat/conversations/direct`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authorizationHeaders(auth),
    },
    body: JSON.stringify({ userId: peerUserId }),
  })
  if (!res.ok) {
    throw new Error(`Failed to ensure direct conversation: ${res.status} ${await res.text()}`)
  }
  return res.json()
}
test.describe('Live Realtime Chat E2E (real backend on isolated smartlab_chat_it)', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 })

  test('1. A -> B realtime message & persistence & refresh persistence', async ({ browser }) => {
    const authA = await loginUser('alice@smartlab.test')
    const authB = await loginUser('bob@smartlab.test')
    await ensureDirectConversation(authA, authB.userId)

    // Context A (Alice)
    const contextA = await browser.newContext()
    const pageA = await contextA.newPage()
    await pageA.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authA)

    // Context B (Bob)
    const contextB = await browser.newContext()
    const pageB = await contextB.newPage()
    await pageB.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authB)

    // Load both users onto homepage
    await pageA.goto('/')
    await pageB.goto('/')

    // Wait for chat headers to be visible
    await expect(pageA.locator('.chat-header-btn')).toBeVisible()
    await expect(pageB.locator('.chat-header-btn')).toBeVisible()

    // Alice opens popover to find Bob
    await pageA.locator('.chat-header-btn').click()
    const popoverA = pageA.locator('#chat-popover-panel')
    await expect(popoverA).toBeVisible()

    // Alice clicks Bob Builder
    const bobItemA = popoverA.locator('.chat-conv-item', { hasText: 'Bob Builder' })
    await expect(bobItemA).toBeVisible()
    await bobItemA.click()

    // Alice has popup window open
    const popupA = pageA.locator('.chat-popup-window')
    await expect(popupA).toBeVisible()
    await expect(popupA.locator('.chat-popup-title')).toHaveText('Bob Builder')

    // Alice types and sends a realtime message
    const uniqueText = `Live message from Alice ${Date.now()}`
    const inputA = popupA.getByRole('textbox', { name: /soạn tin nhắn/i })
    await inputA.fill(uniqueText)
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    // Alice sees message sent
    await expect(popupA.getByText(uniqueText)).toBeVisible()

    // Bob receives realtime notification / popup / dock in Bob's browser!
    // Bob's header unread counter should increase or chat head appear
    await expect.poll(async () => {
      // Bob opens popover to check
      const unreadBadgeB = pageB.locator('.chat-header-btn .notification-count')
      const count = await unreadBadgeB.count()
      return count > 0
    }, { timeout: 15_000 }).toBeTruthy()

    // Bob opens conversation
    await pageB.locator('.chat-header-btn').click()
    const popoverB = pageB.locator('#chat-popover-panel')
    await expect(popoverB).toBeVisible()
    const aliceItemB = popoverB.locator('.chat-conv-item', { hasText: 'Alice Wonder' })
    await expect(aliceItemB).toBeVisible()
    await aliceItemB.click()

    // Bob's popup opens and receives the exact uniqueText
    const popupB = pageB.locator('.chat-popup-window')
    await expect(popupB).toBeVisible()
    await expect(popupB.getByText(uniqueText)).toBeVisible()

    // Refresh Bob => message remains
    await pageB.reload()
    await pageB.locator('.chat-header-btn').click()
    await popoverB.locator('.chat-conv-item', { hasText: 'Alice Wonder' }).click()
    await expect(popupB.getByText(uniqueText)).toBeVisible()

    await contextA.close()
    await contextB.close()
  })

  test('2. Reconnect: buffer, fetch afterSeq, merge with zero duplicates', async ({ browser }) => {
    const authA = await loginUser('alice@smartlab.test')
    const authB = await loginUser('bob@smartlab.test')
    await ensureDirectConversation(authA, authB.userId)

    const contextA = await browser.newContext()
    const pageA = await contextA.newPage()
    await pageA.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authA)

    const contextB = await browser.newContext()
    const pageB = await contextB.newPage()
    await pageB.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authB)

    await pageA.goto('/')
    await pageB.goto('/')

    // Alice opens chat with Bob
    await pageA.locator('.chat-header-btn').click()
    await pageA.locator('#chat-popover-panel .chat-conv-item', { hasText: 'Bob Builder' }).click()
    const popupA = pageA.locator('.chat-popup-window')
    await expect(popupA).toBeVisible()

    // Bob opens chat with Alice
    await pageB.locator('.chat-header-btn').click()
    await pageB.locator('#chat-popover-panel .chat-conv-item', { hasText: 'Alice Wonder' }).click()
    const popupB = pageB.locator('.chat-popup-window')
    await expect(popupB).toBeVisible()

    // Simulate Bob disconnecting (going offline)
    await contextB.setOffline(true)

    // While Bob is offline, Alice sends 3 messages
    const ts = Date.now()
    const msg1 = `CatchUp 1 ${ts}`
    const msg2 = `CatchUp 2 ${ts}`
    const msg3 = `CatchUp 3 ${ts}`

    for (const msg of [msg1, msg2, msg3]) {
      await popupA.getByRole('textbox', { name: /soạn tin nhắn/i }).fill(msg)
      await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()
      await expect(popupA.getByText(msg)).toBeVisible()
      await pageA.waitForTimeout(400)
    }

    // Now restore Bob's network connection
    await contextB.setOffline(false)

    // Trigger focus or wait for reconnect catch-up (which invokes catchUpAfterReconnect / afterSeq)
    await pageB.evaluate(() => window.dispatchEvent(new Event('focus')))

    // Bob must recover all 3 missed messages
    await expect(popupB.getByText(msg1)).toBeVisible({ timeout: 15_000 })
    await expect(popupB.getByText(msg2)).toBeVisible()
    await expect(popupB.getByText(msg3)).toBeVisible()

    // Exactly 1 instance of each message
    await expect(popupB.getByText(msg1)).toHaveCount(1)
    await expect(popupB.getByText(msg2)).toHaveCount(1)
    await expect(popupB.getByText(msg3)).toHaveCount(1)

    // Alice sends a next realtime message after reconnect
    const msg4 = `Next realtime msg ${ts}`
    await popupA.getByRole('textbox', { name: /soạn tin nhắn/i }).fill(msg4)
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    // Appears exactly once in Bob's thread
    await expect(popupB.getByText(msg4)).toBeVisible({ timeout: 10_000 })
    await expect(popupB.getByText(msg4)).toHaveCount(1)

    await contextA.close()
    await contextB.close()
  })

  test('3. Group chat membership & outsider isolation (Alice, Bob, Carol + Outsider David)', async ({ browser }) => {
    const authA = await loginUser('alice@smartlab.test')
    const authB = await loginUser('bob@smartlab.test')
    await ensureDirectConversation(authA, authB.userId)
    const authC = await loginUser('carol@smartlab.test')
    const authD = await loginUser('david@smartlab.test') // Outsider

    // 1. Alice creates a group conversation with Bob & Carol via REST API
    const groupTitle = `Robotics Team ${Date.now()}`
    const groupRes = await fetch(`${API_BASE}/chat/conversations/group`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authA.token}`,
      },
      body: JSON.stringify({
        title: groupTitle,
        memberUserIds: [
          authB.userId,
          authC.userId,
        ],
      }),
    })
    expect(groupRes.status).toBe(201)
    const groupData = await groupRes.json()
    const groupId = groupData.conversationId as string

    // 2. Separate authenticated browser contexts for all 4 users
    const contextA = await browser.newContext()
    const pageA = await contextA.newPage()
    await pageA.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authA)

    const contextB = await browser.newContext()
    const pageB = await contextB.newPage()
    await pageB.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authB)

    const contextC = await browser.newContext()
    const pageC = await contextC.newPage()
    await pageC.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authC)

    const contextD = await browser.newContext()
    const pageD = await contextD.newPage()
    await pageD.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authD)

    // 3. Bob and Carol open & subscribe to group chat in realtime
    await pageA.goto(`/chat?id=${groupId}`)
    await pageB.goto(`/chat?id=${groupId}`)
    await pageC.goto(`/chat?id=${groupId}`)
    await pageD.goto(`/chat?id=${groupId}`)

    await expect(pageA.locator('.chat-page-shell')).toBeVisible()
    await expect(pageB.locator('.chat-page-shell')).toBeVisible()
    await expect(pageC.locator('.chat-page-shell')).toBeVisible()

    // 4. Alice sends a unique message into the group
    const groupMsg = `Confidential lab notes ${Date.now()}`
    const inputA = pageA.getByRole('textbox', { name: /soạn tin nhắn/i })
    await inputA.fill(groupMsg)
    await pageA.getByRole('button', { name: /gửi tin nhắn/i }).click()

    // 5. Assert:
    // - Alice sees canonical persisted message exactly once
    await expect(pageA.locator('.chat-message-list').getByText(groupMsg)).toBeVisible()
    await expect(pageA.locator('.chat-message-list').getByText(groupMsg)).toHaveCount(1)

    // - Bob receives message in realtime exactly once
    await expect(pageB.locator('.chat-message-list').getByText(groupMsg)).toBeVisible({ timeout: 10_000 })
    await expect(pageB.locator('.chat-message-list').getByText(groupMsg)).toHaveCount(1)

    // - Carol receives message in realtime exactly once
    await expect(pageC.locator('.chat-message-list').getByText(groupMsg)).toBeVisible({ timeout: 10_000 })
    await expect(pageC.locator('.chat-message-list').getByText(groupMsg)).toHaveCount(1)

    // - Outsider David sees an explicit inaccessible state, without probing history/read endpoints
    await expect(pageD.getByText('Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.')).toBeVisible()

    // - Outsider David does not receive message
    await pageD.waitForTimeout(1000)
    await expect(pageD.getByText(groupMsg)).toHaveCount(0)

    // - Outsider David GET history returns 403 or 404 according to backend contract
    const outsiderAttempt = await fetch(`${API_BASE}/chat/conversations/${groupId}/messages`, {
      headers: { Authorization: `Bearer ${authD.token}` },
    })
    expect([403, 404]).toContain(outsiderAttempt.status)

    await contextA.close()
    await contextB.close()
    await contextC.close()
    await contextD.close()
  })

  test('4. Read watermark advancement & unread consistency', async ({ browser }) => {
    const authA = await loginUser('alice@smartlab.test')
    const authB = await loginUser('bob@smartlab.test')
    await ensureDirectConversation(authA, authB.userId)

    // Context A (Alice)
    const contextA = await browser.newContext()
    const pageA = await contextA.newPage()
    await pageA.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authA)

    await pageA.goto('/')
    await pageA.locator('.chat-header-btn').click()
    await pageA.locator('#chat-popover-panel .chat-conv-item', { hasText: 'Bob Builder' }).click()
    const popupA = pageA.locator('.chat-popup-window')
    await expect(popupA).toBeVisible()

    // Alice sends a new message to Bob while Bob's chat window is closed
    const readTestMsg = `Read test message ${Date.now()}`
    await popupA.getByRole('textbox', { name: /soạn tin nhắn/i }).fill(readTestMsg)
    await popupA.getByRole('button', { name: /gửi tin nhắn/i }).click()
    await expect(popupA.getByText(readTestMsg)).toBeVisible()
    // Sending should advance Alice's own persisted watermark, not count her message as unread.
    await expect.poll(async () => {
      const response = await fetch(`${API_BASE}/chat/conversations`, { headers: authorizationHeaders(authA) })
      const list = await response.json()
      const direct = list.find((item: { displayName: string }) => item.displayName === 'Bob Builder')
      return direct?.unreadCount === 0 && direct?.lastReadSeq === direct?.lastMessageSeq
    }, { timeout: 10_000 }).toBeTruthy()

    // Bob opens browser
    const contextB = await browser.newContext()
    const pageB = await contextB.newPage()
    await pageB.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, authB)

    await pageB.goto('/')
    const unreadBadgeB = pageB.locator('.chat-header-btn .notification-count')
    await expect(unreadBadgeB).toBeVisible({ timeout: 10_000 })
    const initialUnread = Number(await unreadBadgeB.innerText())
    expect(initialUnread).toBeGreaterThan(0)

    // Bob opens conversation with Alice to view the message
    await pageB.locator('.chat-header-btn').click()
    await pageB.locator('#chat-popover-panel .chat-conv-item', { hasText: 'Alice Wonder' }).click()

    // Viewing the conversation clears the unread count for this conversation
    await expect(pageB.locator('.chat-popup-window')).toBeVisible()
    await expect(pageB.locator('.chat-popup-window').getByText(readTestMsg)).toBeVisible()

    // Unread count clears or decreases
    await expect.poll(async () => {
      const count = await unreadBadgeB.count()
      if (count === 0) return true
      const current = Number(await unreadBadgeB.innerText())
      return current < initialUnread
    }, { timeout: 10_000 }).toBeTruthy()

    // Verify backend watermark advanced via REST
    await expect.poll(async () => {
      const convListRes = await fetch(`${API_BASE}/chat/conversations`, {
        headers: { Authorization: `Bearer ${authB.token}` },
      })
      const convList = await convListRes.json()
      const conv = convList.find((c: any) => c.displayName === 'Alice Wonder')
      return conv && conv.unreadCount === 0 && conv.lastReadSeq === conv.lastMessageSeq
    }, { timeout: 10_000 }).toBeTruthy()

    await contextA.close()
    await contextB.close()
  })
  test('5. Real role policy: owner, group admin, member and global admin', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const carol = await loginUser('carol@smartlab.test')
    const david = await loginUser('david@smartlab.test')
    expect(alice.roles).toContain('LEADER')
    expect(bob.roles).toContain('MEMBER')
    expect(david.roles).toContain('ADMIN')

    const create = await fetch(`${API_BASE}/chat/conversations/group`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ title: `Role policy ${Date.now()}`, memberUserIds: [bob.userId, carol.userId] }),
    })
    expect(create.status).toBe(201)
    const groupId = (await create.json()).conversationId as string
    const groupUrl = `${API_BASE}/chat/conversations/${groupId}`

    const aliceContext = await browser.newContext()
    const alicePage = await aliceContext.newPage()
    await alicePage.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, alice)
    await alicePage.goto(`/chat?id=${groupId}`)
    await expect(alicePage.getByRole('heading', { name: /Role policy/ })).toBeVisible()
    await expect(alicePage.getByRole('button', { name: 'Rời nhóm', exact: true })).toBeVisible()
    await alicePage.getByRole('button', { name: 'Rời nhóm', exact: true }).click()
    await expect(alicePage.getByRole('dialog', { name: 'Chỉ định trưởng nhóm kế nhiệm' })).toBeVisible()
    await alicePage.getByRole('dialog', { name: 'Chỉ định trưởng nhóm kế nhiệm' }).getByRole('button', { name: 'Hủy' }).click()

    const promoteBob = await fetch(`${groupUrl}/members/${bob.userId}/role`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ role: 'ADMIN' }),
    })
    expect(promoteBob.status).toBe(200)
    const promoteCarol = await fetch(`${groupUrl}/members/${carol.userId}/role`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ role: 'ADMIN' }),
    })
    expect(promoteCarol.status).toBe(200)

    const bobCannotPromote = await fetch(`${groupUrl}/members/${carol.userId}/role`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(bob) },
      body: JSON.stringify({ role: 'MEMBER' }),
    })
    expect(bobCannotPromote.status).toBe(403)
    const bobCannotRemovePeer = await fetch(`${groupUrl}/members/${carol.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(bob),
    })
    expect(bobCannotRemovePeer.status).toBe(403)
    const ownerCannotLeave = await fetch(`${groupUrl}/members/${alice.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(alice),
    })
    expect(ownerCannotLeave.status).toBe(409)

    const bobMessage = await fetch(`${groupUrl}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(bob) },
      body: JSON.stringify({ clientMessageId: crypto.randomUUID(), messageType: 'TEXT', content: 'Authorship integrity check' }),
    })
    expect(bobMessage.status).toBe(201)
    const messageId = (await bobMessage.json()).id as number
    const ownerCannotRewrite = await fetch(`${groupUrl}/messages/${messageId}`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ content: 'Forged authorship' }),
    })
    expect(ownerCannotRewrite.status).toBe(403)
    const outsiderCannotModerate = await fetch(`${groupUrl}/messages/${messageId}`, {
      method: 'DELETE', headers: authorizationHeaders(david),
    })
    expect([403, 404]).toContain(outsiderCannotModerate.status)
    const ownerCanModerate = await fetch(`${groupUrl}/messages/${messageId}`, {
      method: 'DELETE', headers: authorizationHeaders(alice),
    })
    expect(ownerCanModerate.status).toBe(204)

    const downgradeCarol = await fetch(`${groupUrl}/members/${carol.userId}/role`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ role: 'MEMBER' }),
    })
    expect(downgradeCarol.status).toBe(200)
    const bobCanRemoveMember = await fetch(`${groupUrl}/members/${carol.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(bob),
    })
    expect(bobCanRemoveMember.status).toBe(204)
    const carolCannotRead = await fetch(`${groupUrl}/messages`, { headers: authorizationHeaders(carol) })
    expect(carolCannotRead.status).toBe(404)

    const bobCanLeave = await fetch(`${groupUrl}/members/${bob.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(bob),
    })
    expect(bobCanLeave.status).toBe(204)
    await aliceContext.close()
  })

  test('6. Removed participant loses the open popup and cached thread immediately', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const created = await fetch(`${API_BASE}/chat/conversations/group`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ title: `Revocation ${Date.now()}`, memberUserIds: [bob.userId] }),
    })
    expect(created.status).toBe(201)
    const group = await created.json()
    const context = await browser.newContext()
    const page = await context.newPage()
    await page.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, bob)
    await page.goto('/')
    await page.locator('.chat-header-btn').click()
    await page.locator(`#chat-popover-panel .chat-conv-item`, { hasText: group.title }).click()
    await expect(page.locator(`.chat-popup-window[data-conversation-id="${group.conversationId}"]`)).toBeVisible()

    const removed = await fetch(`${API_BASE}/chat/conversations/${group.conversationId}/members/${bob.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(alice),
    })
    expect(removed.status).toBe(204)
    await expect(page.locator(`.chat-popup-window[data-conversation-id="${group.conversationId}"]`)).toHaveCount(0)
    await page.locator('.chat-header-btn').click()
    await expect(page.locator('#chat-popover-panel .chat-conv-item', { hasText: group.title })).toHaveCount(0)
    const history = await fetch(`${API_BASE}/chat/conversations/${group.conversationId}/messages`, { headers: authorizationHeaders(bob) })
    expect(history.status).toBe(404)
    await context.close()
  })

  test('7. Open recipient thread acknowledges the exact realtime sequence', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const direct = await ensureDirectConversation(alice, bob.userId)
    const context = await browser.newContext()
    const page = await context.newPage()
    await page.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, bob)
    await page.goto(`/chat?id=${direct.conversationId}`)
    await expect(page.locator('.chat-page-main .chat-composer-input')).toBeVisible()
    const content = `Open-thread watermark ${Date.now()}`
    const sent = await fetch(`${API_BASE}/chat/conversations/${direct.conversationId}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ clientMessageId: crypto.randomUUID(), messageType: 'TEXT', content }),
    })
    expect(sent.status).toBe(201)
    const message = await sent.json()
    await expect(page.locator('.chat-page-main .chat-message-list').getByText(content)).toBeVisible()
    await expect.poll(async () => {
      const response = await fetch(`${API_BASE}/chat/conversations`, { headers: authorizationHeaders(bob) })
      const list = await response.json()
      const current = list.find((item: { conversationId: string }) => item.conversationId === direct.conversationId)
      return current?.lastReadSeq >= message.messageSeq && current?.unreadCount === 0
    }, { timeout: 10_000 }).toBeTruthy()
    await context.close()
  })

  test('8. Mobile real user opens conversation on the full chat page', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const direct = await ensureDirectConversation(alice, bob.userId)
    const context = await browser.newContext({ viewport: { width: 375, height: 667 } })
    const page = await context.newPage()
    await page.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, alice)
    await page.goto('/')
    await page.locator('.chat-header-btn').click()
    await page.locator('#chat-popover-panel .chat-conv-item', { hasText: bob.name }).click()
    await expect(page).toHaveURL(new RegExp(`/chat\\?id=${direct.conversationId}$`))
    await expect(page.locator('.chat-page-main .chat-composer-input')).toBeVisible()
    await expect(page.locator('.chat-popup-host')).toBeHidden()
    await context.close()
  })

  test('9. Owner chooses successor, then successor dissolves into searchable read-only history', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const carol = await loginUser('carol@smartlab.test')
    const title = `Lifecycle ${Date.now()}`
    const created = await fetch(`${API_BASE}/chat/conversations/group`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ title, memberUserIds: [bob.userId, carol.userId] }),
    })
    expect(created.status).toBe(201)
    const group = await created.json()
    const url = `${API_BASE}/chat/conversations/${group.conversationId}`
    const content = `Archive history ${Date.now()}`
    expect((await fetch(`${url}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ clientMessageId: crypto.randomUUID(), messageType: 'TEXT', content }),
    })).status).toBe(201)
    expect((await fetch(`${url}/members/${carol.userId}`, {
      method: 'DELETE', headers: authorizationHeaders(carol),
    })).status).toBe(204)

    const aliceContext = await browser.newContext()
    const alicePage = await aliceContext.newPage()
    await alicePage.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, alice)
    await alicePage.goto(`/chat?id=${group.conversationId}`)
    await expect(alicePage.locator('.chat-page-main .chat-message-list').getByText(content)).toBeVisible()
    await alicePage.getByRole('button', { name: 'Rời nhóm', exact: true }).click()
    const leaveDialog = alicePage.getByRole('dialog', { name: 'Chỉ định trưởng nhóm kế nhiệm' })
    await expect(leaveDialog).toBeVisible()
    await leaveDialog.getByLabel('Chọn trưởng nhóm kế nhiệm').selectOption(bob.userId)
    await leaveDialog.getByRole('button', { name: 'Xác nhận rời nhóm' }).click()
    await expect(leaveDialog).toHaveCount(0)
    expect((await fetch(`${url}/messages`, { headers: authorizationHeaders(alice) })).status).toBe(404)

    const bobContext = await browser.newContext()
    const bobPage = await bobContext.newPage()
    await bobPage.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, bob)
    await bobPage.goto(`/chat?id=${group.conversationId}`)
    await expect(bobPage.locator('.chat-page-main .chat-message-list').getByText(content)).toBeVisible()
    await bobPage.getByRole('button', { name: 'Giải tán nhóm' }).click()
    const dissolveDialog = bobPage.getByRole('dialog', { name: 'Xác nhận giải tán nhóm' })
    await expect(dissolveDialog).toContainText('lưu trữ')
    await dissolveDialog.getByRole('button', { name: 'Xác nhận giải tán' }).click()
    await expect(bobPage.getByText('Nhóm đã giải tán. Lịch sử được lưu trữ chỉ đọc.')).toBeVisible()
    await expect(bobPage.locator('.chat-page-main .chat-composer-input')).toHaveCount(0)
    await expect(bobPage.locator('.chat-page-sidebar .chat-conv-item', { hasText: title })).toBeVisible()
    await bobPage.reload()
    await expect(bobPage.locator('.chat-page-main .chat-message-list').getByText(content)).toBeVisible()
    await bobPage.getByRole('button', { name: 'Đã lưu trữ' }).click()
    await expect(bobPage.locator('.chat-page-sidebar .chat-conv-item', { hasText: title })).toBeVisible()
    const closed = await (await fetch(`${API_BASE}/chat/conversations`, { headers: authorizationHeaders(bob) })).json()
    expect(closed.find((item: { conversationId: string }) => item.conversationId === group.conversationId))
      .toMatchObject({ archived: true, closedAt: expect.any(String) })
    expect((await fetch(`${url}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(bob) },
      body: JSON.stringify({ clientMessageId: crypto.randomUUID(), messageType: 'TEXT', content: 'blocked' }),
    })).status).toBe(409)
    expect((await fetch(`${url}/messages`, { headers: authorizationHeaders(carol) })).status).toBe(404)
    await aliceContext.close()
    await bobContext.close()
  })

  test('10. Mobile owner opens group actions and confirms dissolution', async ({ browser }) => {
    const alice = await loginUser('alice@smartlab.test')
    const bob = await loginUser('bob@smartlab.test')
    const created = await fetch(`${API_BASE}/chat/conversations/group`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authorizationHeaders(alice) },
      body: JSON.stringify({ title: `Mobile lifecycle ${Date.now()}`, memberUserIds: [bob.userId] }),
    })
    expect(created.status).toBe(201)
    const group = await created.json()
    const context = await browser.newContext({ viewport: { width: 375, height: 667 } })
    const page = await context.newPage()
    await page.addInitScript(({ token, sessionId }) => {
      localStorage.setItem('smartlab.token', token)
      localStorage.setItem('smartlab.sessionId', sessionId)
    }, alice)
    await page.goto(`/chat?id=${group.conversationId}`)
    await expect(page.locator('.chat-page-main .chat-composer-input')).toBeVisible()
    await page.getByRole('button', { name: 'Bật tắt thông tin cuộc trò chuyện' }).click()
    const details = page.getByRole('complementary', { name: 'Chi tiết cuộc trò chuyện' })
    await expect(details).toBeVisible()
    await expect(details.getByRole('button', { name: 'Rời nhóm', exact: true })).toBeVisible()
    await details.getByRole('button', { name: 'Giải tán nhóm' }).click()
    const dialog = page.getByRole('dialog', { name: 'Xác nhận giải tán nhóm' })
    await dialog.getByRole('button', { name: 'Hủy' }).click()
    await expect(page.locator('.chat-page-main .chat-composer-input')).toBeVisible()
    await details.getByRole('button', { name: 'Giải tán nhóm' }).click()
    await dialog.getByRole('button', { name: 'Xác nhận giải tán' }).click()
    await expect(page.locator('.chat-page-main .chat-composer-input')).toHaveCount(0)
    await expect(page.getByText('Nhóm đã giải tán. Lịch sử được lưu trữ chỉ đọc.')).toBeVisible()
    await context.close()
  })

})
