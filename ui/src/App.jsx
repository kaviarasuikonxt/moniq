// ui/src/App.jsx
import { useEffect, useMemo, useState } from "react";
import {
  getAccessToken,
  getReceipt,
  getReceiptItems,
  getReceiptOcr,
  listReceipts,
  loginV2,
  setAccessToken,
  uploadReceipt,
} from "./api";
import "./styles.css";

function StatusBadge({ status }) {
  const cls =
    status === "OCR_COMPLETED"
      ? "badge badge-success"
      : status === "OCR_PENDING"
      ? "badge badge-warning"
      : status === "FAILED"
      ? "badge badge-danger"
      : "badge";

  return <span className={cls}>{status || "UNKNOWN"}</span>;
}

function App() {
  const [email, setEmail] = useState("userA@moniq.com");
  const [password, setPassword] = useState("P@ssw0rd123");
  const [loggedIn, setLoggedIn] = useState(!!getAccessToken());

  const [receipts, setReceipts] = useState([]);
  const [selectedReceiptId, setSelectedReceiptId] = useState(null);
  const [selectedReceipt, setSelectedReceipt] = useState(null);
  const [receiptItems, setReceiptItems] = useState(null);
  const [receiptOcr, setReceiptOcr] = useState(null);

  const [uploadFile, setUploadFile] = useState(null);
  const [uploading, setUploading] = useState(false);

  const [loadingReceipts, setLoadingReceipts] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);

  const [error, setError] = useState("");
  const [info, setInfo] = useState("");

  const selectedStatus = selectedReceipt?.status || receiptItems?.status || "";

  async function handleLogin(e) {
    e.preventDefault();
    setError("");
    setInfo("");

    try {
      const pair = await loginV2(email, password);
      setAccessToken(pair.accessToken);
      setLoggedIn(true);
      setInfo("Login successful.");
      await refreshReceipts();
    } catch (err) {
      setError(err.message || "Login failed");
    }
  }

  function handleLogout() {
    setAccessToken("");
    setLoggedIn(false);
    setReceipts([]);
    setSelectedReceiptId(null);
    setSelectedReceipt(null);
    setReceiptItems(null);
    setReceiptOcr(null);
    setError("");
    setInfo("Logged out.");
  }

  async function refreshReceipts() {
    if (!getAccessToken()) return;

    setLoadingReceipts(true);
    setError("");

    try {
      const data = await listReceipts();
      setReceipts(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || "Failed to load receipts");
    } finally {
      setLoadingReceipts(false);
    }
  }

  async function loadReceiptDetail(receiptId) {
    if (!receiptId) return;

    setLoadingDetail(true);
    setError("");

    try {
      const [receipt, items] = await Promise.all([
        getReceipt(receiptId),
        getReceiptItems(receiptId),
      ]);

      setSelectedReceipt(receipt);
      setReceiptItems(items);

      try {
        const ocr = await getReceiptOcr(receiptId);
        setReceiptOcr(ocr);
      } catch {
        setReceiptOcr(null);
      }
    } catch (err) {
      setError(err.message || "Failed to load receipt detail");
    } finally {
      setLoadingDetail(false);
    }
  }

  async function handleUpload(e) {
    e.preventDefault();
    if (!uploadFile) {
      setError("Please choose a receipt file.");
      return;
    }

    setUploading(true);
    setError("");
    setInfo("");

    try {
      const formData = new FormData();
      formData.append("file", uploadFile);

      const created = await uploadReceipt(formData);

      setInfo(`Receipt uploaded. Status: ${created.status}`);
      setUploadFile(null);

      await refreshReceipts();

      if (created?.id) {
        setSelectedReceiptId(created.id);
        await loadReceiptDetail(created.id);
      }
    } catch (err) {
      setError(err.message || "Upload failed");
    } finally {
      setUploading(false);
      const input = document.getElementById("receipt-file");
      if (input) input.value = "";
    }
  }

  useEffect(() => {
    if (loggedIn) {
      refreshReceipts();
    }
  }, [loggedIn]);

  useEffect(() => {
    if (selectedReceiptId && loggedIn) {
      loadReceiptDetail(selectedReceiptId);
    }
  }, [selectedReceiptId, loggedIn]);

  useEffect(() => {
    if (!selectedReceiptId || !loggedIn) return;
    if (selectedStatus !== "OCR_PENDING") return;

    const timer = setInterval(() => {
      loadReceiptDetail(selectedReceiptId);
      refreshReceipts();
    }, 3000);

    return () => clearInterval(timer);
  }, [selectedReceiptId, loggedIn, selectedStatus]);

  const orderedReceipts = useMemo(() => {
    return [...receipts].sort((a, b) => {
      const aDate = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bDate = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return bDate - aDate;
    });
  }, [receipts]);

  return (
    <div className="page">
      <div className="container">
        <header className="hero">
          <div>
            <h1>MoniQ Receipt OCR</h1>
            <p>Upload receipts, track OCR status, and review extracted items.</p>
          </div>
          {loggedIn && (
            <button className="secondary-btn" onClick={handleLogout}>
              Logout
            </button>
          )}
        </header>

        {!loggedIn ? (
          <section className="card narrow">
            <h2>Login</h2>
            <form onSubmit={handleLogin} className="form">
              <label>
                Email
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="userA@moniq.com"
                />
              </label>

              <label>
                Password
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Password"
                />
              </label>

              <button type="submit">Login</button>
            </form>
          </section>
        ) : (
          <>
            <section className="card">
              <h2>Upload Receipt</h2>
              <form onSubmit={handleUpload} className="form upload-form">
                <input
                  id="receipt-file"
                  type="file"
                  accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
                  onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                />
                <button type="submit" disabled={uploading}>
                  {uploading ? "Uploading..." : "Upload"}
                </button>
              </form>
            </section>

            {error && <div className="alert error">{error}</div>}
            {info && <div className="alert info">{info}</div>}

            <div className="grid">
              <section className="card">
                <div className="section-header">
                  <h2>Receipts</h2>
                  <button className="secondary-btn" onClick={refreshReceipts}>
                    Refresh
                  </button>
                </div>

                {loadingReceipts ? (
                  <p className="muted">Loading receipts...</p>
                ) : orderedReceipts.length === 0 ? (
                  <p className="muted">No receipts uploaded yet.</p>
                ) : (
                  <div className="receipt-list">
                    {orderedReceipts.map((r) => (
                      <button
                        key={r.id}
                        className={`receipt-row ${
                          selectedReceiptId === r.id ? "selected" : ""
                        }`}
                        onClick={() => setSelectedReceiptId(r.id)}
                      >
                        <div className="receipt-row-main">
                          <div className="receipt-title">{r.fileName || r.id}</div>
                          <div className="receipt-subtitle">
                            {r.createdAt
                              ? new Date(r.createdAt).toLocaleString()
                              : "No date"}
                          </div>
                        </div>
                        <StatusBadge status={r.status} />
                      </button>
                    ))}
                  </div>
                )}
              </section>

              <section className="card">
                <div className="section-header">
                  <h2>Receipt Detail</h2>
                  {selectedReceipt && <StatusBadge status={selectedStatus} />}
                </div>

                {!selectedReceiptId ? (
                  <p className="muted">Select a receipt to view details.</p>
                ) : loadingDetail ? (
                  <p className="muted">Loading receipt detail...</p>
                ) : !selectedReceipt ? (
                  <p className="muted">Receipt detail not available.</p>
                ) : (
                  <>
                    <div className="detail-grid">
                      <div>
                        <span className="label">Receipt ID</span>
                        <div className="value">{selectedReceipt.id}</div>
                      </div>
                      <div>
                        <span className="label">File</span>
                        <div className="value">{selectedReceipt.fileName}</div>
                      </div>
                      <div>
                        <span className="label">Content Type</span>
                        <div className="value">{selectedReceipt.contentType}</div>
                      </div>
                      <div>
                        <span className="label">Created</span>
                        <div className="value">
                          {selectedReceipt.createdAt
                            ? new Date(selectedReceipt.createdAt).toLocaleString()
                            : "-"}
                        </div>
                      </div>
                    </div>

                    {selectedReceipt.fileUrl && (
                      <div className="preview-link">
                        <a
                          href={selectedReceipt.fileUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          Open uploaded receipt
                        </a>
                      </div>
                    )}

                    {selectedStatus === "OCR_PENDING" && (
                      <div className="processing-box">
                        Processing... auto-refreshing every 3 seconds.
                      </div>
                    )}

                    {selectedStatus === "FAILED" && (
                      <div className="alert error">
                        OCR failed for this receipt.
                      </div>
                    )}

                    <div className="subsection">
                      <h3>Extracted Items</h3>
                      {!receiptItems || !receiptItems.items?.length ? (
                        <p className="muted">No items available yet.</p>
                      ) : (
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>Line</th>
                                <th>Item</th>
                                <th>Amount</th>
                                <th>Category</th>
                                <th>Confidence</th>
                              </tr>
                            </thead>
                            <tbody>
                              {receiptItems.items.map((item) => (
                                <tr key={item.id}>
                                  <td>{item.lineNo}</td>
                                  <td>{item.itemName || item.rawLine}</td>
                                  <td>
                                    {item.amount != null
                                      ? `${item.currency || "SGD"} ${item.amount}`
                                      : "-"}
                                  </td>
                                  <td>{item.category || "-"}</td>
                                  <td>
                                    {item.confidence != null
                                      ? Number(item.confidence).toFixed(2)
                                      : "-"}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>

                    <div className="subsection">
                      <h3>OCR Raw Text</h3>
                      {receiptOcr?.rawText ? (
                        <pre className="ocr-box">{receiptOcr.rawText}</pre>
                      ) : (
                        <p className="muted">OCR raw text not available yet.</p>
                      )}
                    </div>
                  </>
                )}
              </section>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default App;