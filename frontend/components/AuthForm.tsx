"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, AUTH_URL, getSession, saveSession, type Session } from "@/lib/api";
import { LogoMark, Wordmark } from "@/components/Logo";
import PasswordChecklist from "@/components/PasswordChecklist";
import { passwordValid } from "@/lib/passwordRules";

export const fieldClass =
  "peer w-full rounded-xl border border-line bg-surface px-4 pb-2 pt-6 text-[15px] outline-none transition focus:border-forest focus:ring-4 focus:ring-lime/40";
export const labelClass =
  "pointer-events-none absolute left-4 top-2 text-[11px] font-medium uppercase tracking-wider text-muted";

export function BrandPanel() {
  return (
    <div className="relative hidden flex-1 overflow-hidden bg-forest p-12 text-paper lg:flex lg:flex-col lg:justify-between">
      <div className="pointer-events-none absolute -right-32 -top-32 h-[520px] w-[520px] rounded-full bg-lime/20 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-40 -left-20 h-[420px] w-[420px] rounded-full border border-lime/20" />
      <div className="pointer-events-none absolute -bottom-24 -left-4 h-[300px] w-[300px] rounded-full border border-lime/20" />

      <div className="relative flex items-center gap-3">
        <LogoMark tone="light" />
        <Wordmark light />
      </div>

      <div className="relative">
        <h2 className="font-display text-[56px] font-medium leading-[1.02] tracking-tight">
          Banking help,
          <br />
          <span className="italic text-lime">minus the hold music.</span>
        </h2>
        <p className="mt-5 max-w-md text-[15px] leading-relaxed text-paper/70">
          Ask about a payment, check a limit, or freeze a lost card in seconds. Your assistant answers from your real
          account data, not a script.
        </p>
      </div>

      <div className="pointer-events-none absolute right-10 top-24 w-[270px]">
        <div
          className="float card-sheen relative aspect-[1.586] rounded-2xl bg-gradient-to-br from-lime to-[#9ad94a] p-5 text-forest shadow-2xl"
          style={{ ["--r" as string]: "-8deg" }}
        >
          <div className="flex items-center justify-between text-xs font-semibold">
            <span>FINTECH</span>
            <span>DEBIT</span>
          </div>
          <div className="mt-7 h-7 w-10 rounded-md bg-forest/20" />
          <p className="mt-4 font-mono text-sm tracking-widest">•••• •••• •••• 4821</p>
        </div>
        <div
          className="float-slow card-sheen absolute left-10 top-28 aspect-[1.586] w-full rounded-2xl bg-gradient-to-br from-[#1d1d1d] to-[#3a3a3a] p-5 text-paper shadow-2xl"
          style={{ ["--r" as string]: "6deg" }}
        >
          <div className="flex items-center justify-between text-xs font-semibold">
            <span>FINTECH</span>
            <span className="text-lime">CREDIT</span>
          </div>
          <div className="mt-7 h-7 w-10 rounded-md bg-amber/70" />
          <p className="mt-4 font-mono text-sm tracking-widest">•••• •••• •••• 9034</p>
        </div>
      </div>

      <div className="relative flex gap-8 text-sm text-paper/60">
        <span>● Live account data</span>
        <span>● Instant card controls</span>
        <span>● 24/7</span>
      </div>
    </div>
  );
}

export default function AuthForm({ mode }: { mode: "login" | "signup" }) {
  const router = useRouter();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const isSignup = mode === "signup";

  useEffect(() => {
    if (getSession()) router.replace("/chat");
  }, [router]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (isSignup && !passwordValid(password)) {
      setError("Your password doesn't meet all the requirements below.");
      return;
    }
    setBusy(true);
    try {
      const session = await api<Session>(`${AUTH_URL}/api/auth/${mode}`, null, {
        method: "POST",
        body: JSON.stringify(isSignup ? { fullName, email, password } : { email, password }),
      });
      saveSession(session);
      router.replace("/chat");
    } catch (err) {
      setError(err instanceof TypeError ? "Cannot reach the auth service. Is it running on :8081?" : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const field = fieldClass;
  const label = labelClass;

  return (
    <main className="flex min-h-screen flex-1">
      <BrandPanel />

      <div className="bg-paper-grain flex flex-1 items-center justify-center px-6 py-10">
        <form onSubmit={submit} className="rise w-full max-w-[400px]">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <LogoMark />
            <Wordmark />
          </div>

          <h1 className="font-display text-[40px] font-medium leading-tight tracking-tight text-forest">
            {isSignup ? "Open your account" : "Welcome back"}
          </h1>
          <p className="mb-8 mt-2 text-[15px] text-muted">
            {isSignup ? "Takes ten seconds. Your cards and history appear instantly." : "Sign in to chat with your banking assistant."}
          </p>

          <div className="space-y-3">
            {isSignup && (
              <div className="relative">
                <input className={field} value={fullName} onChange={(e) => setFullName(e.target.value)} required />
                <span className={label}>Full name</span>
              </div>
            )}
            <div className="relative">
              <input className={field} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              <span className={label}>Email</span>
            </div>
            <div className="relative">
              <input
                className={field}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <span className={label}>Password</span>
            </div>
            {!isSignup && (
              <div className="text-right">
                <Link href="/forgot-password" className="text-[13px] font-medium text-forest-2 hover:underline">
                  Forgot password?
                </Link>
              </div>
            )}
            {isSignup && (
              <PasswordChecklist password={password} />
            )}
          </div>

          {error && (
            <p className="mt-4 rounded-xl border border-coral/30 bg-coral/10 px-4 py-2.5 text-sm text-[#b2391f]">{error}</p>
          )}

          <button
            disabled={busy}
            className="group mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-forest py-3.5 text-[15px] font-semibold text-paper transition hover:bg-forest-2 disabled:opacity-60"
          >
            {busy ? "One moment…" : isSignup ? "Create account" : "Log in"}
            {!busy && <span className="text-lime transition group-hover:translate-x-1">→</span>}
          </button>

          <p className="mt-6 text-center text-sm text-muted">
            {isSignup ? "Already banking with us?" : "New here?"}{" "}
            <Link className="font-semibold text-forest underline decoration-lime decoration-2 underline-offset-4" href={isSignup ? "/login" : "/signup"}>
              {isSignup ? "Log in" : "Create an account"}
            </Link>
          </p>
        </form>
      </div>
    </main>
  );
}
