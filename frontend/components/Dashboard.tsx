"use client";

import { useEffect, useMemo, useState } from "react";
import { api, BANKING_URL, money, type Account, type BankCard, type Transaction } from "@/lib/api";

type Props = {
  token: string;
  userId: number;
  userName: string;
  refreshKey: number;
  open: boolean;
  onClose: () => void;
  onUnauthorized: () => void;
};

const CATEGORY: Record<string, { icon: string; color: string }> = {
  Shopping: { icon: "🛍️", color: "#0e3b2c" },
  Food: { icon: "🍔", color: "#ff6b4a" },
  Transport: { icon: "🚕", color: "#f2b84b" },
  Entertainment: { icon: "🎬", color: "#7a5cff" },
  Utilities: { icon: "⚡", color: "#2f8f6b" },
  Groceries: { icon: "🛒", color: "#9ad94a" },
  Income: { icon: "💰", color: "#c9f27a" },
};
const cat = (c: string) => CATEGORY[c] ?? { icon: "💳", color: "#6b746f" };

function CardVisual({ card, holder }: { card: BankCard; holder: string }) {
  const blocked = card.status !== "ACTIVE";
  const debit = card.type === "DEBIT";
  const theme = debit
    ? "from-forest to-[#1d6a50] text-paper"
    : "from-[#1b1b1b] to-[#3d3d3d] text-paper";
  return (
    <div className={`card-sheen relative aspect-[1.586] overflow-hidden rounded-2xl bg-gradient-to-br p-4 shadow-lg ${theme} ${blocked ? "grayscale" : ""}`}>
      <div className="absolute -right-10 -top-10 h-40 w-40 rounded-full bg-paper/10" />
      <div className="absolute -bottom-16 -left-8 h-44 w-44 rounded-full bg-lime/10" />
      <div className="relative flex items-center justify-between text-[11px] font-semibold tracking-wider">
        <span>FINTECH</span>
        <span className={debit ? "text-lime" : "text-amber"}>{debit ? "DEBIT" : "CREDIT"}</span>
      </div>
      <div className="relative mt-4 h-6 w-9 rounded-md bg-gradient-to-br from-amber to-[#c98a1b]" />
      <p className="relative mt-3 font-mono text-[15px] tracking-[0.18em]">{card.maskedNumber.replace(/\*/g, "•")}</p>
      <div className="relative mt-3 flex items-end justify-between text-[10px] uppercase tracking-wider text-paper/70">
        <span className="max-w-[55%] truncate">{holder}</span>
        <span>
          {card.expiry} · {card.network}
        </span>
      </div>
      {blocked && (
        <div className="stripes absolute inset-0 flex items-center justify-center">
          <span className="-rotate-12 rounded-md border-2 border-coral bg-ink/80 px-4 py-1 text-sm font-bold tracking-[0.3em] text-coral">
            BLOCKED
          </span>
        </div>
      )}
    </div>
  );
}

