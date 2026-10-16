import { useEffect, useMemo, useState } from "react";
import {
  BriefcaseBusiness,
  Building2,
  Mail,
  Phone,
  Search,
  Save,
  UsersRound,
  X,
} from "lucide-react";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";

function initials(name) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("");
}

function safePhotoUrl(value) {
  if (!value) return "";
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : "";
  } catch {
    return "";
  }
}

function StaffAvatar({ staff, large = false }) {
  const name = staff.preferredName || staff.fullName || "Staff member";
  const photoUrl = safePhotoUrl(staff.photoUrl);
  return (
    <span className={`directory-avatar${large ? " directory-avatar-large" : ""}`}>
      <span aria-hidden="true">{initials(name)}</span>
      {photoUrl && <img src={photoUrl} alt="" onError={(event) => event.currentTarget.remove()} />}
    </span>
  );
}

async function loadStaff(token) {
  const response = await fetch(`${apiBase}/api/staff`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const responseText = await response.text();
  let result = {};
  if (responseText) {
    try {
      result = JSON.parse(responseText);
    } catch {
      throw new Error(response.ok ? "The server returned an invalid directory response." : `Staff directory request failed (HTTP ${response.status}).`);
    }
  }
  if (!response.ok) throw new Error(result.message ?? `Staff directory request failed (HTTP ${response.status}).`);
  if (!Array.isArray(result)) throw new Error("The server returned an invalid directory response.");
  return result;
}

async function loadDepartments(token) {
  const response = await fetch(`${apiBase}/api/staff/departments`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const responseText = await response.text();
  let result = {};
  if (responseText) {
    try {
      result = JSON.parse(responseText);
    } catch {
      throw new Error(response.ok ? "The server returned invalid departments." : `Department request failed (HTTP ${response.status}).`);
    }
  }
  if (!response.ok) throw new Error(result.message ?? `Department request failed (HTTP ${response.status}).`);
  if (!Array.isArray(result)) throw new Error("The server returned invalid departments.");
  return result;
}

async function updateStaff(token, id, profile) {
  const response = await fetch(`${apiBase}/api/staff/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(profile),
  });
  const responseText = await response.text();
  let result = {};
  if (responseText) {
    try {
      result = JSON.parse(responseText);
    } catch {
      throw new Error(response.ok ? "The server returned an invalid profile." : `Profile update failed (HTTP ${response.status}).`);
    }
  }
  if (!response.ok) throw new Error(result.message ?? `Profile update failed (HTTP ${response.status}).`);
  return result;
}

function StaffDetails({
  staff,
  canEdit,
  departments,
  colleagues,
  onClose,
  onSave,
}) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [form, setForm] = useState(() => ({
    fullName: staff.fullName ?? "",
    preferredName: staff.preferredName ?? "",
    title: staff.title ?? "",
    designation: staff.designation ?? "",
    departmentId: staff.departmentId ?? "",
    email: staff.email ?? "",
    mobileNumber: staff.mobileNumber ?? "",
    supervisorId: staff.supervisorId ?? "",
    photoUrl: staff.photoUrl ?? "",
  }));

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const name = staff.preferredName || staff.fullName || "Staff member";
  const setField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setSaved(false);
  };
  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSaved(false);
    try {
      const updated = await onSave(staff.id, form);
      setForm({
        fullName: updated.fullName ?? "",
        preferredName: updated.preferredName ?? "",
        title: updated.title ?? "",
        designation: updated.designation ?? "",
        departmentId: updated.departmentId ?? "",
        email: updated.email ?? "",
        mobileNumber: updated.mobileNumber ?? "",
        supervisorId: updated.supervisorId ?? "",
        photoUrl: updated.photoUrl ?? "",
      });
      setEditing(false);
      setSaved(true);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="directory-modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section className={`directory-modal${editing ? " directory-modal-editing" : ""}`} role="dialog" aria-modal="true" aria-labelledby="directory-modal-title">
        <button className="directory-modal-close" onClick={onClose} aria-label="Close staff profile"><X size={19} /></button>
        <div className="directory-modal-header">
          <StaffAvatar staff={editing ? { ...staff, ...form } : staff} large />
          <span className="directory-kicker">{editing ? "CENTRAL STAFF RECORD" : "STAFF PROFILE"}</span>
          <h2 id="directory-modal-title">{editing ? "Edit staff profile" : `${staff.title ? `${staff.title} ` : ""}${staff.fullName || name}`}</h2>
          <p>{editing ? "Changes update the employee record used across the portal." : staff.designation || "University staff"}</p>
        </div>
        {editing ? (
          <form className="directory-edit-form" onSubmit={submit}>
            <div className="directory-edit-grid">
              <label className="directory-edit-field directory-edit-field-wide">Full name<input required value={form.fullName} onChange={setField("fullName")} /></label>
              <label className="directory-edit-field">Preferred name<input value={form.preferredName} onChange={setField("preferredName")} /></label>
              <label className="directory-edit-field">Title<input value={form.title} onChange={setField("title")} placeholder="e.g. Dr" /></label>
              <label className="directory-edit-field directory-edit-field-wide">Position<input required value={form.designation} onChange={setField("designation")} /></label>
              <label className="directory-edit-field">Department<select value={form.departmentId} onChange={setField("departmentId")}><option value="">No department</option>{departments.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
              <label className="directory-edit-field">Supervisor<select value={form.supervisorId} onChange={setField("supervisorId")}><option value="">No supervisor</option>{colleagues.filter((item) => item.id !== staff.id).map((item) => <option key={item.id} value={item.staffId || item.id}>{item.preferredName || item.fullName}</option>)}</select></label>
              <label className="directory-edit-field directory-edit-field-wide">Work email<input type="email" required value={form.email} onChange={setField("email")} /></label>
              <label className="directory-edit-field directory-edit-field-wide">Mobile number<input type="tel" value={form.mobileNumber} onChange={setField("mobileNumber")} /></label>
              <label className="directory-edit-field directory-edit-field-wide">Profile photo URL<input type="url" value={form.photoUrl} onChange={setField("photoUrl")} placeholder="https://…" /></label>
            </div>
            {error && <div className="directory-edit-error" role="alert">{error}</div>}
            {saved && <div className="directory-edit-success" role="status">Profile saved to the central staff record.</div>}
            <p className="directory-modal-note">Work email changes take effect for sign-in. The employee should use their updated email next time they sign in.</p>
            <div className="directory-modal-actions">
              <button className="directory-modal-secondary" type="button" onClick={() => { setEditing(false); setError(""); }}>Cancel</button>
              <button className="directory-modal-save" type="submit" disabled={saving}><Save size={15} />{saving ? "Saving…" : "Save changes"}</button>
            </div>
          </form>
        ) : (
          <>
            <div className="directory-modal-details">
              {staff.email && (
                <a className="directory-detail" href={`mailto:${staff.email}`}>
                  <span className="directory-detail-icon"><Mail size={17} /></span>
                  <span><small>WORK EMAIL</small><strong>{staff.email}</strong></span>
                </a>
              )}
              {staff.mobileNumber && (
                <a className="directory-detail" href={`tel:${staff.mobileNumber}`}>
                  <span className="directory-detail-icon"><Phone size={17} /></span>
                  <span><small>MOBILE</small><strong>{staff.mobileNumber}</strong></span>
                </a>
              )}
              {staff.supervisor && (
                <div className="directory-detail">
                  <span className="directory-detail-icon"><UsersRound size={17} /></span>
                  <span><small>SUPERVISOR</small><strong>{staff.supervisor}</strong></span>
                </div>
              )}
              {staff.department && (
                <div className="directory-detail">
                  <span className="directory-detail-icon"><Building2 size={17} /></span>
                  <span><small>DEPARTMENT</small><strong>{staff.department}</strong></span>
                </div>
              )}
              {staff.designation && (
                <div className="directory-detail">
                  <span className="directory-detail-icon"><BriefcaseBusiness size={17} /></span>
                  <span><small>POSITION</small><strong>{staff.designation}</strong></span>
                </div>
              )}
            </div>
            {saved && <div className="directory-edit-success" role="status">Profile saved to the central staff record.</div>}
            {canEdit ? (
              <div className="directory-modal-actions">
                <p className="directory-modal-note">Contact information is maintained by HR and IT.</p>
                <button className="directory-modal-save" onClick={() => { setEditing(true); setSaved(false); }}><BriefcaseBusiness size={15} />Edit profile</button>
              </div>
            ) : (
              <p className="directory-modal-note">For updates to staff information, please contact Human Resources.</p>
            )}
          </>
        )}
      </section>
    </div>
  );
}

export default function StaffDirectory({ employee }) {
  const [staff, setStaff] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [search, setSearch] = useState("");
  const [department, setDepartment] = useState("");
  const [selectedStaff, setSelectedStaff] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const canEdit = employee.evaluationAdmin === true;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([
      loadStaff(employee.idToken),
      canEdit ? loadDepartments(employee.idToken) : Promise.resolve([]),
    ])
      .then(([result, departmentOptions]) => {
        if (!cancelled) {
          setStaff(result);
          setDepartments(departmentOptions);
        }
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError instanceof TypeError
          ? "Can’t reach the server. Make sure the Spring Boot API is running."
          : requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [employee.idToken, canEdit, reload]);

  async function saveStaff(id, profile) {
    const updated = await updateStaff(employee.idToken, id, profile);
    setStaff((current) => current.map((member) => member.id === updated.id ? updated : member));
    setSelectedStaff(updated);
    return updated;
  }

  const departmentNames = useMemo(
    () => canEdit && departments.length
      ? departments.map((item) => item.name)
      : [...new Set(staff.map((member) => member.department).filter(Boolean))]
        .sort((first, second) => first.localeCompare(second)),
    [staff, departments, canEdit],
  );
  const filteredStaff = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return staff.filter((member) => {
      const matchesDepartment = !department || member.department === department;
      const name = member.preferredName || member.fullName || "";
      const matchesSearch = !query || [
        name,
        member.fullName,
        member.email,
        member.mobileNumber,
        member.designation,
        member.department,
      ].some((value) => value?.toLocaleLowerCase().includes(query));
      return matchesDepartment && matchesSearch;
    });
  }, [staff, search, department]);

  return (
    <div className="page-content directory-page">
      <section className="directory-heading">
        <div>
          <span className="directory-kicker">OUR PEOPLE</span>
          <h1>Staff directory</h1>
          <p>Get to know your colleagues across the university.</p>
        </div>
        <div className="directory-count"><UsersRound size={18} /><span><strong>{loading ? "—" : staff.length}</strong> colleagues</span></div>
      </section>

      <section className="directory-tools" aria-label="Find colleagues">
        <label className="directory-search">
          <Search size={17} />
          <input
            aria-label="Search staff directory"
            placeholder="Search by name, role, email or department"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <label className="directory-department">
          <Building2 size={16} />
          <select aria-label="Filter by department" value={department} onChange={(event) => setDepartment(event.target.value)}>
            <option value="">All departments</option>
            {departmentNames.map((name) => <option key={name} value={name}>{name}</option>)}
          </select>
        </label>
      </section>

      <div className="directory-results-label">
        <span>{loading ? "Loading colleagues…" : `${filteredStaff.length} ${filteredStaff.length === 1 ? "colleague" : "colleagues"}`}</span>
        {department && <button onClick={() => setDepartment("")}>Clear department filter <X size={13} /></button>}
      </div>

      {error ? (
        <section className="directory-state directory-state-error" role="alert">
          <strong>We couldn’t load the staff directory</strong>
          <p>{error}</p>
          <button className="directory-retry" onClick={() => setReload((value) => value + 1)}>Try again</button>
        </section>
      ) : loading ? (
        <div className="directory-grid" aria-label="Loading staff directory">
          {[0, 1, 2, 3, 4, 5].map((item) => <div className="directory-card directory-card-skeleton" key={item} />)}
        </div>
      ) : filteredStaff.length ? (
        <section className="directory-grid" aria-label="Staff members">
          {filteredStaff.map((member) => {
            const name = member.preferredName || member.fullName || "Staff member";
            return (
              <button className="directory-card" key={`${member.email || member.fullName}-${member.department}`} onClick={() => setSelectedStaff(member)}>
                <span className="directory-card-top">
                  <StaffAvatar staff={member} />
                  <span className="directory-card-arrow" aria-hidden="true">↗</span>
                </span>
                <span className="directory-card-name">{member.title ? `${member.title} ` : ""}{member.fullName || name}</span>
                <span className="directory-card-role">{member.designation || "University staff"}</span>
                <span className="directory-card-divider" />
                <span className="directory-card-meta">
                  {member.department && <span><Building2 size={14} />{member.department}</span>}
                  {member.email && <span><Mail size={14} />{member.email}</span>}
                </span>
              </button>
            );
          })}
        </section>
      ) : (
        <section className="directory-state">
          <span className="directory-state-icon"><UsersRound size={22} /></span>
          <strong>{staff.length ? "No colleagues match your search" : "No staff profiles to show yet"}</strong>
          <p>{staff.length ? "Try a different name or clear your department filter." : "Active staff profiles will appear here once they’re available."}</p>
          {(search || department) && <button className="directory-retry" onClick={() => { setSearch(""); setDepartment(""); }}>Clear filters</button>}
        </section>
      )}

      <footer className="page-footer directory-footer">
        <span>© {new Date().getFullYear()} SCOT University Management System</span>
        <span>Staff details are maintained by Human Resources.</span>
      </footer>
      {selectedStaff && (
        <StaffDetails
          staff={selectedStaff}
          canEdit={canEdit}
          departments={departments}
          colleagues={staff}
          onClose={() => setSelectedStaff(null)}
          onSave={saveStaff}
        />
      )}
    </div>
  );
}
