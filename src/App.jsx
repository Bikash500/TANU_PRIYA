import React, { useEffect, useState } from "react";
import { apiRequest } from "./api.js";
import LoginPage from "./components/LoginPage.jsx";
import StudentsPage from "./features/students/StudentsPage.jsx";
import AttendancePage from "./features/attendance/AttendancePage.jsx";
import AcademicsPage from "./features/academics/AcademicsPage.jsx";
import ReportsPage from "./features/reports/ReportsPage.jsx";
import AccountsPage from "./features/accounts/AccountsPage.jsx";
import AuditPage from "./features/audit/AuditPage.jsx";
import GuardianPage from "./features/guardian/GuardianPage.jsx";
import SchoolStructurePage from "./features/structure/SchoolStructurePage.jsx";
import AiAssistantPage from "./features/assistant/AiAssistantPage.jsx";

const navigation = [
  { id: "structure", label: "School structure", roles: ["ADMIN"] },
  { id: "students", label: "Students", roles: ["ADMIN"] },
  { id: "attendance", label: "Attendance", roles: ["ADMIN", "TEACHER"] },
  { id: "academics", label: "Academics", roles: ["ADMIN"] },
  { id: "reports", label: "Reports", roles: ["ADMIN"] },
  { id: "accounts", label: "Accounts", roles: ["ADMIN"] },
  { id: "audit", label: "Audit history", roles: ["ADMIN"] },
  { id: "assistant", label: "AI Assistant", roles: ["ADMIN", "TEACHER", "GUARDIAN"] },
];

function SchoolBrand() {
  return (
    <a className="brand" href="/" aria-label="Pandey Technical School home">
      <span className="brand-mark"><span>PT</span></span>
      <span className="brand-copy">
        <span className="brand-name">PANDEY TECHNICAL SCHOOL</span>
        <span className="brand-tagline">LEARN <i /> BUILD <i /> LEAD</span>
      </span>
    </a>
  );
}

function getPageTitle(page, role) {
  if (role === "GUARDIAN" && page !== "assistant") return "Family portal";
  return navigation.find((item) => item.id === page)?.label || "Students";
}

export default function App() {
  const [session, setSession] = useState(null);
  const [page, setPage] = useState("students");
  const [academicYearLabel, setAcademicYearLabel] = useState("");
  const [academicYearError, setAcademicYearError] = useState("");
  const [currentTime, setCurrentTime] = useState(() => new Date());

  useEffect(() => {
    const timerId = window.setInterval(() => setCurrentTime(new Date()), 1000);
    return () => window.clearInterval(timerId);
  }, []);

  const schoolDateTime = new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  }).format(currentTime);

  useEffect(() => {
    if (!session || !["ADMIN", "TEACHER"].includes(session.user.role)) {
      setAcademicYearLabel("");
      setAcademicYearError("");
      return undefined;
    }
    let cancelled = false;
    async function loadAcademicYear() {
      try {
        const years = await apiRequest("/api/school/academic-years", session.credentials);
        if (cancelled) return;
        const current = years.find((year) => year.isCurrent);
        setAcademicYearLabel(current?.name || "");
        setAcademicYearError(current ? "" : "No active academic year is configured.");
      } catch (requestError) {
        if (!cancelled) setAcademicYearError(requestError.message);
      }
    }
    loadAcademicYear();
    window.addEventListener("academic-year-changed", loadAcademicYear);
    return () => {
      cancelled = true;
      window.removeEventListener("academic-year-changed", loadAcademicYear);
    };
  }, [session]);

  if (!session) return <LoginPage onLogin={(credentials, user) => {
    setSession({ credentials, user });
    setPage(user.role === "GUARDIAN" ? "guardian" : user.role === "TEACHER" ? "attendance" : "students");
  }} />;

  const { credentials, user } = session;
  const roleLinks = navigation.filter((item) => item.roles.includes(user.role));
  const selectedPage = user.role === "GUARDIAN" && page !== "assistant" ? "guardian" : page;

  function signOut() {
    setSession(null);
    setPage("students");
  }

  return (
    <main className="application-shell">
      <header className="app-header">
        <SchoolBrand />
        <div className="account-controls">
          <span className="user-chip">
            <span className="user-avatar">{user.username.slice(0, 1).toUpperCase()}</span>
            <span><strong>{user.username}</strong><small>{user.role.toLowerCase()}</small></span>
          </span>
          <button className="signout-button" onClick={signOut}>Sign out</button>
        </div>
      </header>
      <nav className="module-nav" aria-label="School modules">
        {user.role === "GUARDIAN" ? (
          <>
            <button
              type="button"
              className={`nav-link ${selectedPage === "guardian" ? "active" : ""}`}
              onClick={() => setPage("guardian")}
            >
              Family portal
            </button>
            {roleLinks.map((item) => (
              <button
                type="button"
                key={item.id}
                className={`nav-link ${selectedPage === item.id ? "active" : ""}`}
                onClick={() => setPage(item.id)}
              >
                {item.label}
              </button>
            ))}
          </>
        ) : roleLinks.map((item) => (
          <button
            type="button"
            key={item.id}
            className={`nav-link ${selectedPage === item.id ? "active" : ""}`}
            onClick={() => setPage(item.id)}
          >
            {item.label}
          </button>
        ))}
      </nav>
      <section className="content module-content">
        <section className="school-banner" aria-label="Pandey Technical School">
          <div className="school-banner-copy">
            <span className="school-banner-kicker">WELCOME TO YOUR SCHOOL</span>
            <h1>PANDEY<br />TECHNICAL SCHOOL</h1>
            <p>Learning today. Building tomorrow. Leading with purpose.</p>
            <div className="school-banner-motto"><span>LEARN</span><i /><span>BUILD</span><i /><span>LEAD</span></div>
            <time className="school-banner-datetime" dateTime={currentTime.toISOString()}>
              {schoolDateTime} IST
            </time>
          </div>
          <div className="school-banner-art" aria-hidden="true">
            <img src="/school-campus.svg" alt="" />
            <span className="school-banner-seal">PTS</span>
          </div>
        </section>
        <div className="module-title">
          <div>
            <p className="eyebrow">PANDEY TECHNICAL SCHOOL</p>
            <h1>{getPageTitle(selectedPage, user.role)}</h1>
          </div>
          {user.role !== "GUARDIAN" && (
            <span className="academic-year" title={academicYearError || undefined}>
              {academicYearError ? "ACADEMIC YEAR UNAVAILABLE"
                : academicYearLabel ? `ACADEMIC YEAR ${academicYearLabel}` : "LOADING ACADEMIC YEAR"}
            </span>
          )}
        </div>
        {selectedPage === "structure" && <SchoolStructurePage credentials={credentials} />}
        {selectedPage === "students" && <StudentsPage credentials={credentials} user={user} />}
        {selectedPage === "attendance" && <AttendancePage credentials={credentials} />}
        {selectedPage === "academics" && <AcademicsPage credentials={credentials} user={user} />}
        {selectedPage === "reports" && <ReportsPage credentials={credentials} />}
        {selectedPage === "accounts" && <AccountsPage credentials={credentials} />}
        {selectedPage === "audit" && <AuditPage credentials={credentials} />}
        {selectedPage === "guardian" && <GuardianPage credentials={credentials} />}
        {selectedPage === "assistant" && <AiAssistantPage credentials={credentials} />}
        <footer className="footer">© 2026 Pandey Technical School <span>·</span> School information system</footer>
      </section>
    </main>
  );
}
