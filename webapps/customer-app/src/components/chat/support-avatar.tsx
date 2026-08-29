import { Headphones } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { cn } from "@/lib/utils"

type SupportAvatarProps = {
  className?: string
}

export function SupportAvatar({ className }: SupportAvatarProps) {
  return (
    <Avatar className={cn("size-10 border border-white/80 shadow-sm", className)}>
      <AvatarFallback className="bg-coral text-white">
        <Headphones aria-hidden="true" className="size-5" strokeWidth={2.2} />
      </AvatarFallback>
    </Avatar>
  )
}
