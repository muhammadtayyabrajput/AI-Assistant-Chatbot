export function LogoMark({ size = 36, tone = "dark" }: { size?: number; tone?: "dark" | "light" }) {
  const bg = tone === "dark" ? "#0e3b2c" : "#c9f27a";
  const fg = tone === "dark" ? "#c9f27a" : "#0e3b2c";
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden>
      <rect width="40" height="40" rx="12" fill={bg} />
      {/* a chat bubble whose tail doubles as a rising arrow */}
      <path d="M11 13.5A3.5 3.5 0 0 1 14.5 10h11A3.5 3.5 0 0 1 29 13.5v7a3.5 3.5 0 0 1-3.5 3.5H19l-5 4.5V24h-0.5A3.5 3.5 0 0 1 11 20.5z" fill={fg} />
      <path d="M16 20l3-3.5 2.5 2L25 14.5" stroke={bg} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function Wordmark({ light = false }: { light?: boolean }) {
  return (
    <span className={`font-display text-[22px] font-semibold tracking-tight ${light ? "text-paper" : "text-forest"}`}>
      Fintech<span className={light ? "text-lime" : "text-forest-2"}>Assist</span>
    </span>
  );
}
