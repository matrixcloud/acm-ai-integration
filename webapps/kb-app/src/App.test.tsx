import { render, screen } from "@testing-library/react"
import { StrictMode } from "react"
import { afterEach, describe, expect, it, vi } from "vitest"

import App from "@/App"
import { MOCK_LATENCY_MS } from "@/data/mock-kb"

describe("App", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("renders the knowledge base management title and lists knowledge bases", async () => {
    vi.useFakeTimers()

    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )

    expect(screen.getByRole("heading", { name: "知识库管理" })).toBeInTheDocument()

    await vi.advanceTimersByTimeAsync(MOCK_LATENCY_MS + 100)

    expect(screen.getAllByText("售后服务知识库").length).toBeGreaterThan(0)
    expect(screen.getAllByText("订单流程知识库").length).toBeGreaterThan(0)
  })
})
