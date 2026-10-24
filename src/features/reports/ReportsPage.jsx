import { useEffect, useState } from "react";
import { apiRequest } from "../../api.js";

const exports = [
  { id: "students", title: "Student directory", description: "Enrollment, grade and family contact details", href: "/api/reports/students.csv" },
  { id: "attendance", title: "Attendance report", description: "Daily attendance by student and grade", href: "/api/reports/attendance.csv" },
  { id: "academics", title: "Academic results", description: "Subject results and term progress", href: "/api/reports/academics.csv" },
];

const reportColumns = {
  students: [
    ["admissionNumber", "Admission no."],
    ["name", "Student"],
    ["grade", "Grade"],
    ["email", "Student email"],
    ["guardianName", "Guardian"],
    ["guardianEmail", "Guardian email"],
    ["status", "Status"],
  ],
  attendance: [
    ["admissionNumber", "Admission no."],
    ["studentName", "Student"],
    ["grade", "Grade"],
    ["status", "Attendance"],
    ["date", "Date"],
  ],
  academics: [
    ["admissionNumber", "Admission no."],
    ["studentName", "Student"],
    ["grade", "Grade"],
    ["subject", "Subject"],
    ["term", "Term"],
    ["score", "Score / 100"],
    ["remarks", "Remarks"],
  ],
};

export default function ReportsPage({ credentials }) {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState("");
  const [previewing, setPreviewing] = useState("");
  const [preview, setPreview] = useState(null);
  const [attendanceDate, setAttendanceDate] = useState(new Date().toISOString().slice(0, 10));

  useEffect(() => {
    apiRequest("/api/reports/summary", credentials)
      .then(setSummary)
      .catch((requestError) => setError(requestError.message));
  }, [credentials]);

  async function openPreview(report) {
    setPreviewing(report.id);
    try {
      const path = report.id === "attendance"
        ? `/api/attendance?${new URLSearchParams({ date: attendanceDate })}`
        : report.id === "students" ? "/api/students" : "/api/academics";
      const rows = await apiRequest(path, credentials);
      setPreview({
        ...report,
        rows,
        generatedAt: new Date(),
        reportDate: report.id === "attendance" ? attendanceDate : null,
      });
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setPreviewing("");
    }
  }

  async function download(report) {
    setDownloading(report.id);
    try {
      const path = report.id === "attendance"
        ? `${report.href}?${new URLSearchParams({ date: attendanceDate })}`
        : report.href;
      const blob = await apiRequest(path, credentials);
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = report.id === "attendance"
        ? `attendance-report-${attendanceDate}.csv`
        : `${report.id}-report.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setDownloading("");
    }
  }

  const cards = [
    ["Active students", summary?.activeStudents ?? "—", "Currently enrolled"],
    ["Grade levels", summary?.gradeCount ?? "—", "With enrolled students"],
    ["Present today", summary?.presentToday ?? "—", "Attendance recorded today"],
    ["Academic results", summary?.academicRecords ?? "—", "Scores entered"],
  ];

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      <div className="report-stat-grid">{cards.map(([label, value, hint]) => <section className="report-stat" key={label}><span>{label}</span><strong>{value}</strong><small>{hint}</small></section>)}</div>
      <section className="directory report-directory">
        <div className="directory-heading directory-toolbar">
          <div><h2>School reports</h2><p>Preview polished, print-ready HTML reports or export CSV files</p></div>
          <label className="compact-field">Attendance report date<input aria-label="Report attendance date" type="date" value={attendanceDate} onChange={(event) => setAttendanceDate(event.target.value)} /></label>
        </div>
        <div className="report-list">{exports.map((report) => (
          <article className="report-card" key={report.id}>
            <span className="report-icon" aria-hidden="true">▤</span>
            <div className="report-copy"><h3>{report.title}</h3><p>{report.description}</p></div>
            <div className="report-actions">
              <button className="secondary-button" disabled={Boolean(previewing)} onClick={() => openPreview(report)}>{previewing === report.id ? "Preparing..." : "View HTML"}</button>
              <button className="secondary-button" disabled={Boolean(downloading)} onClick={() => download(report)}>{downloading === report.id ? "Preparing..." : "Download CSV"}</button>
            </div>
          </article>
        ))}</div>
      </section>
      {preview && (
        <section className="report-preview" aria-labelledby="report-preview-title">
          <div className="report-preview-actions">
            <span>HTML report preview</span>
            <button className="secondary-button" onClick={() => window.print()}>Print report</button>
          </div>
          <header className="report-paper-header">
            <div className="report-school-mark" aria-hidden="true">PT</div>
            <div>
              <p className="report-school-name">PANDEY TECHNICAL SCHOOL</p>
              <h2 id="report-preview-title">{preview.title}</h2>
              <p className="report-school-tagline">LEARN · BUILD · LEAD</p>
            </div>
            <div className="report-generated">
              <span>{preview.reportDate ? "REPORT DATE" : "GENERATED"}</span>
              <strong>{preview.reportDate || preview.generatedAt.toLocaleDateString()}</strong>
            </div>
          </header>
          <div className="report-paper-summary">
            <span><strong>{preview.rows.length}</strong> records</span>
            <span>{preview.reportDate ? `Attendance for ${new Date(`${preview.reportDate}T00:00:00`).toLocaleDateString()}` : "School information system"}</span>
          </div>
          <div className="table-wrap report-preview-table">
            <table className="data-table">
              <thead><tr>{reportColumns[preview.id].map(([, label]) => <th key={label}>{label}</th>)}</tr></thead>
              <tbody>
                {preview.rows.length === 0
                  ? <tr><td className="empty-state" colSpan={reportColumns[preview.id].length}>No records are available for this report.</td></tr>
                  : preview.rows.map((row, index) => (
                    <tr key={row.id || row.studentId || row.admissionNumber || index}>
                      {reportColumns[preview.id].map(([key]) => <td key={key}>{row[key] ?? "—"}</td>)}
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <footer className="report-paper-footer">Confidential school record · Pandey Technical School</footer>
        </section>
      )}
    </>
  );
}
