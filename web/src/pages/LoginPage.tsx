import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

export default function LoginPage() {
  const [password, setPassword] = useState("");
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(false);
    const ok = await api.login(password);
    setBusy(false);
    if (ok) {
      navigate("/");
    } else {
      setError(true);
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>🚲 Bike Tracker</h1>
        {error ? <div className="error">Wrong password</div> : null}
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoFocus
        />
        <button type="submit" disabled={busy || password.length === 0}>
          {busy ? "…" : "Enter"}
        </button>
      </form>
    </div>
  );
}
