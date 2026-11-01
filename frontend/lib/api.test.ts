import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, money, msUntilExpiry, streamChat } from "./api";

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
const jwt = (payload: object) => `${b64url({ alg: "HS256" })}.${b64url(payload)}.sig`;

afterEach(() => vi.unstubAllGlobals());

describe("msUntilExpiry", () => {
  it("returns the time left for a valid token", () => {
    const ms = msUntilExpiry(jwt({ exp: Math.floor(Date.now() / 1000) + 3600 }))!;
    expect(ms).toBeGreaterThan(3_590_000);
    expect(ms).toBeLessThanOrEqual(3_600_000);
  });

  it("is negative once the token has expired", () => {
    expect(msUntilExpiry(jwt({ exp: Math.floor(Date.now() / 1000) - 10 }))!).toBeLessThan(0);
  });

  it.each(["", "garbage", "a.b.c", "a..c"])("returns null for an unreadable token (%s)", (t) => {
    expect(msUntilExpiry(t)).toBeNull();
  });

  it("returns null when there is no exp claim", () => {
    expect(msUntilExpiry(jwt({ sub: "1" }))).toBeNull();
  });
});

describe("api", () => {
  const respond = (status: number, body?: unknown) =>
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status })));

  it("sends the bearer token and returns parsed JSON", async () => {
    respond(200, { ok: true });
    await expect(api("http://x/y", "tok")).resolves.toEqual({ ok: true });
    const headers = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][1].headers;
    expect(headers.Authorization).toBe("Bearer tok");
  });

  it("does not send an Authorization header when logged out", async () => {
    respond(200, {});
    await api("http://x/y", null);
    const headers = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0][1].headers;
    expect(headers.Authorization).toBeUndefined();
  });

  it("throws an ApiError carrying the server message and status", async () => {
    respond(409, { message: "Email already registered" });
    await expect(api("http://x/y", null)).rejects.toMatchObject({ status: 409, message: "Email already registered" });
  });

  it("falls back to a generic message when the error body is not JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("oops", { status: 500 })));
    const err = (await api("http://x/y", null).catch((e) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe("Request failed (500)");
  });

  it("handles 204 No Content", async () => {
    respond(204);
    await expect(api("http://x/y", "t", { method: "DELETE" })).resolves.toBeUndefined();
  });
});

describe("streamChat", () => {
  const sse = (chunks: string[]) => {
    const enc = new TextEncoder();
    const stream = new ReadableStream({
      start(c) {
        chunks.forEach((x) => c.enqueue(enc.encode(x)));
        c.close();
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(stream, { status: 200 })));
  };

  const run = async () => {
    const meta: unknown[] = [];
    const tokens: string[] = [];
    const errors: string[] = [];
    await streamChat("tok", { chatId: null, message: "hi" }, {
      onMeta: (m) => meta.push(m),
      onToken: (t) => tokens.push(t),
      onError: (e) => errors.push(e),
    });
    return { meta, tokens, errors };
  };

  it("parses meta, token and done events in order", async () => {
    sse([
      'event:meta\ndata:{"chatId":"c1","title":"Hi"}\n\n',
      'event:token\ndata:{"t":"Hello"}\n\n',
      'event:token\ndata:{"t":" world"}\n\n',
      "event:done\ndata:{}\n\n",
    ]);
    const { meta, tokens, errors } = await run();
    expect(meta).toEqual([{ chatId: "c1", title: "Hi" }]);
    expect(tokens.join("")).toBe("Hello world");
    expect(errors).toEqual([]);
  });

  it("reassembles events that are split across network chunks", async () => {
    sse(['event:token\nda', 'ta:{"t":"Hel', 'lo"}\n', "\nevent:token\ndata:", '{"t":"!"}\n\n']);
    expect((await run()).tokens.join("")).toBe("Hello!");
  });

  it("keeps newlines and quotes inside tokens intact", async () => {
    sse([`event:token\ndata:${JSON.stringify({ t: 'line1\nline2 "quoted"' })}\n\n`]);
    expect((await run()).tokens).toEqual(['line1\nline2 "quoted"']);
  });

  it("handles CRLF line endings", async () => {
    sse(['event:token\r\ndata:{"t":"a"}\r\n\r\n']);
    expect((await run()).tokens).toEqual(["a"]);
  });

  it("surfaces server error events", async () => {
    sse(['event:error\ndata:{"message":"The AI service rejected the API key."}\n\n']);
    expect((await run()).errors).toEqual(["The AI service rejected the API key."]);
  });

  it("throws an ApiError with the status when the request is rejected", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await expect(run()).rejects.toMatchObject({ status: 401 });
  });

  it("sends the token and JSON body", async () => {
    sse(["event:done\ndata:{}\n\n"]);
    await run();
    const [, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer tok");
    expect(JSON.parse(init.body)).toEqual({ chatId: null, message: "hi" });
  });
});

describe("money", () => {
  it("formats amounts as currency", () => {
    expect(money(1234.5)).toBe("$1,234.50");
    expect(money(0)).toBe("$0.00");
  });
});
