import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ChatMessage } from "@/components/chat/chat-message"

const CREATED_AT = new Date("2026-08-28T08:00:00+08:00")

describe("ChatMessage", () => {
  it("preserves line breaks and wraps long customer content", () => {
    render(
      <ChatMessage
        message={{ id: "multiline-message", role: "customer", content: "第一行\n第二行", createdAt: CREATED_AT }}
      />,
    )

    expect(screen.getByText(/第一行/)).toHaveClass("whitespace-pre-wrap", "break-words")
  })

  it("renders markdown in support messages", () => {
    render(
      <ChatMessage
        message={{
          id: "markdown-message",
          role: "support",
          content: "**加粗** 与 [链接](https://example.com)",
          createdAt: CREATED_AT,
        }}
      />,
    )

    expect(screen.getByText("加粗").tagName).toBe("STRONG")
    expect(screen.getByRole("link", { name: "链接" })).toHaveAttribute(
      "href",
      "https://example.com",
    )
  })

  it("renders customer messages as literal text", () => {
    const { container } = render(
      <ChatMessage
        message={{
          id: "customer-markdown",
          role: "customer",
          content: "**加粗** [链接](https://example.com)",
          createdAt: CREATED_AT,
        }}
      />,
    )

    expect(screen.getByText("**加粗** [链接](https://example.com)")).toBeInTheDocument()
    expect(container.querySelector("strong")).toBeNull()
    expect(container.querySelector("a")).toBeNull()
  })

  it("does not render raw HTML in support messages", () => {
    const { container } = render(
      <ChatMessage
        message={{
          id: "html-message",
          role: "support",
          content: '<script>alert(1)</script><img src=x onerror=alert(1)>',
          createdAt: CREATED_AT,
        }}
      />,
    )

    expect(container.querySelector("script")).toBeNull()
    expect(container.querySelector("img")).toBeNull()
  })

  it("neutralizes javascript: links in support messages", () => {
    const { container } = render(
      <ChatMessage
        message={{
          id: "javascript-message",
          role: "support",
          content: "[点我](javascript:alert(1))",
          createdAt: CREATED_AT,
        }}
      />,
    )

    const href = container.querySelector("a")?.getAttribute("href") ?? ""
    expect(href.toLowerCase().startsWith("javascript:")).toBe(false)
  })

  it("renders streaming reply as plain text when markdown is disabled", () => {
    const { container } = render(
      <ChatMessage
        message={{ id: "streaming", role: "support", content: "**进行中**", createdAt: CREATED_AT }}
        renderMarkdown={false}
      />,
    )

    expect(screen.getByText("**进行中**")).toBeInTheDocument()
    expect(container.querySelector("strong")).toBeNull()
  })
})