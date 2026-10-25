import React, { useEffect, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";

export default function AcademicsPage({ credentials, user }) {
  const [students, setStudents] = useState([]);
  const [records, setRecords] = useState([]);
  const [form, setForm] = useState({ studentId: "", subject: "", term: "Term 1", score: "", remarks: "" });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function loadData() {
    try {
      const [studentList, academicRecords] = await Promise.all([
        apiRequest("/api/students", credentials),
        apiRequest("/api/academics", credentials),
      ]);
      setStudents(studentList);
      setRecords(academicRecords);
      setForm((current) => ({ ...current, studentId: current.studentId || String(studentList[0]?.id || "") }));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  useEffect(() => { loadData(); }, [credentials]);

  async function addRecord(event) {
    event.preventDefault();
    setSaving(true);
    try {
      const record = await apiRequest("/api/academics", credentials, {
        method: "POST",
        body: jsonBody({ ...form, studentId: Number(form.studentId), score: Number(form.score) }),
      });
      setRecords((current) => [record, ...current]);
      setForm((current) => ({ ...current, subject: "", score: "", remarks: "" }));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function deleteRecord(record) {
    if (!window.confirm(`Delete ${record.subject} for ${record.studentName}?`)) return;
    try {
      await apiRequest(`/api/academics/${record.id}`, credentials, { method: "DELETE" });
      setRecords((current) => current.filter((item) => item.id !== record.id));
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      <section className="stats-card module-stat"><div className="stat-icon">✦</div><div><p className="stat-label">Academic records</p><p className="stat-value">{records.length}</p></div><span className="stat-note">Scores out of 100</span></section>
      {user.role !== "GUARDIAN" && (
        <section className="directory record-entry">
          <div className="directory-heading"><div><h2>Record a result</h2><p>Add a subject score for a student</p></div></div>
          <form className="record-form" onSubmit={addRecord}>
            <label className="field-label">Student<select value={form.studentId} onChange={(event) => setForm({ ...form, studentId: event.target.value })} required>{students.map((student) => <option key={student.id} value={student.id}>{student.admissionNumber} — {student.name}</option>)}</select></label>
            <label className="field-label">Subject<input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} maxLength="100" required placeholder="e.g. Mathematics" /></label>
            <label className="field-label">Term<input value={form.term} onChange={(event) => setForm({ ...form, term: event.target.value })} maxLength="50" required placeholder="e.g. Term 1" /></label>
            <label className="field-label">Score (0–100)<input type="number" min="0" max="100" step="0.1" value={form.score} onChange={(event) => setForm({ ...form, score: event.target.value })} required /></label>
            <label className="field-label">Remarks<input value={form.remarks} onChange={(event) => setForm({ ...form, remarks: event.target.value })} maxLength="500" placeholder="Optional feedback" /></label>
            <button className="primary-button" disabled={saving || students.length === 0}>{saving ? "Saving..." : "Add result"}</button>
          </form>
        </section>
      )}
      <section className="directory academic-list">
        <div className="directory-heading"><div><h2>Results and progress</h2><p>Subject grades by student and term</p></div></div>
        <div className="table-wrap"><table className="data-table">
          <thead><tr><th>STUDENT</th><th>SUBJECT</th><th>TERM</th><th>SCORE</th><th>REMARKS</th>{user.role === "ADMIN" && <th><span className="sr-only">Actions</span></th>}</tr></thead>
          <tbody>{records.length === 0 ? <tr><td className="empty-state" colSpan={user.role === "ADMIN" ? 6 : 5}>No academic records yet.</td></tr> : records.map((record) => (
            <tr key={record.id}><td><strong className="student-name">{record.studentName}</strong><small className="student-secondary">{record.admissionNumber}</small></td><td>{record.subject}</td><td>{record.term}</td><td><span className="score-badge">{record.score}%</span></td><td>{record.remarks || <span className="muted-text">—</span>}</td>{user.role === "ADMIN" && <td><button className="icon-button delete-button" aria-label={`Delete ${record.subject} record for ${record.studentName}`} onClick={() => deleteRecord(record)}>×</button></td>}</tr>
          ))}</tbody>
        </table></div>
      </section>
    </>
  );
}
