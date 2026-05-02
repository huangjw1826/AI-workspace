import { InfoCard } from "../components/ui/InfoCard";
import type { HealthStatus } from "../lib/types";

export function HealthPage({ health }: { health: HealthStatus | null }) {
  return (
    <section className="main-panel page-panel">
      <div className="health-grid">
        <InfoCard label="后端" value={health?.status === "ok" ? "正常" : "待检查"} />
        <InfoCard label="ffmpeg" value={health?.ffmpeg ? "可用" : "不可用"} />
        <InfoCard label="FunASR" value={health?.funasr ? "已安装" : "未安装"} />
        <InfoCard label="LLM" value={`${health?.llm_provider ?? "--"} / ${health?.llm_model ?? "--"}`} />
        <InfoCard label="API Key" value={health?.llm_configured ? "已配置" : "未配置"} />
        <InfoCard label="Python" value={health?.python ?? "--"} />
      </div>
    </section>
  );
}
