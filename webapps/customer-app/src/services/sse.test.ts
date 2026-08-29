import { describe, expect, it } from "vitest"

import { parseSseStream } from "@/services/sse"

function sseStream(blocks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const block of blocks) {
        controller.enqueue(encoder.encode(block))
      }
      controller.close()
    },
  })
}

async function collect(stream: ReadableStream<Uint8Array>) {
  const events: Array<{ event: string; data: string }> = []
  for await (const event of parseSseStream(stream)) {
    events.push(event)
  }
  return events
}

describe("parseSseStream", () => {
  it("parses chunk and done events separated by blank lines", async () => {
    const events = await collect(
      sseStream([
        "event:chunk\ndata:您\n\n",
        "event:chunk\ndata:好\n\n",
        'event:done\ndata:{"content":"您好"}\n\n',
      ]),
    )

    expect(events).toEqual([
      { event: "chunk", data: "您" },
      { event: "chunk", data: "好" },
      { event: "done", data: '{"content":"您好"}' },
    ])
  })

  it("joins multi-line data fields with newlines", async () => {
    const events = await collect(sseStream(["event:chunk\ndata:第一行\ndata:第二行\n\n"]))

    expect(events).toEqual([{ event: "chunk", data: "第一行\n第二行" }])
  })

  it("emits a final event without a trailing blank line", async () => {
    const events = await collect(sseStream(["event:done\ndata:尾帧"]))

    expect(events).toEqual([{ event: "done", data: "尾帧" }])
  })

  it("handles frames split across chunk boundaries", async () => {
    const events = await collect(
      sseStream(["event:chunk\nda", "ta:跨块\n\nevent:", "done\ndata:完\n\n"]),
    )

    expect(events).toEqual([
      { event: "chunk", data: "跨块" },
      { event: "done", data: "完" },
    ])
  })

  it("normalizes CRLF line endings", async () => {
    const events = await collect(sseStream(["event:chunk\r\ndata:你好\r\n\r\n"]))

    expect(events).toEqual([{ event: "chunk", data: "你好" }])
  })

  it("normalizes CRLF split across chunk boundaries", async () => {
    const events = await collect(sseStream(["event:chunk\r", "\ndata:你好\r\n\r\n"]))

    expect(events).toEqual([{ event: "chunk", data: "你好" }])
  })

  it("ignores comment lines and events without data", async () => {
    const events = await collect(sseStream([": keep-alive\n\n", "event:ping\n\n"]))

    expect(events).toEqual([])
  })
})
