import {
  DEFAULT_MOCK_REPLY,
  MOCK_REPLY_DELAY_MS,
  MOCK_REPLY_RULES,
} from "@/data/mock-chat"

export async function getMockReply(message: string): Promise<string> {
  const normalizedMessage = message.trim()

  if (!normalizedMessage) {
    throw new Error("Mock 客服回复要求消息不能为空")
  }

  const matchedRule = MOCK_REPLY_RULES.find((rule) =>
    rule.keywords.some((keyword) => normalizedMessage.includes(keyword)),
  )

  await new Promise((resolve) => window.setTimeout(resolve, MOCK_REPLY_DELAY_MS))

  return matchedRule?.reply ?? DEFAULT_MOCK_REPLY
}
