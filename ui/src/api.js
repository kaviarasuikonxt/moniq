const API_BASE = import.meta.env.VITE_API_BASE_URL;

const TOKEN_KEY = "moniq_tokens";

export function getTokens() {
  const raw = localStorage.getItem(TOKEN_KEY);
  return raw ? JSON.parse(raw) : null;
}

export function setTokens(tokens) {
  localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY);
}

async function jsonFetch(path, opts = {}, retry = true) {
  const tokens = getTokens();
  const headers = {
    ...(opts.headers || {}),
    "Content-Type": "application/json"
  };
  if (tokens?.accessToken) {
    headers.Authorization = `Bearer ${tokens.accessToken}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...opts, headers });

  // Optional basic refresh-on-401 for protected endpoints
  if (res.status === 401 && retry && tokens?.refreshToken) {
    const refreshed = await refresh(tokens.refreshToken);
    if (refreshed?.accessToken) {
      setTokens({ ...tokens, ...refreshed });
      return jsonFetch(path, opts, false);
    }
  }

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function loginV2(email, password) {
  return jsonFetch("/auth/login/v2", {
    method: "POST",
    body: JSON.stringify({ email, password })
  }, false);
}

export async function refresh(refreshToken) {
  try {
    const data = await jsonFetch("/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken })
    }, false);
    return data;
  } catch {
    clearTokens();
    return null;
  }
}

export async function listReceipts() {
  return jsonFetch("/api/receipts", { method: "GET" });
}

export async function getReceipt(id) {
  return jsonFetch(`/api/receipts/${id}`, { method: "GET" });
}

export async function uploadReceipt({ file, merchant, receiptDate, totalAmount, currency }) {
  const tokens = getTokens();
  const headers = {};
  if (tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`;

  const form = new FormData();
  form.append("file", file);
  if (merchant) form.append("merchant", merchant);
  if (receiptDate) form.append("receiptDate", receiptDate);
  if (totalAmount) form.append("totalAmount", totalAmount);
  if (currency) form.append("currency", currency);

  let res = await fetch(`${API_BASE}/api/receipts`, {
    method: "POST",
    headers,
    body: form
  });

  if (res.status === 401 && tokens?.refreshToken) {
    const refreshed = await refresh(tokens.refreshToken);
    if (refreshed?.accessToken) {
      setTokens({ ...tokens, ...refreshed });
      const headers2 = { Authorization: `Bearer ${refreshed.accessToken}` };
      res = await fetch(`${API_BASE}/api/receipts`, {
        method: "POST",
        headers: headers2,
        body: form
      });
    }
  }

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json();
}