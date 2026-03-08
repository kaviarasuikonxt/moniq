// ui/src/api.js
const API_BASE = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8888").replace(/\/+$/, ""); // Remove trailing slashes

let accessToken = localStorage.getItem("accessToken") || "";

export function setAccessToken(token) {
  accessToken = token || "";
  if (accessToken) {
    localStorage.setItem("accessToken", accessToken);
  } else {
    localStorage.removeItem("accessToken");
  }
}

export function getAccessToken() {
  return accessToken;
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});

  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");

  const payload = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    const message =
      typeof payload === "object" && payload?.message
        ? payload.message
        : `Request failed: ${response.status}`;
    throw new Error(message);
  }

  return payload;
}

export async function loginV2(email, password) {
  return request("/auth/login/v2", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function uploadReceipt(formData) {
  return request("/api/receipts", {
    method: "POST",
    body: formData,
  });
}

export async function listReceipts() {
  return request("/api/receipts", {
    method: "GET",
  });
}

export async function getReceipt(id) {
  return request(`/api/receipts/${id}`, {
    method: "GET",
  });
}

export async function getReceiptItems(id) {
  return request(`/api/receipts/${id}/items`, {
    method: "GET",
  });
}

export async function getReceiptOcr(id) {
  return request(`/api/receipts/${id}/ocr`, {
    method: "GET",
  });
}