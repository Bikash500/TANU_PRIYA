import { useEffect, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";

const statuses = ["PRESENT", "ABSENT", "LATE", "EXCUSED"];

export default function AttendancePage({ credentials }) {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [grade, setGrade] = useState("");
  const [entries, setEntries] = useState([]);
  const [savingId, setSavingId] = useState(null);
  const [error, setError] = useState("");

  async function loadAttendance() {
    try {
      const params = new URLSearchParams({ date });
      if (grade) params.set("grade", grade);
      setEntries(await apiRequest(`/api/attendance?${params}`, credentials));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  useEffect(() => { loadAttendance(); }, [credentials, date, grade]);

  async function markAttendance(entry, status) {
    setSavingId(entry.studentId);
    try {
      const updated = await apiRequest("/api/attendance", credentials, {
        method: "PUT",
        body: jsonBody({ studentId: entry.studentId, date, status }),
      });
      setEntries((current) => current.map((item) => item.studentId === updated.studentId ? updated : item));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSavingId(null);
    }
  }

  const markedCount = entries.filter((entry) => entry.status !== "UNMARKED").length;

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      <section className="stats-card module-stat">
        <div className="stat-icon">✓</div><div><p className="stat-label">Attendance marked</p><p className="stat-value">{markedCount} / {entries.length}</p></div><span className="stat-note">{date}</span>
      </section>
      <section className="directory">
        <div className="directory-heading directory-toolbar">
          <div><h2>Daily attendance</h2><p>Record present, absent, late or excused for each student</p></div>
          <div className="toolbar-actions">
            <label className="compact-field">Attendance date<input aria-label="Attendance date" type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>
            <label className="compact-field">Grade<select aria-label="Filter attendance by grade" value={grade} onChange={(event) => setGrade(event.target.value)}><option value="">All grades</option>{Array.from({ length: 12 }, (_, index) => <option key={index + 1}>Grade {index + 1}</option>)}</select></label>
          </div>
        </div>
        <div className="table-wrap"><table className="data-table">
          <thead><tr><th>STUDENT</th><th>ADMISSION NO.</th><th>GRADE</th><th>ATTENDANCE</th><th>RECORD</th></tr></thead>
          <tbody>{entries.length === 0 ? <tr><td colSpan="5" className="empty-state">No students found for this grade.</td></tr> : entries.map((entry) => (
            <tr key={entry.studentId}>
              <td><strong className="student-name">{entry.studentName}</strong></td><td><span className="admission-code">{entry.admissionNumber}</span></td><td><span className="grade-badge">{entry.grade}</span></td>
              <td><select className={`attendance-select status-${entry.status.toLowerCase()}`} aria-label={`Attendance for ${entry.studentName}`} value={entry.status} onChange={(event) => markAttendance(entry, event.target.value)}><option value="UNMARKED">Not marked</option>{statuses.map((status) => <option key={status} value={status}>{status[0] + status.slice(1).toLowerCase()}</option>)}</select></td>
              <td className="muted-text">{savingId === entry.studentId ? "Saving..." : entry.updatedAt ? `Updated ${entry.updatedAt}` : "—"}</td>
            </tr>
          ))}</tbody>
        </table></div>
        <div className="table-footer">Changes save immediately and are included in audit history.</div>
      </section>
    </>
  );
}
