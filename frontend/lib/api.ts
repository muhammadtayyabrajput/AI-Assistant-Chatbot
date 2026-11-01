export const AUTH_URL = process.env.NEXT_PUBLIC_AUTH_URL ?? "http://localhost:8081";
export const BANKING_URL = process.env.NEXT_PUBLIC_BANKING_URL ?? "http://localhost:8082";
export const CHAT_URL = process.env.NEXT_PUBLIC_CHAT_URL ?? "http://localhost:8083";

export type Session = { token: string; userId: number; fullName: string; email: string };

export type ChatSummary = { id: string; title: string; updatedAt: string };
export type ChatMsg = { role: "user" | "assistant"; content: string };

export type Account = { accountNumber: string; holderName: string; balance: number; currency: string };
export type BankCard = {
  id: string; type: string; network: string; maskedNumber: string; last4: string; expiry: string;
  creditLimit: number | null; availableLimit: number | null; dailyAtmLimit: number; status: string;
};
export type Transaction = {
  id: string; merchant: string; category: string; amount: number; type: string;
  status: string; date: string; cardLast4: string;
};

const KEY = "fintech-session";

// sessionStorage = one login per browser tab, so two users in two tabs can never see each other's data.
export function getSession(): Session | null {
  try {
    localStorage.removeItem(KEY); // drop the shared login left by older versions
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(s: Session) {
  sessionStorage.setItem(KEY, JSON.stringify(s));
}

export function clearSession() {
  sessionStorage.removeItem(KEY);
}

/** Milliseconds until the JWT expires (negative if already expired, null if unreadable). */
export function msUntilExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" ? payload.exp * 1000 - Date.now() : null;
  } catch {
    return null;
  }
}

// Tabs keep separate logins (sessionStorage), but logging out should end that user everywhere in this browser.
const CHANNEL = "fintech-auth";

export function broadcastLogout(userId: number) {
  try {
    const ch = new BroadcastChannel(CHANNEL);
    ch.postMessage({ type: "logout", userId });
    ch.close();
  } catch {
    /* BroadcastChannel unsupported */
  }
}

/** Calls cb when another tab logs out. Returns an unsubscribe function. */
export function onRemoteLogout(cb: (userId: number) => void): () => void {
  try {
    const ch = new BroadcastChannel(CHANNEL);
    ch.onmessage = (e) => e.data?.type === "logout" && cb(e.data.userId);
    return () => ch.close();
  } catch {
    return () => {};
  }
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export async function api<T>(url: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!res.ok) {
    let msg = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body?.message) msg = body.message;
    } catch {
      /* no body */
    }
    throw new ApiError(res.status, msg);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

type StreamHandlers = {
  onMeta: (m: { chatId: string; title: string }) => void;
  onToken: (t: string) => void;
  onError: (message: string) => void;
};

/** POST /api/chat and parse the Server-Sent Events stream (EventSource can't send POST + auth headers). */
export async function streamChat(
  token: string,
  body: { chatId: string | null; message: string },
  h: StreamHandlers,
  signal?: AbortSignal,
) {
  const res = await fetch(`${CHAT_URL}/api/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
    body: JSON.stringify(body),
    signal,
  });
  if (!res.ok || !res.body) throw new ApiError(res.status, `Chat request failed (${res.status})`);

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  const dispatch = (block: string) => {
    let event = "message";
    const data: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
    }
    if (!data.length) return;
    const payload = JSON.parse(data.join("\n"));
    if (event === "meta") h.onMeta(payload);
    else if (event === "token") h.onToken(payload.t);
    else if (event === "error") h.onError(payload.message);
  };

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? "";
    blocks.forEach(dispatch);
  }
  if (buffer.trim()) dispatch(buffer);
}

export const money = (n: number, currency = "USD") =>
  new Intl.NumberFormat("en-US", { style: "currency", currency }).format(n);
