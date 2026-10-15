import { useState } from "react";
import {
  ArrowDownToLine,
  ArrowRight,
  Bell,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  Check,
  ChevronDown,
  CircleHelp,
  Clock3,
  GraduationCap,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Mail,
  Menu,
  Search,
  ShieldCheck,
  UserRound,
  UsersRound,
  X,
} from "lucide-react";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";

function getInitials(name) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function formatDate(value) {
  if (!value) return "Not provided";
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(date);
}

function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const response = await fetch(`${apiBase}/api/auth/demo-login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const responseText = await response.text();
      let result;
      try {
        result = JSON.parse(responseText);
      } catch {
        throw new Error(
          response.ok
            ? "The server returned an invalid response."
            : `The server rejected the request (HTTP ${response.status}). Check that the local API and CORS settings are configured.`,
        );
      }
      if (!response.ok) {
        throw new Error(result.message ?? "Unable to sign in. Please try again.");
      }
      onLogin(result);
    } catch (requestError) {
      setError(
        requestError instanceof TypeError
          ? "Can’t reach the server. Make sure the Spring Boot API is running."
          : requestError.message,
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-decoration login-decoration-one" />
      <div className="login-decoration login-decoration-two" />
      <section className="login-card">
        <div className="brand-mark login-brand-mark">
          <GraduationCap size={25} strokeWidth={2.2} />
        </div>
        <div className="login-eyebrow">STAFF PORTAL</div>
        <h1>Your workday,<br />all in one place.</h1>
        <p className="login-subtitle">
          Sign in to your university account to view your staff profile.
        </p>
        <div className="demo-notice" role="note">
          <ShieldCheck size={18} />
          <span>
            <strong>Development demo</strong>
            Email entry doesn’t verify identity. Don’t use this sign-in for
            real employee data or production access.
          </span>
        </div>
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="work-email">Work email</label>
          <div className="email-field">
            <Mail size={18} aria-hidden="true" />
            <input
              id="work-email"
              type="email"
              autoComplete="email"
              placeholder="you@scot.lk"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
          <button className="primary-button login-button" disabled={busy}>
            {busy ? "Signing in…" : "Continue with email"}
            {!busy && <ArrowRight size={18} />}
          </button>
        </form>
        <div className="login-footnote">
          <LockKeyhole size={14} />
          For authorised staff use
        </div>
      </section>
      <div className="login-footer">SCOT · UNIVERSITY MANAGEMENT SYSTEM</div>
    </main>
  );
}

function DetailCard({ icon: Icon, label, value, accent = false }) {
  return (
    <div className={`detail-card${accent ? " detail-card-accent" : ""}`}>
      <div className="detail-icon"><Icon size={18} /></div>
      <div className="detail-copy">
        <span className="detail-label">{label}</span>
        <span className="detail-value">{value || "Not provided"}</span>
      </div>
    </div>
  );
}

function Profile({ employee, onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const name = employee.preferredName || employee.fullName || "Staff member";
  const [firstName] = name.trim().split(/\s+/);

  return (
    <div className="app-shell">
      <aside className={`sidebar${menuOpen ? " sidebar-open" : ""}`}>
        <div className="sidebar-brand">
          <div className="brand-mark"><GraduationCap size={24} /></div>
          <div><strong>SCOT</strong><span>UNIVERSITY PORTAL</span></div>
          <button
            className="icon-button sidebar-close"
            onClick={() => setMenuOpen(false)}
            aria-label="Close navigation"
          >
            <X size={20} />
          </button>
        </div>
        <div className="nav-section-label">MAIN MENU</div>
        <nav className="side-nav" aria-label="Main navigation">
          <a className="nav-item" href="#profile">
            <LayoutDashboard size={18} /><span>Dashboard</span>
          </a>
          <a className="nav-item nav-item-active" href="#profile" aria-current="page">
            <UserRound size={18} /><span>My Profile</span><span className="active-indicator" />
          </a>
          <div className="nav-section-label nav-section-spaced">UNIVERSITY</div>
          <a className="nav-item nav-item-disabled" href="#profile" aria-disabled="true">
            <CalendarDays size={18} /><span>Attendance</span><span className="coming-soon">SOON</span>
          </a>
          <a className="nav-item nav-item-disabled" href="#profile" aria-disabled="true">
            <UsersRound size={18} /><span>Staff directory</span><span className="coming-soon">SOON</span>
          </a>
          <a className="nav-item nav-item-disabled" href="#profile" aria-disabled="true">
            <Building2 size={18} /><span>Departments</span><span className="coming-soon">SOON</span>
          </a>
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon"><CircleHelp size={17} /></div>
            <div><strong>Need a hand?</strong><span>Contact the HR team</span></div>
            <ArrowRight size={15} />
          </div>
          <div className="sidebar-user">
            <div className="avatar avatar-small">{getInitials(name)}</div>
            <div className="sidebar-user-copy">
              <strong>{name}</strong><span>{employee.designation || "Staff member"}</span>
            </div>
            <button
              className="icon-button logout-icon"
              onClick={onLogout}
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>

      {menuOpen && (
        <button
          className="mobile-scrim"
          onClick={() => setMenuOpen(false)}
          aria-label="Close navigation"
        />
      )}

      <main className="main-area">
        <header className="topbar">
          <button
            className="icon-button mobile-menu"
            onClick={() => setMenuOpen(true)}
            aria-label="Open navigation"
          >
            <Menu size={21} />
          </button>
          <label className="topbar-search">
            <Search size={15} />
            <input aria-label="Search portal" placeholder="Search..." />
            <kbd>⌘ K</kbd>
          </label>
          <div className="topbar-actions">
            <button className="icon-button notification-button" aria-label="Notifications">
              <Bell size={19} /><span className="notification-dot" />
            </button>
            <div className="topbar-divider" />
            <button className="topbar-account" onClick={() => setMenuOpen(!menuOpen)}>
              <div className="avatar avatar-top">{getInitials(name)}</div>
              <span>{firstName}</span><ChevronDown size={15} />
            </button>
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-row">
            <div>
              <h1>My Profile</h1>
              <p>View your staff information and employment details.</p>
            </div>
            <button className="outline-button" onClick={() => window.print()}>
              <ArrowDownToLine size={17} /> Export profile
            </button>
          </section>

          <section className="profile-hero">
            <div className="hero-content">
              <div className="avatar avatar-hero">{getInitials(name)}</div>
              <div className="hero-copy">
                <div className="hero-overline">Welcome back, {firstName} <span className="wave">👋</span></div>
                <h2>{employee.title ? `${employee.title} ` : ""}{employee.fullName || name}</h2>
                <p>{employee.designation || "University staff"}{employee.department ? <><span className="hero-separator">·</span>{employee.department}</> : null}</p>
                <div className="hero-tags">
                  {employee.staffId && <span className="hero-tag"><BriefcaseBusiness size={14} /> Staff ID&nbsp; {employee.staffId}</span>}
                  {employee.role && <span className="hero-tag"><ShieldCheck size={14} /> {employee.role}</span>}
                </div>
              </div>
            </div>
            <div className="hero-highlights">
              <div className="hero-highlight">
                <span className="highlight-icon"><Building2 size={18} /></span>
                <span><small>Department</small><strong>{employee.department || "Not provided"}</strong></span>
              </div>
              <div className="hero-highlight">
                <span className="highlight-icon"><BriefcaseBusiness size={18} /></span>
                <span><small>Position</small><strong>{employee.designation || "Not provided"}</strong></span>
              </div>
            </div>
          </section>

          <section className="profile-metrics" aria-label="Employment summary">
            <article className="metric-card metric-slate">
              <span className="metric-label">STAFF ID</span>
              <strong>{employee.staffId || "—"}</strong>
              <span className="metric-caption">University staff record</span>
              <span className="metric-icon"><UserRound size={23} /></span>
            </article>
            <article className="metric-card metric-red">
              <span className="metric-label">EMPLOYMENT STATUS</span>
              <strong>{employee.status || "—"}</strong>
              <span className="metric-caption">Current staff status</span>
              <span className="metric-icon"><ShieldCheck size={23} /></span>
            </article>
            <article className="metric-card metric-green">
              <span className="metric-label">DEPARTMENT</span>
              <strong>{employee.department || "—"}</strong>
              <span className="metric-caption">Your university team</span>
              <span className="metric-icon"><Building2 size={23} /></span>
            </article>
            <article className="metric-card metric-amber">
              <span className="metric-label">JOINED</span>
              <strong>{employee.dateJoined ? formatDate(employee.dateJoined) : "—"}</strong>
              <span className="metric-caption">Start date on record</span>
              <span className="metric-icon"><CalendarDays size={23} /></span>
            </article>
          </section>

          <section className="profile-grid">
            <article className="panel contact-panel">
              <div className="panel-heading">
                <div><span className="panel-kicker">GET IN TOUCH</span><h3>Contact details</h3></div>
                <div className="panel-heading-icon"><Mail size={19} /></div>
              </div>
              <div className="detail-list">
                <DetailCard icon={Mail} label="Work email" value={employee.workEmail} accent />
                <DetailCard icon={Building2} label="Department" value={employee.department} />
                <DetailCard icon={BriefcaseBusiness} label="Reports to" value={employee.supervisor} />
              </div>
              <div className="panel-note"><LockKeyhole size={14} /> For changes, contact Human Resources.</div>
            </article>

            <article className="panel employment-panel">
              <div className="panel-heading">
                <div><span className="panel-kicker">THE DETAILS</span><h3>Employment</h3></div>
                <div className="panel-heading-icon panel-heading-icon-purple"><BriefcaseBusiness size={19} /></div>
              </div>
              <div className="employment-list">
                <div className="employment-row">
                  <span className="employment-label"><BriefcaseBusiness size={15} /> Job title</span>
                  <strong>{employee.designation || "Not provided"}</strong>
                </div>
                <div className="employment-row">
                  <span className="employment-label"><Building2 size={15} /> Department</span>
                  <strong>{employee.department || "Not provided"}</strong>
                </div>
                <div className="employment-row">
                  <span className="employment-label"><CalendarDays size={15} /> Start date</span>
                  <strong>{formatDate(employee.dateJoined)}</strong>
                </div>
                <div className="employment-row">
                  <span className="employment-label"><Clock3 size={15} /> Employment status</span>
                  <strong className="status-text"><span className="online-dot" />{employee.status || "Not provided"}</strong>
                </div>
              </div>
              <div className="employment-footer">
                <div className="employment-footer-icon"><Check size={15} /></div>
                <span>Employment information is maintained by HR.</span>
              </div>
            </article>
          </section>

          <footer className="page-footer">
            <span>© {new Date().getFullYear()} SCOT University Management System</span>
            <span className="footer-links"><a href="#profile">Privacy</a><span>·</span><a href="#profile">Help &amp; support</a></span>
          </footer>
        </div>
      </main>
    </div>
  );
}

export default function App() {
  const [employee, setEmployee] = useState(null);
  return employee ? (
    <Profile employee={employee} onLogout={() => setEmployee(null)} />
  ) : (
    <Login onLogin={setEmployee} />
  );
}
