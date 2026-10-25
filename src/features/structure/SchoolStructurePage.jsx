import React, { useEffect, useMemo, useState } from "react";
import { apiRequest, jsonBody } from "../../api.js";

const today = new Date();
const nextStartYear = today.getFullYear() + 1;
const defaultYearForm = {
  name: `${nextStartYear}-${String(nextStartYear + 1).slice(-2)}`,
  startsOn: `${nextStartYear}-04-01`,
  endsOn: `${nextStartYear + 1}-03-31`,
};
const grades = Array.from({ length: 12 }, (_, index) => `Grade ${index + 1}`);

export default function SchoolStructurePage({ credentials }) {
  const [years, setYears] = useState([]);
  const [selectedYearId, setSelectedYearId] = useState("");
  const [students, setStudents] = useState([]);
  const [sections, setSections] = useState([]);
  const [enrollments, setEnrollments] = useState([]);
  const [yearForm, setYearForm] = useState(defaultYearForm);
  const [sectionForm, setSectionForm] = useState({ grade: "Grade 1", name: "A", room: "" });
  const [enrollmentForm, setEnrollmentForm] = useState({ studentId: "", classSectionId: "" });
  const [enrollmentDrafts, setEnrollmentDrafts] = useState({});
  const [editingSectionId, setEditingSectionId] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const selectedYear = years.find((year) => String(year.id) === selectedYearId);
  const enrolledStudentIds = useMemo(
    () => new Set(enrollments.map((enrollment) => enrollment.studentId)),
    [enrollments],
  );
  const availableStudents = students.filter((student) => !enrolledStudentIds.has(student.id));

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      apiRequest("/api/school/academic-years", credentials),
      apiRequest("/api/students", credentials),
    ]).then(([yearRecords, studentRecords]) => {
      if (cancelled) return;
      setYears(yearRecords);
      setStudents(studentRecords);
      const current = yearRecords.find((year) => year.isCurrent);
      setSelectedYearId((current || yearRecords[0]) ? String((current || yearRecords[0]).id) : "");
      setError("");
    }).catch((requestError) => {
      if (!cancelled) setError(requestError.message);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [credentials]);

  useEffect(() => {
    if (!selectedYearId) {
      setSections([]);
      setEnrollments([]);
      return undefined;
    }
    let cancelled = false;
    Promise.all([
      apiRequest(`/api/school/sections?academicYearId=${selectedYearId}`, credentials),
      apiRequest(`/api/school/enrollments?academicYearId=${selectedYearId}`, credentials),
    ]).then(([sectionRecords, enrollmentRecords]) => {
      if (cancelled) return;
      setSections(sectionRecords);
      setEnrollments(enrollmentRecords);
      setEnrollmentDrafts({});
      setError("");
    }).catch((requestError) => {
      if (!cancelled) setError(requestError.message);
    });
    return () => { cancelled = true; };
  }, [credentials, selectedYearId]);

  async function refreshYears(preferredId = selectedYearId) {
    const records = await apiRequest("/api/school/academic-years", credentials);
    setYears(records);
    const preferred = records.find((year) => String(year.id) === String(preferredId));
    const current = records.find((year) => year.isCurrent);
    setSelectedYearId(String((preferred || current || records[0])?.id || ""));
  }

  async function refreshYearData() {
    if (!selectedYearId) return;
    const [sectionRecords, enrollmentRecords] = await Promise.all([
      apiRequest(`/api/school/sections?academicYearId=${selectedYearId}`, credentials),
      apiRequest(`/api/school/enrollments?academicYearId=${selectedYearId}`, credentials),
    ]);
    setSections(sectionRecords);
    setEnrollments(enrollmentRecords);
    setEnrollmentDrafts({});
  }

  async function createAcademicYear(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const created = await apiRequest("/api/school/academic-years", credentials, {
        method: "POST",
        body: jsonBody(yearForm),
      });
      await refreshYears(created.id);
      setYearForm(defaultYearForm);
      setNotice(`${created.name} is ready. Add its class sections and enroll students.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function activateYear(year) {
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const activated = await apiRequest(`/api/school/academic-years/${year.id}/activate`, credentials, {
        method: "PUT",
      });
      await refreshYears(activated.id);
      window.dispatchEvent(new Event("academic-year-changed"));
      setNotice(`${activated.name} is now the active academic year.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function saveSection(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      if (editingSectionId) {
        await apiRequest(`/api/school/sections/${editingSectionId}`, credentials, {
          method: "PUT",
          body: jsonBody({ name: sectionForm.name, room: sectionForm.room }),
        });
      } else {
        await apiRequest("/api/school/sections", credentials, {
          method: "POST",
          body: jsonBody({ ...sectionForm, academicYearId: Number(selectedYearId) }),
        });
      }
      setSectionForm({ grade: "Grade 1", name: "A", room: "" });
      setEditingSectionId(null);
      await refreshYearData();
      setNotice(editingSectionId ? "Class section updated." : "Class section added.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function deleteSection(section) {
    if (!window.confirm(`Delete ${section.grade} / ${section.name}? This cannot be undone.`)) return;
    setError("");
    setNotice("");
    try {
      await apiRequest(`/api/school/sections/${section.id}`, credentials, { method: "DELETE" });
      await refreshYearData();
      setNotice("Class section deleted.");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function saveEnrollment(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await apiRequest("/api/school/enrollments", credentials, {
        method: "POST",
        body: jsonBody({
          studentId: Number(enrollmentForm.studentId),
          classSectionId: Number(enrollmentForm.classSectionId),
        }),
      });
      setEnrollmentForm({ studentId: "", classSectionId: "" });
      await refreshYearData();
      setNotice("Student enrolled in this academic year.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  async function updateEnrollment(enrollment) {
    const draft = enrollmentDrafts[enrollment.id] || {
      classSectionId: String(enrollment.classSectionId),
      status: enrollment.status,
    };
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await apiRequest(`/api/school/enrollments/${enrollment.id}`, credentials, {
        method: "PUT",
        body: jsonBody({
          classSectionId: Number(draft.classSectionId),
          status: draft.status,
        }),
      });
      await refreshYearData();
      setNotice(`Enrollment updated for ${enrollment.studentName}.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  function startEditingSection(section) {
    setEditingSectionId(section.id);
    setSectionForm({ grade: section.grade, name: section.name, room: section.room || "" });
    setError("");
  }

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      {notice && <div className="module-notice" role="status">{notice}</div>}

      <section className="directory structure-year-panel">
        <div className="directory-heading">
          <div><h2>Academic years</h2><p>Set the active school year and create future years before promotion.</p></div>
          {selectedYear && <span className="table-count">{selectedYear.isCurrent ? "Current year" : "Planning year"} · {selectedYear.name}</span>}
        </div>
        <div className="structure-year-list">
          {years.map((year) => (
            <button
              type="button"
              key={year.id}
              className={`structure-year-card ${selectedYearId === String(year.id) ? "selected" : ""}`}
              onClick={() => setSelectedYearId(String(year.id))}
            >
              <span><strong>{year.name}</strong><small>{year.startsOn} – {year.endsOn}</small></span>
              <span className={year.isCurrent ? "structure-current-tag" : "structure-year-tag"}>
                {year.isCurrent ? "ACTIVE" : "PLANNED"}
              </span>
            </button>
          ))}
          {!loading && years.length === 0 && <p className="structure-empty">No academic year has been created.</p>}
        </div>
        <form className="structure-form year-create-form" onSubmit={createAcademicYear}>
          <label className="field-label">Academic year
            <input required maxLength="20" minLength="4" value={yearForm.name}
              onChange={(event) => setYearForm({ ...yearForm, name: event.target.value })} />
          </label>
          <label className="field-label">Starts on
            <input type="date" required value={yearForm.startsOn}
              onChange={(event) => setYearForm({ ...yearForm, startsOn: event.target.value })} />
          </label>
          <label className="field-label">Ends on
            <input type="date" required value={yearForm.endsOn}
              onChange={(event) => setYearForm({ ...yearForm, endsOn: event.target.value })} />
          </label>
          <button className="primary-button" type="submit" disabled={saving}>＋ Add year</button>
        </form>
        {selectedYear && !selectedYear.isCurrent && (
          <div className="structure-activate-row">
            <p>Make <strong>{selectedYear.name}</strong> active to use its grades as the current student grades.</p>
            <button className="secondary-button" type="button" disabled={saving}
              onClick={() => activateYear(selectedYear)}>Set as active year</button>
          </div>
        )}
      </section>

      <div className="structure-columns">
        <section className="directory">
          <div className="directory-heading">
            <div><h2>Classes and sections</h2><p>{selectedYear ? `Structure for ${selectedYear.name}` : "Select an academic year"}</p></div>
            <span className="table-count">{sections.length} sections</span>
          </div>
          <form className="structure-form section-create-form" onSubmit={saveSection}>
            <label className="field-label">Grade
              <select required value={sectionForm.grade} disabled={Boolean(editingSectionId)}
                onChange={(event) => setSectionForm({ ...sectionForm, grade: event.target.value })}>
                {grades.map((grade) => <option key={grade}>{grade}</option>)}
              </select>
            </label>
            <label className="field-label">Section
              <input required maxLength="20" value={sectionForm.name}
                onChange={(event) => setSectionForm({ ...sectionForm, name: event.target.value })} />
            </label>
            <label className="field-label">Room (optional)
              <input maxLength="40" value={sectionForm.room}
                onChange={(event) => setSectionForm({ ...sectionForm, room: event.target.value })} />
            </label>
            <div className="structure-form-actions">
              <button className="primary-button" type="submit" disabled={saving || !selectedYearId}>
                {editingSectionId ? "Save section" : "＋ Add section"}
              </button>
              {editingSectionId && <button className="secondary-button" type="button"
                onClick={() => { setEditingSectionId(null); setSectionForm({ grade: "Grade 1", name: "A", room: "" }); }}>
                Cancel
              </button>}
            </div>
          </form>
          <div className="structure-card-list">
            {sections.map((section) => (
              <article className="structure-item-card" key={section.id}>
                <span className="structure-grade-mark">{section.grade.replace("Grade ", "")}</span>
                <div className="structure-item-copy">
                  <strong>{section.grade} · Section {section.name}</strong>
                  <small>{section.room || "Room not assigned"} · {section.enrolledCount} enrolled</small>
                </div>
                <div className="row-actions">
                  <button className="text-button" type="button" onClick={() => startEditingSection(section)}>Edit</button>
                  {section.enrollmentCount === 0 && (
                    <button className="icon-button delete-button" type="button"
                      aria-label={`Delete ${section.grade} section ${section.name}`}
                      onClick={() => deleteSection(section)}>×</button>
                  )}
                </div>
              </article>
            ))}
            {!loading && selectedYear && sections.length === 0 && (
              <p className="structure-empty">Add class sections for this year before enrolling students.</p>
            )}
          </div>
        </section>

        <section className="directory">
          <div className="directory-heading">
            <div><h2>Student enrollment</h2><p>Keep each student’s class placement for this year.</p></div>
            <span className="table-count">{enrollments.length} students</span>
          </div>
          <form className="structure-form enroll-form" onSubmit={saveEnrollment}>
            <label className="field-label">Student
              <select required value={enrollmentForm.studentId}
                onChange={(event) => setEnrollmentForm({ ...enrollmentForm, studentId: event.target.value })}>
                <option value="">Choose a student</option>
                {availableStudents.map((student) => (
                  <option value={student.id} key={student.id}>{student.admissionNumber} · {student.name}</option>
                ))}
              </select>
            </label>
            <label className="field-label">Class section
              <select required value={enrollmentForm.classSectionId}
                onChange={(event) => setEnrollmentForm({ ...enrollmentForm, classSectionId: event.target.value })}>
                <option value="">Choose a section</option>
                {sections.map((section) => (
                  <option value={section.id} key={section.id}>{section.grade} · {section.name}</option>
                ))}
              </select>
            </label>
            <button className="primary-button" type="submit"
              disabled={saving || availableStudents.length === 0 || sections.length === 0}>
              ＋ Enroll student
            </button>
          </form>
          <div className="table-wrap structure-enrollment-table">
            <table className="data-table">
              <thead><tr><th>STUDENT</th><th>GRADE / SECTION</th><th>STATUS</th><th><span className="sr-only">Actions</span></th></tr></thead>
              <tbody>
                {enrollments.map((enrollment) => {
                  const draft = enrollmentDrafts[enrollment.id] || {
                    classSectionId: String(enrollment.classSectionId),
                    status: enrollment.status,
                  };
                  return (
                    <tr key={enrollment.id}>
                      <td><strong className="student-name">{enrollment.studentName}</strong>
                        <small className="student-secondary">{enrollment.admissionNumber}</small></td>
                      <td><select aria-label={`Class section for ${enrollment.studentName}`} value={draft.classSectionId}
                        onChange={(event) => setEnrollmentDrafts({
                          ...enrollmentDrafts,
                          [enrollment.id]: { ...draft, classSectionId: event.target.value },
                        })}>
                        {sections.map((section) => (
                          <option value={section.id} key={section.id}>{section.grade} · {section.name}</option>
                        ))}
                      </select></td>
                      <td><select aria-label={`Enrollment status for ${enrollment.studentName}`} value={draft.status}
                        onChange={(event) => setEnrollmentDrafts({
                          ...enrollmentDrafts,
                          [enrollment.id]: { ...draft, status: event.target.value },
                        })}>
                        <option value="ENROLLED">Enrolled</option><option value="COMPLETED">Completed</option>
                        <option value="WITHDRAWN">Withdrawn</option>
                      </select></td>
                      <td><button className="text-button" type="button" disabled={saving}
                        onClick={() => updateEnrollment(enrollment)}>Save</button></td>
                    </tr>
                  );
                })}
                {!loading && enrollments.length === 0 && (
                  <tr><td className="empty-state" colSpan="4">No students are enrolled in this academic year yet.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
