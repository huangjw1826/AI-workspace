export function InfoCard({ label, value }: { label: string; value: string }) {
  return <div className="info-card"><span>{label}</span><strong>{value}</strong></div>;
}

