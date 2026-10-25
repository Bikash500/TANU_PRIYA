import React, { useEffect, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";

const initialForm = { username: "", password: "", role: "TEACHER", studentId: "" };

export default function AccountsPage({ credentials }) {
  const [accounts, setAccounts] = useState([]);
  const [students, setStudents] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function load() {
    try {
      const [userList, studentList] = await Promise.all([
        apiRequest("/api/users", credentials),
        apiRequest("/api/students", credentials),
      ]);
      setAccounts(userList);
      setStudents(studentList);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  useEffect(() => { load(); }, [credentials]);

  async function createAccount(event) {
    event.preventDefault();
    setSaving(true);
    try {
      const account = await apiRequest("/api/users", credentials, {
        method: "POST",
        body: jsonBody({
          ...form,
          studentId: form.role === "GUARDIAN" ? Number(form.studentId) : null,
        }),
      });
      setAccounts((current) => [...current, account].sort((left, right) => left.role.localeCompare(right.role) || left.username.localeCompare(right.username)));
      setForm(initialForm);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function deleteAccount(account) {
    if (!window.confirm(`Delete account ${account.username}?`)) return;
    try {
      await apiRequest(`/api/users/${account.id}`, credentials, { method: "DELETE" });
      setAccounts((current) => current.filter((item) => item.id !== account.id));
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      <section className="directory account-create">
        <div className="directory-heading"><div><h2>Create a school account</h2><p>Guardians must be linked to the student they can view</p></div></div>
        <form className="account-form" onSubmit={createAccount}>
          <label className="field-label">Email<input type="email" autoComplete="off" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} required maxLength="254" /></label>
          <label className="field-label">Temporary password<input type="password" autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required minLength="10" maxLength="100" /></label>
          <label className="field-label">Role<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}><option value="TEACHER">Teacher</option><option value="GUARDIAN">Guardian</option><option value="ADMIN">Administrator</option></select></label>
          {form.role === "GUARDIAN" && <label className="field-label">Linked student<select value={form.studentId} onChange={(event) => setForm({ ...form, studentId: event.target.value })} required><option value="">Select student</option>{students.map((student) => <option value={student.id} key={student.id}>{student.admissionNumber} — {student.name}</option>)}</select></label>}
          <button className="primary-button" disabled={saving}>{saving ? "Creating..." : "Create account"}</button>
        </form>
      </section>
      <section className="directory account-list">
        <div className="directory-heading"><div><h2>Accounts and roles</h2><p>Password hashes are stored in the database; passwords are never listed here.</p></div></div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>ACCOUNT</th><th>ROLE</th><th>LINKED STUDENT</th><th><span className="sr-only">Actions</span></th></tr></thead>
          <tbody>{accounts.map((account) => <tr key={account.id}><td><strong className="student-name">{account.username}</strong></td><td><span className={`role-badge role-${account.role.toLowerCase()}`}>{account.role}</span></td><td>{account.studentName || <span className="muted-text">—</span>}</td><td><button className="text-button danger-text" onClick={() => deleteAccount(account)}>Remove</button></td></tr>)}</tbody>
        </table></div>
      </section>
    </>
  );
}
