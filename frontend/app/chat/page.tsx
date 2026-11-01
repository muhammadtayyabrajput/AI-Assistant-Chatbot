"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import ChatWindow from "@/components/ChatWindow";
import Dashboard from "@/components/Dashboard";
import Sidebar from "@/components/Sidebar";
import {
  api,
  ApiError,
  CHAT_URL,
  broadcastLogout,
  clearSession,
  getSession,
  msUntilExpiry,
  onRemoteLogout,
  streamChat,
  type ChatMsg,
  type ChatSummary,
  type Session,
} from "@/lib/api";

export default function ChatPage() {
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [dashKey, setDashKey] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [dashOpen, setDashOpen] = useState(false);
  const abort = useRef<AbortController | null>(null);

  // Ends the session completely: cancels any streaming reply, wipes in-memory data, forgets the token.
  const endSession = useCallback(
    (uid?: number) => {
      abort.current?.abort();
      clearSession();
      setSession(null);
      setChats([]);
      setMessages([]);
      setActiveId(null);
      if (uid != null) broadcastLogout(uid);
      router.replace("/login");
    },
    [router],
  );

  const sessionUser = useRef<number | null>(null);
  const logout = useCallback(() => endSession(sessionUser.current ?? undefined), [endSession]);

  const loadChats = useCallback(
    async (token: string) => {
      try {
        setChats(await api<ChatSummary[]>(`${CHAT_URL}/api/chats`, token));
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) logout();
      }
    },
    [logout],
  );

  useEffect(() => {
    const s = getSession();
    if (!s) {
      router.replace("/login");
      return;
    }
    setSession(s);
    sessionUser.current = s.userId;
    loadChats(s.token);
  }, [router, loadChats]);

  // Auto-logout when the token expires.
  useEffect(() => {
    if (!session) return;
    const ms = msUntilExpiry(session.token);
    if (ms === null) return;
    if (ms <= 0) return endSession();
    const t = setTimeout(() => endSession(), Math.min(ms, 2 ** 31 - 1));
    return () => clearTimeout(t);
  }, [session, endSession]);

  // Another tab logged this same user out -> log out here too.
  useEffect(
    () =>
      onRemoteLogout((uid) => {
        if (uid === sessionUser.current) endSession();
      }),
    [endSession],
  );

  async function selectChat(id: string) {
    if (!session || streaming) return;
    setActiveId(id);
    try {
      setMessages(await api<ChatMsg[]>(`${CHAT_URL}/api/chats/${id}/messages`, session.token));
    } catch {
      setMessages([]);
    }
  }

  function newChat() {
    if (streaming) return;
    setActiveId(null);
    setMessages([]);
  }

  async function deleteChat(id: string) {
    if (!session) return;
    await api<void>(`${CHAT_URL}/api/chats/${id}`, session.token, { method: "DELETE" });
    if (id === activeId) newChat();
    loadChats(session.token);
  }

  async function send(text: string) {
    if (!session) return;
    setMessages((m) => [...m, { role: "user", content: text }, { role: "assistant", content: "" }]);
    setStreaming(true);
    const ctrl = new AbortController();
    abort.current = ctrl;

    const appendToLast = (t: string) =>
      setMessages((m) => {
        const copy = [...m];
        const last = copy[copy.length - 1];
        copy[copy.length - 1] = { ...last, content: last.content + t };
        return copy;
      });

    try {
      await streamChat(
        session.token,
        { chatId: activeId, message: text },
        {
          onMeta: (meta) => setActiveId(meta.chatId),
          onToken: appendToLast,
          onError: (msg) => appendToLast(`\n\n⚠️ ${msg}`),
        },
        ctrl.signal,
      );
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return logout();
      if ((e as Error).name !== "AbortError") {
        appendToLast("⚠️ Cannot reach the chat service. Is it running on :8083?");
      }
    } finally {
      setStreaming(false);
      loadChats(session.token);
      setDashKey((k) => k + 1); // refresh dashboard (e.g. a card may have just been blocked)
    }
  }

  if (!session) return null;

  return (
    <div className="bg-paper-grain flex h-screen gap-3 p-0 md:p-3">
      <Sidebar
        chats={chats}
        activeId={activeId}
        userName={session.fullName}
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        onNew={newChat}
        onSelect={selectChat}
        onDelete={deleteChat}
        onLogout={logout}
      />
      <ChatWindow
        messages={messages}
        streaming={streaming}
        userName={session.fullName}
        onSend={send}
        onOpenMenu={() => setMenuOpen(true)}
        onOpenDash={() => setDashOpen(true)}
      />
      <Dashboard
        token={session.token}
        userId={session.userId}
        userName={session.fullName}
        refreshKey={dashKey}
        open={dashOpen}
        onClose={() => setDashOpen(false)}
        onUnauthorized={logout}
      />
    </div>
  );
}
