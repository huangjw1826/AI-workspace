import type React from "react";

export function SettingsSection({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return <section className="settings-section"><div className="settings-title">{icon}<h2>{title}</h2></div>{children}</section>;
}

