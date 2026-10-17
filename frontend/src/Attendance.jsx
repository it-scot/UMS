import { useEffect, useState } from "react";
import { CalendarDays, Clock3, MapPin, RefreshCw } from "lucide-react";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";
const monthNames = Array.from({ length: 12 }, (_, month) =>
  new Intl.DateTimeFormat("en", { month: "long" }).format(new Date(2026, month, 1)),
);
const weekdays = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value, includeDate = true) {
  if (!value) return "Time not recorded";
  const date = parseDate(value);
  if (!date) return value;
  return new Intl.DateTimeFormat("en", {
    ...(includeDate ? { dateStyle: "medium" } : { weekday: "short" }),
    timeStyle: "short",
  }).format(date);
}

function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function clockMinutes(value) {
  const match = String(value ?? "").trim().match(/^(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?$/i);
  if (!match) return null;
  let hours = Number(match[1]);
  const minutes = Number(match[2]);
  const meridiem = match[3]?.toUpperCase();
  if (minutes > 59 || hours > (meridiem ? 12 : 23)) return null;
  if (meridiem) hours = (hours % 12) + (meridiem === "PM" ? 12 : 0);
  return hours * 60 + minutes;
}

function firstEventOfDay(records) {
  return records
    .map((record) => ({ record, date: parseDate(record.eventTime) }))
    .filter((item) => item.date)
    .sort((first, second) => first.date - second.date)[0]?.record;
}

function buildArrivalTrend(records, schedules, year, month) {
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const recordsByDate = new Map();
  for (const record of records) {
    const date = parseDate(record.eventTime);
    if (!date || date.getFullYear() !== year || date.getMonth() !== month) continue;
    const key = dateKey(date);
    recordsByDate.set(key, [...(recordsByDate.get(key) ?? []), record]);
  }

  const days = Array.from({ length: daysInMonth }, (_, index) => {
    const date = new Date(year, month, index + 1);
    const firstEvent = firstEventOfDay(recordsByDate.get(dateKey(date)) ?? []);
    const schedule = schedules.find((item) =>
      String(item.day ?? "").trim().toLowerCase().slice(0, 3)
        === weekdays[date.getDay()].slice(0, 3).toLowerCase(),
    );
    const eventDate = parseDate(firstEvent?.eventTime);
    const start = clockMinutes(schedule?.startTime);
    if (!eventDate || start === null) {
      return { day: index + 1, late: false, early: false, classified: false };
    }
    const arrival = eventDate.getHours() * 60 + eventDate.getMinutes();
    return {
      day: index + 1,
      late: arrival > start,
      early: arrival < start,
      classified: true,
    };
  });
  return {
    days,
    late: days.filter((day) => day.late).length,
    early: days.filter((day) => day.early).length,
    classified: days.filter((day) => day.classified).length,
  };
}

async function loadAttendance(token) {
  const response = await fetch(`${apiBase}/api/attendance/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const text = await response.text();
  let result = {};
  if (text) {
    try {
      result = JSON.parse(text);
    } catch {
      throw new Error(response.ok ? "The server returned an invalid attendance response." : `Attendance request failed (HTTP ${response.status}).`);
    }
  }
  if (!response.ok) throw new Error(result.message ?? `Attendance request failed (HTTP ${response.status}).`);
  if (typeof result.configured !== "boolean" || !Array.isArray(result.records) || !Array.isArray(result.schedules)) {
    throw new Error("The server returned an invalid attendance response.");
  }
  return result;
}

export default function Attendance({ employee }) {
  const [attendance, setAttendance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [selectedMonth, setSelectedMonth] = useState(() => new Date().getMonth());
  const [selectedYear, setSelectedYear] = useState(() => new Date().getFullYear());

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    loadAttendance(employee.idToken)
      .then((result) => { if (!cancelled) setAttendance(result); })
      .catch((requestError) => {
        if (!cancelled) setError(requestError instanceof TypeError
          ? "Can’t reach the server. Make sure the Spring Boot API is running."
          : requestError.message);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [employee.idToken, reload]);

  const records = attendance?.records ?? [];
  const schedules = attendance?.schedules ?? [];
  const todayKey = dateKey(new Date());
  const todayRecords = records
    .filter((record) => {
      const date = parseDate(record.eventTime);
      return date && dateKey(date) === todayKey;
    })
    .sort((first, second) => parseDate(second.eventTime) - parseDate(first.eventTime));
  const availableYears = [...new Set([
    new Date().getFullYear(),
    ...records.map((record) => parseDate(record.eventTime)?.getFullYear()).filter(Boolean),
  ])].sort((first, second) => second - first);
  const historyRecords = records
    .filter((record) => {
      const date = parseDate(record.eventTime);
      return date && date.getFullYear() === selectedYear && date.getMonth() === selectedMonth;
    })
    .sort((first, second) => parseDate(second.eventTime) - parseDate(first.eventTime));
  const trend = buildArrivalTrend(records, schedules, selectedYear, selectedMonth);
  const maxTrendCount = Math.max(1, ...trend.days.map((day) => Math.max(day.late, day.early) ? 1 : 0));

  function renderEvents(items, includeDate) {
    return (
      <div className="attendance-table-wrap">
        <table className="attendance-table">
          <thead><tr><th>{includeDate ? "Date and time" : "Time"}</th><th>Event</th><th>Location</th><th>Reader</th><th>Method</th></tr></thead>
          <tbody>
            {items.map((record) => (
              <tr key={record.id}>
                <td className="attendance-time"><Clock3 size={14} />{formatDateTime(record.eventTime, includeDate)}</td>
                <td><span className="attendance-event">{record.eventName || record.readerState || "Access event"}</span></td>
                <td><span className="attendance-location"><MapPin size={13} />{record.areaName || "Not recorded"}</span></td>
                <td>{record.readerName || "Not recorded"}</td>
                <td>{record.verificationMode || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <div className="page-content attendance-page">
      <section className="welcome-row">
        <div>
          <span className="page-kicker">MY WORK</span>
          <h1>Attendance</h1>
          <p>{employee.preferredName || employee.fullName || "Your"} attendance and arrival history.</p>
        </div>
        <button className="outline-button" onClick={() => setReload((value) => value + 1)} disabled={loading}>
          <RefreshCw size={15} /> Refresh
        </button>
      </section>

      {error && (
        <section className="attendance-state attendance-state-error" role="alert">
          <strong>Attendance couldn’t be loaded</strong>
          <p>{error}</p>
          <button className="directory-retry" onClick={() => setReload((value) => value + 1)}>Try again</button>
        </section>
      )}

      {!error && <section className="attendance-panel attendance-today-panel" aria-labelledby="attendance-today-title">
        <div className="attendance-panel-heading">
          <div><span className="panel-kicker">TODAY</span><h2 id="attendance-today-title">Today’s attendance</h2></div>
          <span className="attendance-count">{todayRecords.length} {todayRecords.length === 1 ? "event" : "events"}</span>
        </div>
        {loading ? (
          <div className="attendance-state" role="status">Loading today’s records…</div>
        ) : !error && !attendance?.configured ? (
          <div className="attendance-state attendance-state-unlinked">
            <span className="attendance-state-icon"><CalendarDays size={21} /></span>
            <div><strong>No employee number is linked to your staff profile</strong><p>Ask Human Resources to verify the staffID or empID on your employee record.</p></div>
          </div>
        ) : todayRecords.length === 0 ? (
          <div className="attendance-state"><span className="attendance-state-icon"><CalendarDays size={21} /></span><div><strong>No events recorded today</strong><p>Your earlier attendance events remain available in history.</p></div></div>
        ) : (
          <>
            <div className="attendance-today-summary">
              <div><small>FIRST RECORDED</small><strong>{formatDateTime(firstEventOfDay(todayRecords)?.eventTime, false)}</strong></div>
              <div><small>LAST RECORDED</small><strong>{formatDateTime(todayRecords[0]?.eventTime, false)}</strong></div>
            </div>
            {renderEvents(todayRecords, false)}
          </>
        )}
      </section>}

      {!error && <section className="attendance-history-section" aria-label="Attendance history">
        <div className="attendance-history-heading">
          <div><span className="panel-kicker">HISTORY</span><h2>Attendance history</h2></div>
          <div className="attendance-filters">
            <label><span>Month</span><select value={selectedMonth} onChange={(event) => setSelectedMonth(Number(event.target.value))}>
              {monthNames.map((name, index) => <option key={name} value={index}>{name}</option>)}
            </select></label>
            <label><span>Year</span><select value={selectedYear} onChange={(event) => setSelectedYear(Number(event.target.value))}>
              {availableYears.map((year) => <option key={year} value={year}>{year}</option>)}
            </select></label>
          </div>
        </div>
        <section className="attendance-panel attendance-history-panel">
          <div className="attendance-panel-heading">
            <div><span className="panel-kicker">{monthNames[selectedMonth].toUpperCase()} {selectedYear}</span><h2>All recorded events</h2></div>
            <span className="attendance-count">{historyRecords.length} {historyRecords.length === 1 ? "event" : "events"}</span>
          </div>
          {loading ? (
            <div className="attendance-state" role="status">Loading attendance history…</div>
          ) : historyRecords.length ? renderEvents(historyRecords, true) : (
            <div className="attendance-state"><span className="attendance-state-icon"><CalendarDays size={21} /></span><div><strong>No events for this month</strong><p>Choose another month or year to view earlier records.</p></div></div>
          )}
        </section>
      </section>}

      {!error && <section className="attendance-panel attendance-trend-panel" aria-labelledby="arrival-trend-title">
        <div className="attendance-panel-heading">
          <div><span className="panel-kicker">ARRIVAL PATTERN</span><h2 id="arrival-trend-title">Early and late arrivals</h2></div>
          <span className="attendance-count">{monthNames[selectedMonth]} {selectedYear}</span>
        </div>
        {loading ? (
          <div className="attendance-state" role="status">Calculating arrival pattern…</div>
        ) : !schedules.length ? (
          <div className="attendance-state"><span className="attendance-state-icon"><Clock3 size={20} /></span><div><strong>No work schedule is available</strong><p>Arrival timing is compared with your scheduled start time.</p></div></div>
        ) : !trend.classified ? (
          <div className="attendance-state"><span className="attendance-state-icon"><Clock3 size={20} /></span><div><strong>No scheduled arrival days found</strong><p>There are no recorded events on days with a matching work schedule this month.</p></div></div>
        ) : (
          <>
            <div className="arrival-trend-summary">
              <div><span className="arrival-legend-dot arrival-dot-late" /><span>Late</span><strong>{trend.late}</strong></div>
              <div><span className="arrival-legend-dot arrival-dot-early" /><span>Early</span><strong>{trend.early}</strong></div>
              <small>{trend.classified} scheduled days compared</small>
            </div>
            <div className="arrival-chart-scroll">
              <div className="arrival-chart" role="img" aria-label={`Daily early and late first arrivals for ${monthNames[selectedMonth]} ${selectedYear}`} style={{ "--chart-days": trend.days.length }}>
                {trend.days.map((day) => (
                  <div className={`arrival-chart-day${day.classified ? " arrival-chart-day-classified" : ""}`} key={day.day} title={`Day ${day.day}: ${day.late ? "late" : day.early ? "early" : day.classified ? "on time" : "no scheduled arrival"}`}>
                    <div className="arrival-chart-bars">
                      <span className="arrival-chart-bar arrival-bar-late" style={{ height: day.late ? `${Math.max(9, 80 / maxTrendCount)}%` : "0%" }} />
                      <span className="arrival-chart-bar arrival-bar-early" style={{ height: day.early ? `${Math.max(9, 80 / maxTrendCount)}%` : "0%" }} />
                    </div>
                    <small>{day.day}</small>
                  </div>
                ))}
              </div>
            </div>
            <p className="arrival-chart-note">Each day uses the first recorded access event and compares it with that weekday’s scheduled start.</p>
          </>
        )}
      </section>}
      <footer className="page-footer attendance-footer">
        <span>© {new Date().getFullYear()} SCOT University Management System</span>
        <span>Attendance events are provided by the access-control system.</span>
      </footer>
    </div>
  );
}