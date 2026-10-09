import { expect, test } from '@playwright/test'

// Live test suite against real Spring Boot backend running on smartlab_chat_it database.
// NO API OR WEBSOCKET MOCKING ALLOWED HERE.

const API_BASE = 'http://127.0.0.1:8080/api/v1.0'

async function loginUser(email: string, password = 'Admin@123456') {
  const res = await fetch(`${API_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!res.ok) {
    throw new Error(`Failed to login ${email}: ${res.status} ${await res.text()}`)
  }
  const data = await res.json()
  return {
    token: data.token as string,
    sessionId: data.sessionId as string,
    email: data.email as string,
  }
}

test.describe('Live Realtime Chat E2E (against backend fdecc04 on smartlab_chat_it)', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 })

  test('1. A -> B realtime message & persistence & refresh persistence', async ({ browser }) => {
    const authA = await loginUser('alice@smartlab.test')
    const authB = await loginUser('bob@smartlab.test')

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
          '17fdd8a1-8783-4348-96d3-68efddb2e0b1', // Bob
          '27968743-cfde-4874-80c2-fb813a4d1f3e', // Carol
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
})
