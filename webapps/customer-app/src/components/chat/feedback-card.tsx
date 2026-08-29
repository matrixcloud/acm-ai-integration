import { Check, Frown, Smile } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import type { FeedbackRating } from "@/types/chat"

type FeedbackCardProps = {
  rating: FeedbackRating | null
  onRate: (rating: FeedbackRating) => void
}

export function FeedbackCard({ rating, onRate }: FeedbackCardProps) {
  if (rating) {
    const ratingLabel = rating === "satisfied" ? "已解决" : "未解决"

    return (
      <Card className="mx-auto max-w-sm border-emerald-200 bg-emerald-50/80 shadow-none">
        <CardContent className="flex flex-col items-center justify-center gap-1 py-4 text-sm text-emerald-800">
          <span className="flex items-center gap-2 font-medium">
            <Check aria-hidden="true" className="size-4" />
            感谢你的评价，本次咨询已结束
          </span>
          <span className="text-xs text-emerald-700/75">你选择了：{ratingLabel}</span>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="mx-auto max-w-sm border-primary/10 shadow-none">
      <CardContent className="text-center">
        <p className="font-semibold text-foreground">本次服务解决了你的问题吗？</p>
        <p className="mt-1 text-sm text-muted-foreground">你的评价会帮助我们持续改进</p>
        <div className="mt-4 flex justify-center gap-3">
          <Button variant="outline" onClick={() => onRate("unsatisfied")}>
            <Frown aria-hidden="true" className="size-4" />
            未解决
          </Button>
          <Button variant="default" onClick={() => onRate("satisfied")}>
            <Smile aria-hidden="true" className="size-4" />
            已解决
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
