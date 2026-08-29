import type { ChatMessage } from "@/types/chat"

export const QUICK_QUESTIONS = ["怎么查询订单进度？", "如何申请退款？", "怎样修改收货地址？"] as const

export const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: "welcome-message",
    role: "support",
    content: "你好，我是 ACM 智能客服小安。很高兴为你服务，请问有什么可以帮你？",
    createdAt: new Date(),
  },
]

type ReplyRule = {
  keywords: readonly string[]
  reply: string
}

export const MOCK_REPLY_RULES: readonly ReplyRule[] = [
  {
    keywords: ["订单", "进度", "物流"],
    reply: "你可以在「我的订单」中查看最新进度。订单发货后，物流单号和配送状态会同步显示。需要我继续帮你了解其他问题吗？",
  },
  {
    keywords: ["退款", "退货", "取消"],
    reply: "如果订单符合售后条件，可以在订单详情中选择「申请售后」。提交后我们通常会在 1 个工作日内完成审核。",
  },
  {
    keywords: ["地址", "收货"],
    reply: "订单发货前可以尝试在订单详情中修改收货地址；如果已经发货，建议联系配送方协商变更。",
  },
  {
    keywords: ["人工", "客服"],
    reply: "当前是功能演示环境，暂未接入人工坐席。我已经记录你的需求，正式接入后可为你转接人工客服。",
  },
]

export const DEFAULT_MOCK_REPLY =
  "我明白你的问题了。当前页面使用 Mock 数据进行功能演示，暂时无法查询真实业务信息。你可以继续描述问题，我会尽力为你解答。"

export const MOCK_REPLY_DELAY_MS = 900
