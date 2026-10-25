import React, { useEffect, useState } from "react";
import { apiRequest } from "../../api.js";

export default function GuardianPage({ credentials }) {
  const [student, setStudent] = useState(null);
  const [attendance, setAttendance] = useState([]);
  const [academics, setAcademics] = useState([]);
  const [activity, setActivity] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      apiRequest("/api/students", credentials),
      apiRequest("/api/attendance", credentials),
      apiRequest("/api/academics", credentials),
      apiRequest("/api/audit/student-activity", credentials),
    ])
      .then(([students, attendanceRecords, academicRecords, activityRecords]) => {
        setStudent(students[0] || null);
        setAttendance(attendanceRecords);
        setAcademics(academicRecords);
        setActivity(activityRecords);
      })
      .catch((requestError) => setError(requestError.message));
  }, [credentials]);

  if (error) return <div className="module-error" role="alert">{error}</div>;
  if (!student) return <section className="directory empty-guardian"><h2>Student portal unavailable</h2><p>This guardian account is not linked to a student. Ask a school administrator to link the account.</p></section>;

  const presentCount = attendance.filter((entry) => entry.status === "PRESENT").length;
  return (
    <>
      <section className="guardian-welcome"><span className="guardian-avatar">{student.name.split(/\s+/).map((part) => part[0]).slice(0, 2).join("").toUpperCase()}</span><div><p className="eyebrow">FAMILY PORTAL</p><h2>{student.name}</h2><p>{student.admissionNumber} <span>·</span> {student.grade} <span>·</span> {student.status}</p></div></section>
      <div className="report-stat-grid guardian-stats">
        <section className="report-stat"><span>Attendance entries</span><strong>{attendance.length}</strong><small>{presentCount} marked present</small></section>
        <section className="report-stat"><span>Academic results</span><strong>{academics.length}</strong><small>Across all recorded terms</small></section>
      </div>
      <section className="directory guardian-section">
        <div className="directory-heading"><div><h2>Recent attendance</h2><p>Your linked student only</p></div></div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>DATE</th><th>STATUS</th><th>GRADE</th></tr></thead><tbody>{attendance.length ? attendance.map((entry) => <tr key={`${entry.studentId}-${entry.date}`}><td>{entry.date}</td><td><span className={`status-badge status-${entry.status.toLowerCase()}`}>{entry.status}</span></td><td>{entry.grade}</td></tr>) : <tr><td colSpan="3" className="empty-state">No attendance records have been entered yet.</td></tr>}</tbody></table></div>
      </section>
      <section className="directory guardian-section">
        <div className="directory-heading"><div><h2>Academic results</h2><p>Progress recorded by the school</p></div></div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>SUBJECT</th><th>TERM</th><th>SCORE</th><th>REMARKS</th></tr></thead><tbody>{academics.length ? academics.map((record) => <tr key={record.id}><td>{record.subject}</td><td>{record.term}</td><td><span className="score-badge">{record.score}%</span></td><td>{record.remarks || "—"}</td></tr>) : <tr><td colSpan="4" className="empty-state">No academic results have been entered yet.</td></tr>}</tbody></table></div>
      </section>
      <section className="directory guardian-section">
        <div className="directory-heading"><div><h2>School activity</h2><p>Recent changes and attendance for {student.name}, visible only to this family</p></div></div>
        <div className="guardian-activity-list">
          {activity.length ? activity.map((entry) => (
            <article className="guardian-activity-item" key={entry.id}>
              <span className="guardian-activity-mark" aria-hidden="true">✓</span>
              <div><strong>{entry.details || `${entry.action} ${entry.entityType.toLowerCase().replace("_", " ")}`}</strong><p>{entry.entityType.replace("_", " ")} · {entry.username}</p></div>
              <time>{new Date(entry.createdAt.replace(" ", "T") + (entry.createdAt.includes("Z") ? "" : "Z")).toLocaleString()}</time>
            </article>
          )) : <p className="guardian-activity-empty">School updates for this student will appear here.</p>}
        </div>
      </section>
    </>
  );
}
