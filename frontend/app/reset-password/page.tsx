"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, AUTH_URL } from "@/lib/api";
import { BrandPanel, fieldClass, labelClass } from "@/components/AuthForm";
import PasswordChecklist from "@/components/PasswordChecklist";
import { passwordValid } from "@/lib/passwordRules";

function ResetForm() {
  const router = useRouter();
  const token = useSearchParams().get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!passwordValid(password)) return setError("Your password doesn't meet all the requirements below.");
    if (password !== confirm) return setError("The two passwords don't match.");
    setBusy(true);
    try {
      await api(`${AUTH_URL}/api/auth/reset-password`, null, {
        method: "POST",
        body: JSON.stringify({ token, newPassword: password }),
      });
      setDone(true);
      setTimeout(() => router.replace("/login"), 2500);
    } catch (err) {
      setError(err instanceof TypeError ? "Cannot reach the auth service. Is it running on :8081?" : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <div className="space-y-4">
        <p className="rounded-xl border border-coral/30 bg-coral/10 px-4 py-3 text-sm text-[#b2391f]">
          This reset link is missing its token. Please request a new one.
        </p>
        <Link href="/forgot-password" className="font-semibold text-forest underline">
          Request a new link
        </Link>
      </div>
    );
  }

  if (done) {
    return (
      <p className="rounded-xl border border-forest/20 bg-lime/30 px-4 py-3 text-sm text-forest">
        Password updated. Taking you to the login page…
      </p>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="relative">
        <input className={fieldClass} type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <span className={labelClass}>New password</span>
      </div>
      <PasswordChecklist password={password} />
      <div className="relative">
        <input className={fieldClass} type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        <span className={labelClass}>Confirm password</span>
      </div>
      {error && (
        <p className="rounded-xl border border-coral/30 bg-coral/10 px-4 py-2.5 text-sm text-[#b2391f]">
          {error}{" "}
          {error.includes("expired") && (
            <Link href="/forgot-password" className="font-semibold underline">
              Get a new link
            </Link>
          )}
        </p>
      )}
      <button
        disabled={busy}
        className="mt-2 w-full rounded-xl bg-forest py-3.5 text-[15px] font-semibold text-paper transition hover:bg-forest-2 disabled:opacity-60"
      >
        {busy ? "Saving…" : "Set new password"}
      </button>
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <main className="flex min-h-screen flex-1">
      <BrandPanel />
      <div className="bg-paper-grain flex flex-1 items-center justify-center px-6 py-10">
        <div className="rise w-full max-w-[400px]">
          <h1 className="font-display text-[40px] font-medium leading-tight tracking-tight text-forest">Choose a new password</h1>
          <p className="mb-8 mt-2 text-[15px] text-muted">Make it one you have not used elsewhere.</p>
          <Suspense>
            <ResetForm />
          </Suspense>
        </div>
      </div>
    </main>
  );
}
