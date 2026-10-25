import React, { useState } from "react";
import { apiRequest } from "../api.js";

const demoAccounts = [
  { role: "Administrator", username: "admin@pandey.edu", password: "AdminDemo2026!" },
  { role: "Teacher", username: "teacher@pandey.edu", password: "TeacherDemo2026!" },
  { role: "Guardian", username: "guardian@pandey.edu", password: "GuardianDemo2026!" },
];
const showDemoAccounts = import.meta.env.DEV;

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setIsSubmitting(true);
    setError("");
    const credentials = { username: username.trim(), password };
    try {
      const user = await apiRequest("/api/auth/me", credentials);
      onLogin(credentials, user);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <a className="brand login-brand" href="/" aria-label="Pandey Technical School home">
          <span className="brand-mark"><span>PT</span></span>
          <span className="brand-copy">
            <span className="brand-name">PANDEY TECHNICAL SCHOOL</span>
            <span className="brand-tagline">LEARN <i /> BUILD <i /> LEAD</span>
          </span>
        </a>
        <div className="login-heading">
          <p className="eyebrow">SCHOOL INFORMATION SYSTEM</p>
          <h1>Welcome back</h1>
          <p>Sign in to continue to your school portal.</p>
        </div>
        <form onSubmit={handleSubmit} className="login-form">
          <label className="field-label">
            School email
            <input
              type="email"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="you@pandey.edu"
              required
            />
          </label>
          <label className="field-label">
            Password
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Enter your password"
              required
            />
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button type="submit" className="primary-button login-submit" disabled={isSubmitting}>
            {isSubmitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
        {showDemoAccounts && (
          <details className="demo-accounts">
            <summary>Local demo accounts</summary>
            <p>These sample credentials are for local development only.</p>
            {demoAccounts.map((account) => (
              <button
                type="button"
                key={account.role}
                className="demo-account"
                onClick={() => {
                  setUsername(account.username);
                  setPassword(account.password);
                  setError("");
                }}
              >
                <span>{account.role}</span>
                <span>{account.username}</span>
              </button>
            ))}
          </details>
        )}
      </section>
      <p className="login-footer">© 2026 Pandey Technical School</p>
    </main>
  );
}
