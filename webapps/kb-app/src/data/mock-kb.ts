import type {
  EvalRunReport,
  EvalSuite,
  KbDocument,
  KnowledgeBase,
  SearchResult,
} from "@/types/kb"

export const MOCK_KNOWLEDGE_BASES: KnowledgeBase[] = [
  {
    id: "kb-001",
    name: "售后服务知识库",
    status: "active",
    docCount: 3,
    createdAt: "2026-08-20T09:12:00.000Z",
  },
  {
    id: "kb-002",
    name: "订单流程知识库",
    status: "active",
    docCount: 2,
    createdAt: "2026-08-22T14:30:00.000Z",
  },
  {
    id: "kb-003",
    name: "产品规格知识库",
    status: "archived",
    docCount: 1,
    createdAt: "2026-08-25T11:05:00.000Z",
  },
]

export const MOCK_DOCUMENTS: Record<string, KbDocument[]> = {
  "kb-001": [
    {
      id: "doc-0011",
      name: "退款政策.md",
      status: "ready",
      chunkCount: 12,
      createdAt: "2026-08-20T09:15:00.000Z",
    },
    {
      id: "doc-0012",
      name: "退货流程.txt",
      status: "ready",
      chunkCount: 8,
      createdAt: "2026-08-21T10:20:00.000Z",
    },
    {
      id: "doc-0013",
      name: "售后时效说明.md",
      status: "processing",
      chunkCount: 0,
      createdAt: "2026-08-29T08:40:00.000Z",
    },
  ],
  "kb-002": [
    {
      id: "doc-0021",
      name: "下单流程.md",
      status: "ready",
      chunkCount: 6,
      createdAt: "2026-08-22T14:35:00.000Z",
    },
    {
      id: "doc-0022",
      name: "物流跟踪说明.txt",
      status: "ready",
      chunkCount: 10,
      createdAt: "2026-08-23T16:10:00.000Z",
    },
  ],
  "kb-003": [
    {
      id: "doc-0031",
      name: "产品参数总览.md",
      status: "ready",
      chunkCount: 15,
      createdAt: "2026-08-25T11:08:00.000Z",
    },
  ],
}

type SearchRule = {
  keywords: readonly string[]
  results: SearchResult[]
}

export const MOCK_SEARCH_RULES: readonly SearchRule[] = [
  {
    keywords: ["退款", "退货", "售后"],
    results: [
      {
        content: "符合售后条件的订单可在订单详情中选择「申请售后」，提交后通常 1 个工作日内完成审核。",
        score: 0.92,
        documentName: "退款政策.md",
      },
      {
        content: "退货商品需保持原包装完好，物流回收后由仓库验收，验收通过即发起退款。",
        score: 0.86,
        documentName: "退货流程.txt",
      },
      {
        content: "售后时效一般为 7 天无理由退货、15 天质量问题换货，超出时效需联系客服协商。",
        score: 0.78,
        documentName: "售后时效说明.md",
      },
    ],
  },
  {
    keywords: ["订单", "物流", "发货", "进度"],
    results: [
      {
        content: "订单发货后，物流单号与配送状态会同步显示在「我的订单」详情页。",
        score: 0.9,
        documentName: "物流跟踪说明.txt",
      },
      {
        content: "下单成功后系统会生成订单号，支付完成进入备货、发货、配送三个阶段。",
        score: 0.83,
        documentName: "下单流程.md",
      },
    ],
  },
  {
    keywords: ["参数", "规格", "尺寸"],
    results: [
      {
        content: "产品参数包含尺寸、重量、材质与适用场景，详见产品详情页规格表。",
        score: 0.88,
        documentName: "产品参数总览.md",
      },
    ],
  },
]

export const DEFAULT_MOCK_SEARCH_RESULTS: SearchResult[] = [
  {
    content: "当前为 Mock 演示数据，未匹配到具体业务知识，请尝试输入「退款」「订单」「参数」等关键词。",
    score: 0.5,
    documentName: "演示文档.md",
  },
]

export const MOCK_EVAL_SUITES: EvalSuite[] = [
  {
    id: "suite-001",
    name: "售后场景基准集",
    caseCount: 5,
  },
  {
    id: "suite-002",
    name: "订单流程基准集",
    caseCount: 4,
  },
]

export const MOCK_EVAL_RUN: EvalRunReport = {
  runNo: "run-0001",
  kbNo: "kb-001",
  status: "completed",
  metrics: {
    contextRelevancy: { avgScore: 0.84, passRate: 0.8 },
    faithfulness: { avgScore: 0.91, passRate: 0.9 },
    answerRelevancy: { avgScore: 0.78, passRate: 0.7 },
  },
  details: [
    {
      query: "如何申请退款？",
      generatedAnswer: "在订单详情中选择「申请售后」即可发起退款，通常 1 个工作日内完成审核。",
      contextRelevancyScore: 0.88,
      faithfulnessScore: 0.95,
      answerRelevancyScore: 0.82,
    },
    {
      query: "退货需要保留什么？",
      generatedAnswer: "退货商品需保持原包装完好，物流回收验收通过后发起退款。",
      contextRelevancyScore: 0.86,
      faithfulnessScore: 0.92,
      answerRelevancyScore: 0.79,
    },
    {
      query: "售后时效是多久？",
      generatedAnswer: "7 天无理由退货、15 天质量问题换货，超出时效需联系客服协商。",
      contextRelevancyScore: 0.79,
      faithfulnessScore: 0.88,
      answerRelevancyScore: 0.74,
    },
  ],
}

export const MOCK_LATENCY_MS = 400
