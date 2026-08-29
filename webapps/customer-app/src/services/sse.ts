export type SseEvent = {
  event: string
  data: string
}

/**
 * Parses a binary SSE stream into events. Frames are separated by a blank line and multi-line
 * `data:` fields are joined with "\n" per the SSE spec. CRLF line endings are normalized before
 * frame detection (payload-embedded CR is not expected from our server); a final event without a
 * trailing blank line is still emitted.
 */
export async function* parseSseStream(
  stream: ReadableStream<Uint8Array>,
): AsyncGenerator<SseEvent> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ""

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, "\n")

      let boundary = buffer.indexOf("\n\n")
      while (boundary !== -1) {
        const parsed = parseSseBlock(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        if (parsed) yield parsed
        boundary = buffer.indexOf("\n\n")
      }
    }

    buffer += decoder.decode()
    const tail = parseSseBlock(buffer)
    if (tail) yield tail
  } finally {
    reader.releaseLock()
  }
}

function parseSseBlock(block: string): SseEvent | null {
  let event = "message"
  const dataLines: string[] = []

  for (const rawLine of block.split("\n")) {
    const line = rawLine.replace(/\r$/, "")
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim()
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).replace(/^ /, ""))
    }
    // comment (":...") and unknown-field lines are ignored per the SSE spec
  }

  if (dataLines.length === 0) return null
  return { event, data: dataLines.join("\n") }
}
