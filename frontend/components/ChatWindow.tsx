"use client";

import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { ChatMsg } from "@/lib/api";
import { LogoMark } from "@/components/Logo";

type Props = {
  messages: ChatMsg[];
  streaming: boolean;
  userName: string;
  onSend: (text: string) => void;
  onOpenMenu: () => void;
  onOpenDash: () => void;
};

const SUGGESTIONS = [
  { icon: "🧾", title: "Last transaction", text: "What was my last transaction?", tint: "bg-[#e6f4c8]" },
  { icon: "📊", title: "Card limits", text: "What is my card limit?", tint: "bg-[#fde7c2]" },
  { icon: "🔒", title: "Freeze a card", text: "Block my card", tint: "bg-[#ffd9cf]" },
  { icon: "⚖️", title: "Dispute a charge", text: "How do I dispute a charge?", tint: "bg-[#d9e8e1]" },
];

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export default function ChatWindow({ messages, streaming, userName, onSend, onOpenMenu, onOpenDash }: Props) {
  const [text, setText] = useState("");
  const bottom = useRef<HTMLDivElement>(null);
  const box = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 160) + "px";
  }, [text]);

  function submit(value: string) {
    const v = value.trim();
    if (!v || streaming) return;
    setText("");
    onSend(v);
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-none bg-surface md:rounded-3xl md:border md:border-line">
      {/* mobile header */}
      <header className="flex items-center justify-between border-b border-line px-4 py-3 md:hidden">
        <button onClick={onOpenMenu} className="rounded-lg px-2 py-1 text-xl" aria-label="Menu">☰</button>
        <LogoMark size={28} />
        <button onClick={onOpenDash} className="rounded-lg px-2 py-1 text-xl" aria-label="Accounts">💳</button>
      </header>

      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[760px] px-5 py-8">
          {messages.length === 0 ? (
            <div className="rise pt-6 md:pt-14">
              <p className="text-sm font-medium uppercase tracking-[0.2em] text-muted">{greeting()}</p>
              <h2 className="mt-2 font-display text-[44px] font-medium leading-[1.05] tracking-tight text-forest md:text-[56px]">
                {userName.split(" ")[0]},
                <br />
                <span className="italic text-forest-2">what can I sort out?</span>
              </h2>

              <div className="mt-10 grid grid-cols-1 gap-3 sm:grid-cols-2">
                {SUGGESTIONS.map((s, i) => (
                  <button
                    key={s.title}
                    onClick={() => submit(s.text)}
                    style={{ animationDelay: `${i * 70}ms` }}
                    className={`rise group rounded-2xl ${s.tint} p-4 text-left transition hover:-translate-y-0.5 hover:shadow-md`}
                  >
                    <span className="text-2xl">{s.icon}</span>
                    <p className="mt-3 font-semibold text-forest">{s.title}</p>
                    <p className="text-sm text-forest/60">“{s.text}”</p>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              {messages.map((m, i) => {
                const last = i === messages.length - 1;
                return m.role === "user" ? (
                  <div key={i} className="rise flex justify-end">
                    <div className="max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-forest px-4 py-3 text-[15px] leading-relaxed text-paper">
                      {m.content}
                    </div>
                  </div>
                ) : (
                  <div key={i} className="rise flex gap-3">
                    <div className="mt-0.5 shrink-0">
                      <LogoMark size={30} />
                    </div>
                    <div className="prose-chat min-w-0 flex-1 text-[15px] leading-relaxed">
                      {m.content ? (
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 py-2">
                          <span className="dot" />
                          <span className="dot" />
                          <span className="dot" />
                        </span>
                      )}
                      {streaming && last && m.content && <span className="cursor" />}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
          <div ref={bottom} />
        </div>
      </div>

      <div className="px-4 pb-4 pt-2">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit(text);
          }}
          className="mx-auto flex max-w-[760px] items-end gap-2 rounded-2xl border border-line bg-paper p-2 shadow-sm transition focus-within:border-forest focus-within:ring-4 focus-within:ring-lime/40"
        >
          <textarea
            ref={box}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                submit(text);
              }
            }}
            rows={1}
            maxLength={2000}
            placeholder="Ask about a payment, a card, a limit…"
            className="max-h-40 flex-1 resize-none bg-transparent px-3 py-2.5 text-[15px] outline-none placeholder:text-muted/70"
          />
          <button
            disabled={streaming || !text.trim()}
            aria-label="Send"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-forest text-lime transition hover:bg-forest-2 disabled:opacity-30"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 19V5M5 12l7-7 7 7" />
            </svg>
          </button>
        </form>
        <p className="mt-2 text-center text-[11px] text-muted">
          FintechAssist never asks for your PIN, password or OTP.
        </p>
      </div>
    </section>
  );
}
