import { act, fireEvent, render, screen } from "@testing-library/react"
import { StrictMode } from "react"
import { afterEach, describe, expect, it, vi } from "vitest"

import App from "@/App"
import { MOCK_REPLY_DELAY_MS } from "@/data/mock-chat"

describe("App", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("completes a Mock reply while running in StrictMode", async () => {
    vi.useFakeTimers()

    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )

    fireEvent.click(screen.getByRole("button", { name: "怎么查询订单进度？" }))
    expect(screen.getByRole("status", { name: "客服正在输入" })).toBeInTheDocument()
    expect(screen.getByPlaceholderText("正在等待客服回复…")).toBeDisabled()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MOCK_REPLY_DELAY_MS)
    })

    expect(screen.getByText(/你可以在「我的订单」中查看最新进度/)).toBeInTheDocument()
    expect(screen.queryByRole("status", { name: "客服正在输入" })).not.toBeInTheDocument()
  })
})
