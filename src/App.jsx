import { useState } from "react";
import LoginPage from "./components/LoginPage.jsx";
import StudentsPage from "./features/students/StudentsPage.jsx";
import AttendancePage from "./features/attendance/AttendancePage.jsx";
import AcademicsPage from "./features/academics/AcademicsPage.jsx";
import ReportsPage from "./features/reports/ReportsPage.jsx";
import AccountsPage from "./features/accounts/AccountsPage.jsx";
import AuditPage from "./features/audit/AuditPage.jsx";
import GuardianPage from "./features/guardian/GuardianPage.jsx";

const navigation = [
  { id: "students", label: "Students", roles: ["ADMIN"] },
  { id: "attendance", label: "Attendance", roles: ["ADMIN", "TEACHER"] },
  { id: "academics", label: "Academics", roles: ["ADMIN"] },
  { id: "reports", label: "Reports", roles: ["ADMIN"] },
  { id: "accounts", label: "Accounts", roles: ["ADMIN"] },
  { id: "audit", label: "Audit history", roles: ["ADMIN"] },
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
  if (role === "GUARDIAN") return "Family portal";
  return navigation.find((item) => item.id === page)?.label || "Students";
}

export default function App() {
  const [session, setSession] = useState(null);
  const [page, setPage] = useState("students");

  if (!session) return <LoginPage onLogin={(credentials, user) => {
    setSession({ credentials, user });
    setPage(user.role === "GUARDIAN" ? "guardian" : user.role === "TEACHER" ? "attendance" : "students");
  }} />;

  const { credentials, user } = session;
  const roleLinks = navigation.filter((item) => item.roles.includes(user.role));
  const selectedPage = user.role === "GUARDIAN" ? "guardian" : page;

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
          <span className="nav-current">Family portal</span>
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
          <span className="academic-year">ACADEMIC YEAR 2026–27</span>
        </div>
        {selectedPage === "students" && <StudentsPage credentials={credentials} user={user} />}
        {selectedPage === "attendance" && <AttendancePage credentials={credentials} />}
        {selectedPage === "academics" && <AcademicsPage credentials={credentials} user={user} />}
        {selectedPage === "reports" && <ReportsPage credentials={credentials} />}
        {selectedPage === "accounts" && <AccountsPage credentials={credentials} />}
        {selectedPage === "audit" && <AuditPage credentials={credentials} />}
        {selectedPage === "guardian" && <GuardianPage credentials={credentials} />}
        <footer className="footer">© 2026 Pandey Technical School <span>·</span> School information system</footer>
      </section>
    </main>
  );
}
