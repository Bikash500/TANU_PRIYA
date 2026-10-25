import React, { useEffect, useMemo, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";

const emptyStudent = {
  admissionNumber: "",
  name: "",
  grade: "",
  email: "",
  dateOfBirth: "",
  guardianName: "",
  guardianEmail: "",
  guardianPhone: "",
  address: "",
  enrollmentDate: new Date().toISOString().slice(0, 10),
  status: "ACTIVE",
};
const pageSize = 8;

export default function StudentsPage({ credentials }) {
  const [students, setStudents] = useState([]);
  const [query, setQuery] = useState("");
  const [gradeFilter, setGradeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sort, setSort] = useState("name");
  const [page, setPage] = useState(1);
  const [form, setForm] = useState(emptyStudent);
  const [editingId, setEditingId] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  async function loadStudents() {
    setLoading(true);
    try {
      const records = await apiRequest("/api/students", credentials);
      setStudents(records);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadStudents(); }, [credentials]);

  const filteredStudents = useMemo(() => {
    const search = query.trim().toLowerCase();
    return students
      .filter((student) => !search || [
        student.admissionNumber, student.name, student.grade, student.email,
        student.guardianName, student.guardianEmail,
      ].some((value) => value?.toLowerCase().includes(search)))
      .filter((student) => !gradeFilter || student.grade === gradeFilter)
      .filter((student) => !statusFilter || student.status === statusFilter)
      .sort((left, right) => {
        if (sort === "grade") return left.grade.localeCompare(right.grade) || left.name.localeCompare(right.name);
        if (sort === "admissionNumber") return left.admissionNumber.localeCompare(right.admissionNumber);
        if (sort === "newest") return right.id - left.id;
        return left.name.localeCompare(right.name);
      });
  }, [students, query, gradeFilter, statusFilter, sort]);

  const pageCount = Math.max(1, Math.ceil(filteredStudents.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visibleStudents = filteredStudents.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  function openCreate() {
    const nextId = students.reduce((largest, student) => Math.max(largest, student.id), 0) + 1;
    setForm({ ...emptyStudent, admissionNumber: `PTS-${String(nextId).padStart(5, "0")}` });
    setEditingId(null);
    setError("");
    setDialogOpen(true);
  }

  function openEdit(student) {
    setForm(Object.fromEntries(Object.keys(emptyStudent).map((key) => [key, student[key] ?? ""])));
    setEditingId(student.id);
    setError("");
    setDialogOpen(true);
  }

  async function saveStudent(event) {
    event.preventDefault();
    try {
      const path = editingId ? `/api/students/${editingId}` : "/api/students";
      const saved = await apiRequest(path, credentials, {
        method: editingId ? "PUT" : "POST",
        body: jsonBody(form),
      });
      setStudents((current) => editingId
        ? current.map((student) => student.id === saved.id ? saved : student)
        : [...current, saved]);
      setDialogOpen(false);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function deleteStudent(student) {
    if (!window.confirm(`Permanently delete ${student.name} and related records?`)) return;
    try {
      await apiRequest(`/api/students/${student.id}`, credentials, { method: "DELETE" });
      setStudents((current) => current.filter((item) => item.id !== student.id));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  function updateField(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  return (
    <>
      <section className="stats-card module-stat">
        <div className="stat-icon">♧</div>
        <div><p className="stat-label">Enrolled students</p><p className="stat-value">{students.length}</p></div>
        <span className="stat-note">{students.filter((student) => student.status === "ACTIVE").length} active</span>
      </section>
      {error && <div className="module-error" role="alert">{error}</div>}
      <section className="directory">
        <div className="directory-heading directory-toolbar">
          <div><h2>Student directory</h2><p>Profiles, enrollment and family contact information</p></div>
          <div className="toolbar-actions">
            <label className="search-box"><span className="search-icon">⌕</span>
              <input aria-label="Search students" placeholder="Search students..." value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} />
            </label>
            <button className="primary-button" onClick={openCreate}>＋ Add student</button>
          </div>
        </div>
        <div className="filter-bar">
          <label>Grade
            <select value={gradeFilter} onChange={(event) => { setGradeFilter(event.target.value); setPage(1); }}>
              <option value="">All grades</option>
              {Array.from({ length: 12 }, (_, index) => <option key={index + 1}>Grade {index + 1}</option>)}
            </select>
          </label>
          <label>Status
            <select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(1); }}>
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option><option value="TRANSFERRED">Transferred</option><option value="GRADUATED">Graduated</option>
            </select>
          </label>
          <label>Sort by
            <select value={sort} onChange={(event) => setSort(event.target.value)}>
              <option value="name">Name</option><option value="grade">Grade</option><option value="admissionNumber">Admission number</option><option value="newest">Recently added</option>
            </select>
          </label>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead><tr><th>STUDENT</th><th>ADMISSION NO.</th><th>GRADE</th><th>GUARDIAN</th><th>STATUS</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {loading ? <tr><td className="empty-state" colSpan="6">Loading student profiles...</td></tr> : visibleStudents.map((student) => (
                <tr key={student.id}>
                  <td><div className="student-cell"><span className="avatar">{student.name.split(/\s+/).map((part) => part[0]).slice(0, 2).join("").toUpperCase()}</span><span><strong className="student-name">{student.name}</strong><small className="student-secondary">{student.email}</small></span></div></td>
                  <td><span className="admission-code">{student.admissionNumber}</span></td>
                  <td><span className="grade-badge">{student.grade}</span></td>
                  <td>{student.guardianName || <span className="muted-text">Not provided</span>}</td>
                  <td><span className={`status-badge status-${student.status.toLowerCase()}`}>{student.status}</span></td>
                  <td><div className="row-actions"><button className="text-button" onClick={() => openEdit(student)}>Profile</button><button className="icon-button delete-button" aria-label={`Delete ${student.name}`} onClick={() => deleteStudent(student)}>×</button></div></td>
                </tr>
              ))}
              {!loading && visibleStudents.length === 0 && <tr><td className="empty-state" colSpan="6">{students.length ? "No students match these filters." : "No students yet. Add a student profile to begin."}</td></tr>}
            </tbody>
          </table>
        </div>
        <div className="table-footer pagination-footer">
          <span>Showing {filteredStudents.length === 0 ? 0 : (currentPage - 1) * pageSize + 1}–{Math.min(currentPage * pageSize, filteredStudents.length)} of {filteredStudents.length} profiles</span>
          <div className="pagination-controls">
            <button className="secondary-button" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>Previous</button>
            <span>Page {currentPage} of {pageCount}</span>
            <button className="secondary-button" disabled={currentPage >= pageCount} onClick={() => setPage(currentPage + 1)}>Next</button>
          </div>
        </div>
      </section>

      {dialogOpen && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setDialogOpen(false); }}>
          <section className="modal profile-modal" role="dialog" aria-modal="true" aria-labelledby="student-form-title">
            <div className="modal-heading"><div><p className="eyebrow">{editingId ? "STUDENT PROFILE" : "NEW ENROLLMENT"}</p><h2 id="student-form-title">{editingId ? "Update student profile" : "Add student profile"}</h2></div><button className="icon-button modal-close" onClick={() => setDialogOpen(false)} aria-label="Close dialog">×</button></div>
            <form onSubmit={saveStudent}>
              <div className="profile-form-grid">
                <label className="field-label">Admission number<input name="admissionNumber" value={form.admissionNumber} onChange={updateField} required maxLength="20" /></label>
                <label className="field-label">Full name<input name="name" value={form.name} onChange={updateField} required maxLength="100" /></label>
                <label className="field-label">Grade<select name="grade" value={form.grade} onChange={updateField} required><option value="">Select a grade</option>{Array.from({ length: 12 }, (_, index) => <option key={index + 1}>Grade {index + 1}</option>)}</select></label>
                <label className="field-label">Student email<input type="text" inputMode="email" name="email" value={form.email} onChange={updateField} required maxLength="254" /></label>
                <label className="field-label">Date of birth<input type="date" name="dateOfBirth" value={form.dateOfBirth} onChange={updateField} /></label>
                <label className="field-label">Enrollment date<input type="date" name="enrollmentDate" value={form.enrollmentDate} onChange={updateField} required /></label>
                <label className="field-label">Guardian name<input name="guardianName" value={form.guardianName} onChange={updateField} maxLength="100" /></label>
                <label className="field-label">Guardian email<input type="text" inputMode="email" name="guardianEmail" value={form.guardianEmail} onChange={updateField} maxLength="254" /></label>
                <label className="field-label">Guardian phone<input type="tel" name="guardianPhone" value={form.guardianPhone} onChange={updateField} maxLength="30" /></label>
                <label className="field-label">Student status<select name="status" value={form.status} onChange={updateField}><option value="ACTIVE">Active</option><option value="TRANSFERRED">Transferred</option><option value="GRADUATED">Graduated</option></select></label>
                <label className="field-label profile-address">Home address<textarea name="address" value={form.address} onChange={updateField} rows="2" maxLength="500" /></label>
              </div>
              {error && <p className="form-error" role="alert">{error}</p>}
              <div className="form-actions"><button type="button" className="secondary-button" onClick={() => setDialogOpen(false)}>Cancel</button><button className="primary-button" type="submit">{editingId ? "Save profile" : "Create profile"}</button></div>
            </form>
          </section>
        </div>
      )}
    </>
  );
}
