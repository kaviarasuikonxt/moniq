import React, { useEffect, useMemo, useState } from "react";
import {
  clearTokens,
  getReceipt,
  getTokens,
  loginV2,
  listReceipts,
  setTokens,
  uploadReceipt
} from "./api.js";

export default function App() {
  const [tokens, setTokensState] = useState(() => getTokens());
  const [email, setEmail] = useState("test_user1@moniq.com");
  const [password, setPassword] = useState("P@ssw0rd123");
  const [error, setError] = useState("");

  const [file, setFile] = useState(null);
  const [merchant, setMerchant] = useState("");
  const [receiptDate, setReceiptDate] = useState("");
  const [totalAmount, setTotalAmount] = useState("");
  const [currency, setCurrency] = useState("SGD");

  const [receipts, setReceipts] = useState([]);
  const [selected, setSelected] = useState(null);

  const loggedIn = useMemo(() => !!tokens?.accessToken, [tokens]);

  async function doLogin(e) {
    e.preventDefault();
    setError("");
    try {
      const pair = await loginV2(email, password);
      setTokens(pair);
      setTokensState(pair);
    } catch (err) {
      setError(String(err.message || err));
    }
  }

  function doLogout() {
    clearTokens();
    setTokensState(null);
    setReceipts([]);
    setSelected(null);
  }

  async function refreshList() {
    setError("");
    try {
      const list = await listReceipts();
      setReceipts(list);
    } catch (err) {
      setError(String(err.message || err));
    }
  }

  async function openDetail(id) {
    setError("");
    try {
      const d = await getReceipt(id);
      setSelected(d);
    } catch (err) {
      setError(String(err.message || err));
    }
  }

  async function doUpload(e) {
    e.preventDefault();
    setError("");
    if (!file) {
      setError("Choose a file first");
      return;
    }
    try {
      const payload = {
        file,
        merchant,
        receiptDate: receiptDate ? new Date(receiptDate).toISOString() : "",
        totalAmount,
        currency
      };
      await uploadReceipt(payload);
      setFile(null);
      setMerchant("");
      setReceiptDate("");
      setTotalAmount("");
      await refreshList();
    } catch (err) {
      setError(String(err.message || err));
    }
  }

  useEffect(() => {
    if (loggedIn) refreshList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loggedIn]);

  return (
    <div className="page">
      <header className="header">
        <div>
          <div className="title">Moniq MVP</div>
          <div className="subtitle">Receipt → Upload → List → Detail</div>
        </div>
        <div className="right">
          {loggedIn ? (
            <button className="btn" onClick={doLogout}>Logout</button>
          ) : null}
        </div>
      </header>

      {error ? <div className="error">{error}</div> : null}

      {!loggedIn ? (
        <section className="card">
          <h2>Login (v2)</h2>
          <form onSubmit={doLogin} className="form">
            <label>
              Email
              <input value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <label>
              Password
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <button className="btn primary" type="submit">Login</button>
          </form>
        </section>
      ) : (
        <>
          <section className="card">
            <h2>Upload Receipt</h2>
            <form onSubmit={doUpload} className="form">
              <label>
                File (jpg/png/pdf)
                <input
                  type="file"
                  accept="image/jpeg,image/png,application/pdf"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                />
              </label>

              <div className="row">
                <label>
                  Merchant (optional)
                  <input value={merchant} onChange={(e) => setMerchant(e.target.value)} />
                </label>
                <label>
                  Receipt Date (optional)
                  <input type="datetime-local" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} />
                </label>
              </div>

              <div className="row">
                <label>
                  Total Amount (optional)
                  <input value={totalAmount} onChange={(e) => setTotalAmount(e.target.value)} placeholder="23.45" />
                </label>
                <label>
                  Currency
                  <input value={currency} onChange={(e) => setCurrency(e.target.value)} />
                </label>
              </div>

              <button className="btn primary" type="submit">Upload</button>
            </form>
          </section>

          <section className="card">
            <div className="row space">
              <h2>Your Receipts</h2>
              <button className="btn" onClick={refreshList}>Refresh</button>
            </div>

            {receipts.length === 0 ? (
              <div className="muted">No receipts yet.</div>
            ) : (
              <div className="list">
                {receipts.map((r) => (
                  <div key={r.id} className="listItem">
                    <div className="listMain">
                      <div className="listTitle">{r.merchant || r.fileName}</div>
                      <div className="muted">
                        {r.status} • {new Date(r.createdAt).toLocaleString()}
                        {r.totalAmount ? ` • ${r.currency} ${r.totalAmount}` : ""}
                      </div>
                    </div>
                    <div className="listActions">
                      <button className="btn" onClick={() => openDetail(r.id)}>Open</button>
                      <a className="btn" href={r.fileUrl} target="_blank" rel="noreferrer">View File</a>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {selected ? (
            <section className="card">
              <div className="row space">
                <h2>Receipt Detail</h2>
                <button className="btn" onClick={() => setSelected(null)}>Close</button>
              </div>

              <div className="kv">
                <div><b>ID:</b> {selected.id}</div>
                <div><b>Status:</b> {selected.status}</div>
                <div><b>File:</b> {selected.fileName} ({selected.contentType}, {selected.fileSizeBytes} bytes)</div>
                <div><b>Merchant:</b> {selected.merchant || "-"}</div>
                <div><b>Receipt Date:</b> {selected.receiptDate ? new Date(selected.receiptDate).toLocaleString() : "-"}</div>
                <div><b>Total:</b> {selected.totalAmount ? `${selected.currency} ${selected.totalAmount}` : "-"}</div>
                <div><b>Created:</b> {new Date(selected.createdAt).toLocaleString()}</div>
              </div>

              <div style={{ marginTop: 12 }}>
                <a className="btn primary" href={selected.fileUrl} target="_blank" rel="noreferrer">
                  Open File URL
                </a>
              </div>
            </section>
          ) : null}
        </>
      )}
    </div>
  );
}