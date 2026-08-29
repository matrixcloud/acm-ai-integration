import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { FeedbackCard } from "@/components/chat/feedback-card"

describe("FeedbackCard", () => {
  it.each([
    ["satisfied", "已解决"],
    ["unsatisfied", "未解决"],
  ] as const)("shows the submitted %s result", (rating, label) => {
    render(<FeedbackCard onRate={vi.fn()} rating={rating} />)

    expect(screen.getByText(`你选择了：${label}`)).toBeInTheDocument()
  })
})
