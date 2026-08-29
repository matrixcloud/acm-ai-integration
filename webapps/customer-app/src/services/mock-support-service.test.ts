import { afterEach, describe, expect, it, vi } from "vitest"

import { DEFAULT_MOCK_REPLY, MOCK_REPLY_DELAY_MS } from "@/data/mock-chat"
import { getMockReply } from "@/services/mock-support-service"

describe("getMockReply", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("returns the matched reply after the configured delay", async () => {
    vi.useFakeTimers()

    const replyPromise = getMockReply("我想查询订单物流")
    await vi.advanceTimersByTimeAsync(MOCK_REPLY_DELAY_MS)

    await expect(replyPromise).resolves.toContain("我的订单")
  })

  it("returns the explicit fallback for an unknown question", async () => {
    vi.useFakeTimers()

    const replyPromise = getMockReply("今天天气怎么样")
    await vi.advanceTimersByTimeAsync(MOCK_REPLY_DELAY_MS)

    await expect(replyPromise).resolves.toBe(DEFAULT_MOCK_REPLY)
  })

  it("fails fast when the message is empty", async () => {
    await expect(getMockReply("   ")).rejects.toThrow("消息不能为空")
  })
})
