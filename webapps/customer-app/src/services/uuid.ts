// crypto.randomUUID 仅限 Secure Context（HTTPS / localhost）；HTTP 公网部署下为
// undefined，退回基于 getRandomValues（所有上下文可用）的 UUIDv4。
const supportsRandomUUID = typeof crypto.randomUUID === "function"

export function newUuid(): string {
  return supportsRandomUUID ? crypto.randomUUID() : uuidV4FromRandomBytes()
}

function uuidV4FromRandomBytes(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("")
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}