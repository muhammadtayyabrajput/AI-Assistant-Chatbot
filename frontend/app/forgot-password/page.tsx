"use client";

import { useState } from "react";
import Link from "next/link";
import { api, AUTH_URL } from "@/lib/api";
import { BrandPanel, fieldClass, labelClass } from "@/components/AuthForm";

type Result = { message: string; devResetLink?: string };

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Result | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      setResult(
        await api<Result>(`${AUTH_URL}/api/auth/forgot-password`, null, {
          method: "POST",
          body: JSON.stringify({ email }),
        }),
      );
    } catch (err) {
      setError(err instanceof TypeError ? "Cannot reach the auth service. Is it running on :8081?" : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="flex min-h-screen flex-1">
      <BrandPanel />
      <div className="bg-paper-grain flex flex-1 items-center justify-center px-6 py-10">
        <div className="rise w-full max-w-[400px]">
          <h1 className="font-display text-[40px] font-medium leading-tight tracking-tight text-forest">Forgot your password?</h1>
          <p className="mb-8 mt-2 text-[15px] text-muted">
            Enter your account email and we will send you a link to choose a new one.
          </p>

          {result ? (
            <div className="space-y-4">
              <p className="rounded-xl border border-forest/20 bg-lime/30 px-4 py-3 text-sm text-forest">{result.message}</p>
              {result.devResetLink && (
                <div className="rounded-xl border border-dashed border-amber bg-amber/10 p-4 text-sm">
                  <p className="font-semibold text-[#8a5d00]">Dev mode: no email server is configured</p>
                  <p className="mt-1 text-muted">Use this link to continue (it is hidden in production):</p>
                  <a href={result.devResetLink} className="mt-2 block break-all font-mono text-xs text-forest underline">
                    {result.devResetLink}
                  </a>
                </div>
              )}
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="relative">
                <input className={fieldClass} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                <span className={labelClass}>Email</span>
              </div>
              {error && (
                <p className="rounded-xl border border-coral/30 bg-coral/10 px-4 py-2.5 text-sm text-[#b2391f]">{error}</p>
              )}
              <button
                disabled={busy}
                className="w-full rounded-xl bg-forest py-3.5 text-[15px] font-semibold text-paper transition hover:bg-forest-2 disabled:opacity-60"
              >
                {busy ? "Sending…" : "Send reset link"}
              </button>
            </form>
          )}

          <p className="mt-6 text-center text-sm text-muted">
            <Link className="font-semibold text-forest underline decoration-lime decoration-2 underline-offset-4" href="/login">
              ← Back to log in
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
