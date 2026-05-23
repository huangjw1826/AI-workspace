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
      <section className="recent-errors">
        <div className="section-head">
          <h3>最近错误</h3>
          <span>{health?.log_dir ?? "--"}</span>
        </div>
        {health?.recent_errors?.length ? (
          <ul>
            {health.recent_errors.map((line, index) => <li key={`${index}-${line}`}>{line}</li>)}
          </ul>
        ) : (
          <p className="muted">暂无错误记录。</p>
        )}
      </section>
    </section>
  );
}
