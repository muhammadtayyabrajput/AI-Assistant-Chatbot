"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
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
  getRawSession,
  msUntilExpiry,
  onRemoteLogout,
  parseSession,
  streamChat,
  subscribeSession,
  type ChatMsg,
  type ChatSummary,
  type Session,
} from "@/lib/api";

export default function ChatPage() {
  const router = useRouter();
  const raw = useSyncExternalStore(subscribeSession, getRawSession, () => null);
  const session = useMemo(() => parseSession(raw), [raw]);

  useEffect(() => {
    if (!session) router.replace("/login");
  }, [session, router]);

  if (!session) return null;
  return <ChatApp key={session.userId} session={session} />;
}

function ChatApp({ session }: { session: Session }) {
  const router = useRouter();
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [dashKey, setDashKey] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [dashOpen, setDashOpen] = useState(false);
  const abort = useRef<AbortController | null>(null);

  const endSession = useCallback(
    (notifyOtherTabs: boolean) => {
      abort.current?.abort();
      clearSession();
      if (notifyOtherTabs) broadcastLogout(session.userId);
      router.replace("/login");
    },
    [router, session.userId],
  );
  const logout = useCallback(() => endSession(true), [endSession]);

  const loadChats = useCallback(async () => {
    try {
      setChats(await api<ChatSummary[]>(`${CHAT_URL}/api/chats`, session.token));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) endSession(false);
    }
  }, [session.token, endSession]);

  useEffect(() => {
    let active = true;
    api<ChatSummary[]>(`${CHAT_URL}/api/chats`, session.token)
      .then((list) => active && setChats(list))
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) endSession(false);
      });
    return () => {
      active = false;
    };
  }, [session.token, endSession]);

  useEffect(() => {
    const ms = msUntilExpiry(session.token);
    if (ms === null) return;
    const t = setTimeout(() => endSession(false), Math.min(Math.max(ms, 0), 2 ** 31 - 1));
    return () => clearTimeout(t);
  }, [session.token, endSession]);

  useEffect(
    () =>
      onRemoteLogout((uid) => {
        if (uid === session.userId) endSession(false);
      }),
    [session.userId, endSession],
  );

  async function selectChat(id: string) {
    if (streaming) return;
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
    await api<void>(`${CHAT_URL}/api/chats/${id}`, session.token, { method: "DELETE" });
    if (id === activeId) newChat();
    loadChats();
  }

  async function send(text: string) {
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
      if (e instanceof ApiError && e.status === 401) return endSession(false);
      if ((e as Error).name !== "AbortError") {
        appendToLast("⚠️ Cannot reach the chat service. Is it running on :8083?");
      }
    } finally {
      setStreaming(false);
      loadChats();
      setDashKey((k) => k + 1);
    }
  }

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
        onUnauthorized={() => endSession(false)}
      />
    </div>
  );
}
