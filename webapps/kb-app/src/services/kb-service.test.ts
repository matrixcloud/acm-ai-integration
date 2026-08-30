import { afterEach, describe, expect, it, vi } from "vitest"

import {
  KbServiceError,
  kbService,
  toDocument,
  toEvalRunReport,
  toEvalSuite,
  toKnowledgeBase,
  toSearchResults,
} from "@/services/kb-service"

describe("kb-service client", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    })
  }

  function emptyResponse(status: number): Response {
    return new Response(null, { status })
  }

  function stubFetch(response: Response) {
    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal("fetch", fetchMock)
    return fetchMock
  }

  it("maps knowledge base wire fields to the domain model", () => {
    expect(
      toKnowledgeBase({
        kbNo: "KB-1",
        name: "售后知识库",
        status: "ARCHIVED",
        docCount: 2,
        createdAt: "2026-08-30T00:00:00",
      }),
    ).toEqual({
      id: "KB-1",
      name: "售后知识库",
      status: "archived",
      docCount: 2,
      createdAt: "2026-08-30T00:00:00",
    })
  })

  it("maps document wire fields to the domain model", () => {
    expect(
      toDocument({
        documentNo: "DOC-1",
        name: "退款政策.md",
        status: "READY",
        chunkCount: 3,
        createdAt: "2026-08-30T00:00:00",
      }),
    ).toEqual({
      id: "DOC-1",
      name: "退款政策.md",
      status: "ready",
      chunkCount: 3,
      createdAt: "2026-08-30T00:00:00",
    })
  })

  it("maps search chunks and drops the wire documentNo", () => {
    expect(
      toSearchResults({
        chunks: [
          { content: "退款需在签收后 7 天内", score: 0.87, documentNo: "DOC-1", documentName: "退款政策.md" },
        ],
      }),
    ).toEqual([{ content: "退款需在签收后 7 天内", score: 0.87, documentName: "退款政策.md" }])
  })

  it("maps eval suites and runs to the domain model", () => {
    expect(
      toEvalSuite({
        suiteNo: "SUITE-1",
        name: "售后套件",
        caseCount: 2,
        createdAt: "2026-08-30T00:00:00",
      }),
    ).toEqual({ id: "SUITE-1", name: "售后套件", caseCount: 2 })

    expect(
      toEvalRunReport({
        runNo: "RUN-1",
        kbNo: "KB-1",
        status: "COMPLETED",
        topK: 3,
        startedAt: "2026-08-30T00:00:00",
        finishedAt: "2026-08-30T00:00:01",
        metrics: {
          contextRelevancy: { avgScore: 0.8, passRate: 0.9 },
          faithfulness: { avgScore: 1, passRate: 1 },
          answerRelevancy: { avgScore: 0.7, passRate: 0.6 },
        },
        details: [
          {
            query: "如何退款",
            generatedAnswer: "退款需在签收后 7 天内",
            contextRelevancyScore: 1,
            faithfulnessScore: 1,
            answerRelevancyScore: 0,
          },
        ],
      }),
    ).toEqual({
      runNo: "RUN-1",
      kbNo: "KB-1",
      status: "completed",
      metrics: {
        contextRelevancy: { avgScore: 0.8, passRate: 0.9 },
        faithfulness: { avgScore: 1, passRate: 1 },
        answerRelevancy: { avgScore: 0.7, passRate: 0.6 },
      },
      details: [
        {
          query: "如何退款",
          generatedAnswer: "退款需在签收后 7 天内",
          contextRelevancyScore: 1,
          faithfulnessScore: 1,
          answerRelevancyScore: 0,
        },
      ],
    })
  })

  it("lists knowledge bases over HTTP and maps the response", async () => {
    const fetchMock = stubFetch(
      jsonResponse(200, [
        { kbNo: "KB-1", name: "售后知识库", status: "ACTIVE", docCount: 0, createdAt: "2026-08-30T00:00:00" },
      ]),
    )

    await expect(kbService.listKnowledgeBases()).resolves.toEqual([
      { id: "KB-1", name: "售后知识库", status: "active", docCount: 0, createdAt: "2026-08-30T00:00:00" },
    ])
    expect(fetchMock).toHaveBeenCalledWith("/kbs", undefined)
  })

  it("creates a knowledge base over HTTP and maps the response", async () => {
    const fetchMock = stubFetch(
      jsonResponse(201, {
        kbNo: "KB-9",
        name: "新品知识库",
        status: "ACTIVE",
        docCount: 0,
        createdAt: "2026-08-30T00:00:00",
      }),
    )

    await expect(kbService.createKnowledgeBase("新品知识库")).resolves.toEqual({
      id: "KB-9",
      name: "新品知识库",
      status: "active",
      docCount: 0,
      createdAt: "2026-08-30T00:00:00",
    })

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe("/kbs")
    expect((init as RequestInit).method).toBe("POST")
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: "新品知识库",
    })
  })

  it("posts a JSON search and maps chunks", async () => {
    const fetchMock = stubFetch(
      jsonResponse(200, {
        chunks: [
          { content: "退款需在签收后 7 天内", score: 0.87, documentNo: "DOC-1", documentName: "退款政策.md" },
        ],
      }),
    )

    await expect(kbService.search("KB-1", "如何退款", 3)).resolves.toEqual([
      { content: "退款需在签收后 7 天内", score: 0.87, documentName: "退款政策.md" },
    ])

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe("/kbs/KB-1/search")
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      query: "如何退款",
      topK: 3,
    })
  })

  it("uploads a document as multipart form data", async () => {
    const file = new File(["# 退款"], "退款政策.md", { type: "text/markdown" })
    const fetchMock = stubFetch(
      jsonResponse(201, {
        documentNo: "DOC-1",
        name: "退款政策.md",
        status: "PROCESSING",
        chunkCount: 1,
        createdAt: "2026-08-30T00:00:00",
      }),
    )

    await expect(kbService.uploadDocument("KB-1", file)).resolves.toEqual({
      id: "DOC-1",
      name: "退款政策.md",
      status: "processing",
      chunkCount: 1,
      createdAt: "2026-08-30T00:00:00",
    })

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe("/kbs/KB-1/documents")
    expect((init as RequestInit).method).toBe("POST")
    expect(((init as RequestInit).body as FormData).get("file")).toBe(file)
  })

  it("resolves without parsing the body on a 204 delete", async () => {
    const fetchMock = stubFetch(emptyResponse(204))

    await expect(kbService.deleteDocument("KB-1", "DOC-1")).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith("/kbs/KB-1/documents/DOC-1", { method: "DELETE" })
  })

  it("parses Problem Details into KbServiceError", async () => {
    stubFetch(
      jsonResponse(409, {
        title: "Conflict",
        status: 409,
        detail: "知识库未启用",
        code: "KB_NOT_ACTIVE",
      }),
    )

    await expect(kbService.archiveKnowledgeBase("KB-1")).rejects.toMatchObject({
      name: "KbServiceError",
      code: "KB_NOT_ACTIVE",
      httpStatus: 409,
    })
  })

  it("routes archive, activate and eval suite list to their gateway paths", async () => {
    const active = {
      kbNo: "KB-1",
      name: "售后知识库",
      status: "ACTIVE",
      docCount: 0,
      createdAt: "2026-08-30T00:00:00",
    }

    const archiveFetch = stubFetch(jsonResponse(200, { ...active, status: "ARCHIVED" }))
    await expect(kbService.archiveKnowledgeBase("KB-1")).resolves.toMatchObject({
      id: "KB-1",
      status: "archived",
    })
    expect(archiveFetch).toHaveBeenCalledWith("/kbs/KB-1/archive", { method: "POST" })

    const activateFetch = stubFetch(jsonResponse(200, active))
    await expect(kbService.activateKnowledgeBase("KB-1")).resolves.toMatchObject({ status: "active" })
    expect(activateFetch).toHaveBeenCalledWith("/kbs/KB-1/activate", { method: "POST" })

    const suitesFetch = stubFetch(
      jsonResponse(200, [
        { suiteNo: "SUITE-1", name: "售后套件", caseCount: 1, createdAt: "2026-08-30T00:00:00" },
      ]),
    )
    await expect(kbService.listEvalSuites()).resolves.toEqual([
      { id: "SUITE-1", name: "售后套件", caseCount: 1 },
    ])
    expect(suitesFetch).toHaveBeenCalledWith("/eval/suites", undefined)
  })

  it("posts eval runs with the wire request body", async () => {
    const fetchMock = stubFetch(
      jsonResponse(201, {
        runNo: "RUN-1",
        kbNo: "KB-1",
        status: "COMPLETED",
        topK: 3,
        startedAt: "2026-08-30T00:00:00",
        finishedAt: "2026-08-30T00:00:01",
        metrics: {
          contextRelevancy: { avgScore: 1, passRate: 1 },
          faithfulness: { avgScore: 1, passRate: 1 },
          answerRelevancy: { avgScore: 1, passRate: 1 },
        },
        details: [],
      }),
    )

    const report = await kbService.startEvalRun("KB-1", "SUITE-1", 3)

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe("/eval/runs")
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      kbNo: "KB-1",
      suiteNo: "SUITE-1",
      topK: 3,
    })
    expect(report.status).toBe("completed")
  })

  it("falls back to a generic code when the error body is not JSON", async () => {
    stubFetch(new Response("boom", { status: 500 }))

    await expect(kbService.listKnowledgeBases()).rejects.toBeInstanceOf(KbServiceError)
  })
})