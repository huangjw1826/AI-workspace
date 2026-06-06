export function InfoCard({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="info-card">
      <span className="info-card-label">{label}</span>
      <strong className="info-card-value">{value}</strong>
    </div>
  );
}