export default function Dashboard({ token, userId, userName, refreshKey, open, onClose, onUnauthorized }: Props) {
  const [account, setAccount] = useState<Account | null>(null);
  const [cards, setCards] = useState<BankCard[]>([]);
  const [txs, setTxs] = useState<Transaction[]>([]);
  const [error, setError] = useState("");
  const [cardIdx, setCardIdx] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [a, c, t] = await Promise.all([
          api<Account>(`${BANKING_URL}/api/accounts/${userId}?name=${encodeURIComponent(userName)}`, token),
          api<BankCard[]>(`${BANKING_URL}/api/cards/${userId}`, token),
          api<Transaction[]>(`${BANKING_URL}/api/transactions/${userId}?limit=20`, token),
        ]);
        if (cancelled) return;
        setAccount(a);
        setCards(c);
        setTxs(t);
        setError("");
      } catch (e) {
        if (cancelled) return;
        if ((e as { status?: number }).status === 401) onUnauthorized();
        else setError("Cannot load banking data. Is banking-service running on :8082?");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token, userId, userName, refreshKey, onUnauthorized]);

  const spend = useMemo(() => {
    const totals = new Map<string, number>();
    txs.filter((t) => t.type === "DEBIT").forEach((t) => totals.set(t.category, (totals.get(t.category) ?? 0) + t.amount));
    const rows = [...totals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4);
    return { rows, max: Math.max(1, ...rows.map((r) => r[1])), total: [...totals.values()].reduce((a, b) => a + b, 0) };
  }, [txs]);

  const card = cards[cardIdx];

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-ink/40 lg:hidden" onClick={onClose} />}
      <aside
        className={`fixed inset-y-0 right-0 z-40 flex w-[340px] max-w-[92vw] flex-col gap-5 overflow-y-auto bg-paper p-5 transition-transform lg:static lg:z-auto lg:w-[330px] lg:translate-x-0 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
      >
        <button onClick={onClose} className="self-end text-sm text-muted lg:hidden">
          Close ✕
        </button>
        {error && <p className="rounded-xl bg-coral/10 px-3 py-2 text-xs text-[#b2391f]">{error}</p>}

        <div>
          <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted">Total balance</p>
          <p className="mt-1 font-display text-[44px] font-medium leading-none tracking-tight text-forest">
            {account ? money(account.balance, account.currency) : "—"}
          </p>
          <p className="mt-2 truncate font-mono text-[11px] text-muted">{account?.accountNumber}</p>
        </div>

        {card && (
          <div>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-xs font-semibold uppercase tracking-[0.18em] text-muted">Your cards</h3>
              <div className="flex gap-1.5">
                {cards.map((c, i) => (
                  <button
                    key={c.id}
                    onClick={() => setCardIdx(i)}
                    aria-label={`Show ${c.type} card`}
                    className={`h-2 rounded-full transition-all ${i === cardIdx ? "w-6 bg-forest" : "w-2 bg-line"}`}
                  />
                ))}
              </div>
            </div>
            <CardVisual card={card} holder={userName} />
            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
              {card.creditLimit != null ? (
                <>
                  <div className="rounded-xl bg-surface p-3">
                    <p className="text-muted">Limit</p>
                    <p className="mt-0.5 text-sm font-semibold">{money(card.creditLimit)}</p>
                  </div>
                  <div className="rounded-xl bg-surface p-3">
                    <p className="text-muted">Available</p>
                    <p className="mt-0.5 text-sm font-semibold text-forest-2">{money(card.availableLimit ?? 0)}</p>
                  </div>
                </>
              ) : (
                <>
                  <div className="rounded-xl bg-surface p-3">
                    <p className="text-muted">Daily ATM limit</p>
                    <p className="mt-0.5 text-sm font-semibold">{money(card.dailyAtmLimit)}</p>
                  </div>
                  <div className="rounded-xl bg-surface p-3">
                    <p className="text-muted">Status</p>
                    <p className={`mt-0.5 text-sm font-semibold ${card.status === "ACTIVE" ? "text-forest-2" : "text-coral"}`}>
                      {card.status === "ACTIVE" ? "Active" : "Blocked"}
                    </p>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {spend.rows.length > 0 && (
          <div className="rounded-2xl bg-surface p-4">
            <div className="mb-3 flex items-baseline justify-between">
              <h3 className="text-xs font-semibold uppercase tracking-[0.18em] text-muted">Spending</h3>
              <span className="text-sm font-semibold">{money(spend.total)}</span>
            </div>
            <div className="space-y-2.5">
              {spend.rows.map(([name, value]) => (
                <div key={name}>
                  <div className="mb-1 flex justify-between text-xs">
                    <span>
                      {cat(name).icon} {name}
                    </span>
                    <span className="text-muted">{money(value)}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-line">
                    <div
                      className="h-full rounded-full transition-all duration-700"
                      style={{ width: `${(value / spend.max) * 100}%`, background: cat(name).color }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div>
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-[0.18em] text-muted">Recent activity</h3>
          <ul className="space-y-1">
            {txs.slice(0, 7).map((t) => (
              <li key={t.id} className="flex items-center gap-3 rounded-xl px-2 py-2 transition hover:bg-surface">
                <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-base"
                  style={{ background: cat(t.category).color + "22" }}
                >
                  {cat(t.category).icon}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{t.merchant}</p>
                  <p className="text-xs text-muted">
                    {new Date(t.date).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
                    {t.status === "PENDING" && (
                      <span className="ml-1.5 rounded-full bg-amber/25 px-1.5 py-0.5 text-[10px] font-semibold text-[#8a5d00]">pending</span>
                    )}
                  </p>
                </div>
                <span className={`text-sm font-semibold ${t.type === "CREDIT" ? "text-forest-2" : ""}`}>
                  {t.type === "CREDIT" ? "+" : "−"}
                  {money(t.amount)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </>
  );
}
