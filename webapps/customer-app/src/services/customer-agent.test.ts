import { afterEach, describe, expect, it, vi } from "vitest"

import {
  CustomerServiceError,
  createConversation,
  fetchQuickQuestions,
  streamAssistantReply,
  submitFeedback,
  toChatMessage,
  toConversationStatus,
  toFeedbackRating,
} from "@/services/customer-agent"

describe("customer-agent client", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubFetch(response: Response) {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response))
  }

  function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    })
  }

  function sseResponse(body: string, status = 200): Response {
    return new Response(body, {
      status,
      headers: { "Content-Type": "text/event-stream" },
    })
  }

  it("sends API-Version and Idempotency-Key headers on commands", async () => {
    stubFetch(jsonResponse(201, { conversationNo: "CON-1", status: "ACTIVE" }))
    const fetchMock = vi.mocked(fetch)

    await createConversation("customer-001")

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe("/conversations")
    const headers = new Headers(init?.headers)
    expect(headers.get("API-Version")).toBe("1")
    expect(headers.get("Idempotency-Key")).toBeTruthy()
  })

  it("does not send Idempotency-Key on read-only requests", async () => {
    stubFetch(jsonResponse(200, []))
    const fetchMock = vi.mocked(fetch)

    await fetchQuickQuestions()

    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
    expect(headers.get("API-Version")).toBe("1")
    expect(headers.get("Idempotency-Key")).toBeNull()
  })

  it("streams chunks and resolves with the persisted thread from the done event", async () => {
    stubFetch(
      sseResponse(
        "event:chunk\ndata:您\n\nevent:chunk\ndata:好\n\n" +
          'event:done\ndata:{"conversationNo":"CON-1","messages":[{"id":2,"seqNo":2,"role":"AGENT","content":"您好"}]}\n\n',
      ),
    )
    const fetchMock = vi.mocked(fetch)
    const chunks: string[] = []

    const thread = await streamAssistantReply("CON-1", "你好", (token) => chunks.push(token))

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe("/conversations/CON-1/messages")
    const headers = new Headers(init?.headers)
    expect(headers.get("Accept")).toBe("text/event-stream")
    expect(headers.get("API-Version")).toBe("1")
    expect(headers.get("Idempotency-Key")).toBeTruthy()
    expect(chunks).toEqual(["您", "好"])
    expect(thread.conversationNo).toBe("CON-1")
    expect(thread.messages).toHaveLength(1)
    expect(thread.messages[0].content).toBe("您好")
  })

  it("rejects with the error event code when the stream reports a business failure", async () => {
    stubFetch(
      sseResponse(
        'event:error\ndata:{"code":"CONVERSATION_NOT_ACTIVE","detail":"Conversation is not active"}\n\n',
      ),
    )

    const error = await streamAssistantReply("CON-1", "你好", () => {}).catch(
      (cause: unknown) => cause,
    )
    expect(error).toBeInstanceOf(CustomerServiceError)
    expect((error as CustomerServiceError).code).toBe("CONVERSATION_NOT_ACTIVE")
    expect((error as CustomerServiceError).message).toBe("Conversation is not active")
  })

  it("rejects when the stream ends without a done event", async () => {
    stubFetch(sseResponse("event:chunk\ndata:写到一半\n\n"))

    const error = await streamAssistantReply("CON-1", "你好", () => {}).catch(
      (cause: unknown) => cause,
    )
    expect(error).toBeInstanceOf(CustomerServiceError)
    expect((error as CustomerServiceError).code).toBe("INCOMPLETE_STREAM")
  })

  it("parses Problem Details into CustomerServiceError", async () => {
    stubFetch(
      jsonResponse(409, {
        code: "CONVERSATION_NOT_ACTIVE",
        detail: "Conversation C-1 is not active",
        title: "Conversation not active",
      }),
    )

    const error = await streamAssistantReply("C-1", "你好", () => {}).catch(
      (cause: unknown) => cause,
    )
    expect(error).toBeInstanceOf(CustomerServiceError)
    expect((error as CustomerServiceError).code).toBe("CONVERSATION_NOT_ACTIVE")
    expect((error as CustomerServiceError).httpStatus).toBe(409)
    expect((error as CustomerServiceError).message).toBe("Conversation C-1 is not active")
  })

  it("falls back to a generic message when the error body is not JSON", async () => {
    stubFetch(new Response("gateway timeout", { status: 502 }))

    const error = await streamAssistantReply("C-1", "你好", () => {}).catch(
      (cause: unknown) => cause,
    )
    expect(error).toBeInstanceOf(CustomerServiceError)
    expect((error as CustomerServiceError).code).toBe("HTTP_ERROR")
    expect((error as CustomerServiceError).httpStatus).toBe(502)
    expect((error as CustomerServiceError).message).toContain("502")
  })

  it("maps backend roles to frontend message roles", () => {
    expect(
      toChatMessage({
        id: 1,
        seqNo: 1,
        role: "CUSTOMER",
        content: "你好",
        createdAt: "2026-08-29T12:00:00",
      }).role,
    ).toBe("customer")
    expect(
      toChatMessage({
        id: 2,
        seqNo: 2,
        role: "AGENT",
        content: "您好",
        createdAt: "2026-08-29T12:00:01",
      }).role,
    ).toBe("support")
    expect(
      toChatMessage({
        id: 2,
        seqNo: 2,
        role: "AGENT",
        content: "您好",
        createdAt: "2026-08-29T12:00:01",
      }).createdAt,
    ).toEqual(new Date("2026-08-29T12:00:01"))
  })

  it("round-trips feedback ratings", () => {
    expect(toFeedbackRating("SATISFIED")).toBe("satisfied")
    expect(toFeedbackRating("DISSATISFIED")).toBe("unsatisfied")

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { status: "ENDED" }))
    vi.stubGlobal("fetch", fetchMock)

    void submitFeedback("C-1", "satisfied", "回复很快")
    const body = JSON.parse(fetchMock.mock.calls[0]?.[1]?.body as string)
    expect(body.rating).toBe("SATISFIED")
    expect(body.comment).toBe("回复很快")
  })

  it("maps conversation statuses", () => {
    expect(toConversationStatus("ACTIVE")).toBe("active")
    expect(toConversationStatus("AWAITING_FEEDBACK")).toBe("awaiting-feedback")
    expect(toConversationStatus("ENDED")).toBe("ended")
  })
})