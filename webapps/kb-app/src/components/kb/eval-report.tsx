import type { EvalRunReport } from "@/types/kb"

type EvalReportProps = {
  report: EvalRunReport | null
  isRunning: boolean
}

const METRIC_LABELS = {
  contextRelevancy: "上下文相关性",
  faithfulness: "忠实度",
  answerRelevancy: "答案相关性",
} as const

type MetricKey = keyof typeof METRIC_LABELS

export function EvalReport({ report, isRunning }: EvalReportProps) {
  if (isRunning) {
    return (
      <div className="flex items-center justify-center rounded-2xl border border-border bg-card px-4 py-8 text-sm text-muted-foreground">
        评估运行中，请稍候…
      </div>
    )
  }

  if (!report) {
    return (
      <p className="rounded-2xl border border-dashed border-border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
        尚无评估报告，点击「发起评估」生成 Mock 报告。
      </p>
    )
  }

  const metricKeys = Object.keys(METRIC_LABELS) as MetricKey[]

  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-border bg-card px-4 py-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">评估报告 {report.runNo}</h3>
        <span className="text-xs text-muted-foreground">状态：{statusLabel(report.status)}</span>
      </div>

      <div className="grid grid-cols-3 gap-3">
        {metricKeys.map((key) => {
          const metric = report.metrics[key]
          return (
            <div
              key={key}
              className="flex flex-col gap-1 rounded-2xl bg-secondary px-3 py-2"
            >
              <span className="text-xs text-muted-foreground">{METRIC_LABELS[key]}</span>
              <span className="text-sm font-semibold text-foreground">
                平均 {(metric.avgScore * 100).toFixed(0)}%
              </span>
              <span className="text-xs text-muted-foreground">
                通过率 {(metric.passRate * 100).toFixed(0)}%
              </span>
            </div>
          )
        })}
      </div>

      <div className="flex flex-col gap-2">
        <span className="text-xs font-semibold text-foreground">逐问题明细</span>
        <ul className="flex flex-col gap-2">
          {report.details.map((detail, index) => (
            <li
              key={index}
              className="flex flex-col gap-1 rounded-2xl border border-border bg-background px-3 py-2 text-xs"
            >
              <span className="font-medium text-foreground">Q：{detail.query}</span>
              <span className="text-muted-foreground">A：{detail.generatedAnswer}</span>
              <span className="flex flex-wrap gap-2 text-muted-foreground">
                <span>上下文 {detail.contextRelevancyScore.toFixed(2)}</span>
                <span>忠实度 {detail.faithfulnessScore.toFixed(2)}</span>
                <span>答案相关性 {detail.answerRelevancyScore.toFixed(2)}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function statusLabel(status: EvalRunReport["status"]): string {
  if (status === "running") {
    return "运行中"
  }
  if (status === "completed") {
    return "已完成"
  }
  return "失败"
}
