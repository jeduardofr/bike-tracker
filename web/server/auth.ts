import { createHmac, timingSafeEqual } from "node:crypto";
import type { Context, Next } from "hono";
import { getCookie, setCookie } from "hono/cookie";

const COOKIE_NAME = "bt_session";
const SESSION_DAYS = 30;

function secret(): string {
  const s = process.env.SESSION_SECRET;
  if (!s) throw new Error("SESSION_SECRET is not set");
  return s;
}

function sign(exp: number): string {
  const mac = createHmac("sha256", secret()).update(String(exp)).digest("hex");
  return `${mac}.${exp}`;
}

function verify(token: string | undefined): boolean {
  if (!token) return false;
  const [mac, expStr] = token.split(".");
  const exp = Number(expStr);
  if (!mac || !Number.isFinite(exp) || exp < Date.now()) return false;
  const expected = createHmac("sha256", secret()).update(String(exp)).digest("hex");
  const a = Buffer.from(mac);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export function login(c: Context, password: string): boolean {
  const expected = process.env.DASHBOARD_PASSWORD;
  if (!expected) throw new Error("DASHBOARD_PASSWORD is not set");
  const a = Buffer.from(password);
  const b = Buffer.from(expected);
  const ok = a.length === b.length && timingSafeEqual(a, b);
  if (!ok) return false;

  const exp = Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1000;
  setCookie(c, COOKIE_NAME, sign(exp), {
    httpOnly: true,
    sameSite: "Lax",
    path: "/",
    maxAge: SESSION_DAYS * 24 * 60 * 60
  });
  return true;
}

export function isAuthenticated(c: Context): boolean {
  return verify(getCookie(c, COOKIE_NAME));
}

export async function requireAuth(c: Context, next: Next) {
  if (!isAuthenticated(c)) return c.json({ error: "unauthorized" }, 401);
  await next();
}
