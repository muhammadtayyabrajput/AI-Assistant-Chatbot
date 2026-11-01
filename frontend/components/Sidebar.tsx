"use client";

import type { ChatSummary } from "@/lib/api";
import { LogoMark, Wordmark } from "@/components/Logo";

type Props = {
  chats: ChatSummary[];
  activeId: string | null;
  userName: string;
  open: boolean;
  onClose: () => void;
  onNew: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onLogout: () => void;
};

function group(chats: ChatSummary[]) {
  const today = new Date().toDateString();
  const week = Date.now() - 7 * 864e5;
  const out: Record<string, ChatSummary[]> = { Today: [], "This week": [], Earlier: [] };
  for (const c of chats) {
    const d = new Date(c.updatedAt);
    (d.toDateString() === today ? out.Today : d.getTime() > week ? out["This week"] : out.Earlier).push(c);
  }
  return Object.entries(out).filter(([, v]) => v.length);
}

export default function Sidebar({ chats, activeId, userName, open, onClose, onNew, onSelect, onDelete, onLogout }: Props) {
  const initials = userName.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-ink/40 md:hidden" onClick={onClose} />}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-72 flex-col bg-forest text-paper transition-transform md:static md:z-auto md:w-[270px] md:translate-x-0 md:rounded-3xl ${
          open ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="flex items-center gap-2.5 px-5 pb-3 pt-5">
          <LogoMark tone="light" size={32} />
          <Wordmark light />
        </div>

        <div className="px-4 pb-3">
          <button
            onClick={() => {
              onNew();
              onClose();
            }}
            className="flex w-full items-center justify-between rounded-xl bg-lime px-4 py-2.5 text-sm font-semibold text-forest transition hover:brightness-95"
          >
            New conversation <span className="text-lg leading-none">+</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-3 pb-3">
          {chats.length === 0 && (
            <p className="px-3 py-6 text-center text-sm text-paper/50">Your conversations will show up here.</p>
          )}
          {group(chats).map(([label, items]) => (
            <div key={label} className="mb-3">
              <p className="px-3 pb-1 pt-2 text-[11px] font-medium uppercase tracking-widest text-paper/40">{label}</p>
              {items.map((c) => (
                <div
                  key={c.id}
                  className={`group flex items-center rounded-xl transition ${
                    c.id === activeId ? "bg-paper/12 ring-1 ring-lime/40" : "hover:bg-paper/8"
                  }`}
                >
                  <button
                    onClick={() => {
                      onSelect(c.id);
                      onClose();
                    }}
                    className="min-w-0 flex-1 truncate px-3 py-2 text-left text-sm text-paper/90"
                  >
                    {c.title}
                  </button>
                  <button
                    onClick={() => onDelete(c.id)}
                    title="Delete"
                    className="mr-1.5 hidden h-6 w-6 items-center justify-center rounded-md text-paper/50 hover:bg-coral/20 hover:text-coral group-hover:flex"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          ))}
        </div>

        <div className="m-3 flex items-center gap-3 rounded-2xl bg-forest-2/60 p-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-lime text-sm font-bold text-forest">
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{userName}</p>
            <button onClick={onLogout} className="text-xs text-paper/50 hover:text-lime">
              Log out
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
