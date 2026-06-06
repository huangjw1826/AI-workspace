import React from "react";

/**
 * Wraps page content with a fade+slide-up entrance animation.
 * Key changes with a string key (e.g. route path) to re-trigger the animation.
 */
export function PageTransition({
  children,
  pageKey,
}: {
  children: React.ReactNode;
  pageKey: string;
}) {
  return (
    <div key={pageKey} className="anim-fade-in" style={{ minHeight: 0 }}>
      {children}
    </div>
  );
}
