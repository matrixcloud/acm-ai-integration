import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ChatMessage } from "@/components/chat/chat-message"

describe("ChatMessage", () => {
  it("preserves line breaks and wraps long content", () => {
    render(
      <ChatMessage
        message={{
          id: "multiline-message",
          role: "customer",
          content: "第一行\n第二行",
          createdAt: new Date("2026-08-28T08:00:00+08:00"),
        }}
      />,
    )

    expect(screen.getByText(/第一行/)).toHaveClass("whitespace-pre-wrap", "break-words")
  })
})
