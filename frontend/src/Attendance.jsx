import { useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  BarChart3,
  Briefcase,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock3,
  Download,
  Info,
  LockKeyhole,
  LogIn,
  LogOut,
  MapPin,
  RefreshCw,
  Timer,
  TrendingUp,
  UserCheck,
} from "lucide-react";

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

function formatTime(value) {
  if (!value) return "—";
  const date = parseDate(value);
  if (!date) return String(value);
  return new Intl.DateTimeFormat("en", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
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

function formatDuration(totalMinutes) {
  if (totalMinutes === null || totalMinutes === undefined || totalMinutes <= 0) return "0h 00m";
  const hours = Math.floor(totalMinutes / 60);
  const minutes = Math.round(totalMinutes % 60);
  if (hours === 0) return `${minutes}m`;
  return `${hours}h ${String(minutes).padStart(2, "0")}m`;
}

function getScheduleForDate(date, schedules) {
  const weekdayName = weekdays[date.getDay()];
  if (Array.isArray(schedules) && schedules.length > 0) {
    const custom = schedules.find((item) =>
      String(item.day ?? "").trim().toLowerCase().slice(0, 3) === weekdayName.slice(0, 3).toLowerCase()
    );
    return custom || null;
  }
  // Default Mon-Fri 08:30 - 17:30 if schedules unconfigured
  if (date.getDay() >= 1 && date.getDay() <= 5) {
    return { day: weekdayName, startTime: "08:30", endTime: "17:30" };
  }
  return null;
}

function firstEventOfDay(records) {
  return records
    .map((record) => ({ record, date: parseDate(record.eventTime) }))
    .filter((item) => item.date)
    .sort((first, second) => first.date - second.date)[0]?.record;
}

function lastEventOfDay(records) {
  return records
    .map((record) => ({ record, date: parseDate(record.eventTime) }))
    .filter((item) => item.date)
    .sort((first, second) => second.date - first.date)[0]?.record;
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

async function sendPunch(token, type) {
  const response = await fetch(`${apiBase}/api/attendance/punch`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ type }),
  });
  const text = await response.text();
  let result = {};
  if (text) {
    try { result = JSON.parse(text); } catch { /* ignore */ }
  }
  if (!response.ok) throw new Error(result.message ?? "Punch recording failed.");
  return result;
}

export default function Attendance({ employee }) {
  const [attendance, setAttendance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [punching, setPunching] = useState(false);
  const [punchNotice, setPunchNotice] = useState("");
  const [expandedDate, setExpandedDate] = useState(null);
  const [statusFilter, setStatusFilter] = useState("all");

  const now = new Date();
  const [selectedMonth, setSelectedMonth] = useState(() => now.getMonth());
  const [selectedYear, setSelectedYear] = useState(() => now.getFullYear());
  const [graphTab, setGraphTab] = useState("hours"); // "hours" | "variance"
  const [selectedGraphDay, setSelectedGraphDay] = useState(null);

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

  async function handlePunch(type) {
    setPunching(true);
    setPunchNotice("");
    try {
      await sendPunch(employee.idToken, type);
      setPunchNotice(type === "in" ? "✓ Clock-in recorded successfully!" : "✓ Clock-out recorded successfully!");
      setReload((v) => v + 1);
      setTimeout(() => setPunchNotice(""), 5000);
    } catch (err) {
      setError(err.message);
    } finally {
      setPunching(false);
    }
  }

  const records = attendance?.records ?? [];
  const schedules = attendance?.schedules ?? [];

  // Today calculations
  const todayDate = new Date();
  const todayKey = dateKey(todayDate);
  const todaySchedule = getScheduleForDate(todayDate, schedules);

  const todayRecords = useMemo(() => {
    return records
      .filter((record) => {
        const date = parseDate(record.eventTime);
        return date && dateKey(date) === todayKey;
      })
      .sort((a, b) => parseDate(a.eventTime) - parseDate(b.eventTime));
  }, [records, todayKey]);

  const todayArrivalEvent = todayRecords[0] ?? null;
  const todayLeaveEvent = todayRecords.length > 1 ? todayRecords[todayRecords.length - 1] : null;

  // Working state today
  const hasArrivedToday = Boolean(todayArrivalEvent);
  const hasLeftToday = Boolean(todayLeaveEvent);

  const arrivalDate = parseDate(todayArrivalEvent?.eventTime);
  const leaveDate = parseDate(todayLeaveEvent?.eventTime);

  // Today's total working minutes
  let todayWorkingMinutes = 0;
  if (arrivalDate) {
    if (leaveDate) {
      todayWorkingMinutes = Math.max(0, (leaveDate.getTime() - arrivalDate.getTime()) / (1000 * 60));
    } else {
      // Still in office - active duration from arrival until now
      todayWorkingMinutes = Math.max(0, (Date.now() - arrivalDate.getTime()) / (1000 * 60));
    }
  }

  // Today Arrival vs Shift Start comparison
  let todayArrivalStatusText = "On Schedule";
  let todayArrivalBadgeClass = "badge-ontime";
  if (todayArrivalEvent && todaySchedule?.startTime) {
    const arrMinutes = arrivalDate.getHours() * 60 + arrivalDate.getMinutes();
    const startMinutes = clockMinutes(todaySchedule.startTime);
    if (startMinutes !== null) {
      const diff = arrMinutes - startMinutes;
      if (diff > 5) {
        todayArrivalStatusText = `Late by ${diff}m (Shift: ${todaySchedule.startTime})`;
        todayArrivalBadgeClass = "badge-late";
      } else if (diff < -5) {
        todayArrivalStatusText = `Early by ${Math.abs(diff)}m (Shift: ${todaySchedule.startTime})`;
        todayArrivalBadgeClass = "badge-early";
      } else {
        todayArrivalStatusText = `On Time (Shift: ${todaySchedule.startTime})`;
        todayArrivalBadgeClass = "badge-ontime";
      }
    }
  }

  // Today Leave vs Shift End comparison
  let todayLeaveStatusText = "Shift in progress";
  let todayLeaveBadgeClass = "badge-active";
  if (todayLeaveEvent && todaySchedule?.endTime) {
    const lveMinutes = leaveDate.getHours() * 60 + leaveDate.getMinutes();
    const endMinutes = clockMinutes(todaySchedule.endTime);
    if (endMinutes !== null) {
      const diff = lveMinutes - endMinutes;
      if (diff > 10) {
        todayLeaveStatusText = `Overtime +${diff}m (Shift end: ${todaySchedule.endTime})`;
        todayLeaveBadgeClass = "badge-ot";
      } else if (diff < -10) {
        todayLeaveStatusText = `Left ${Math.abs(diff)}m early (Shift end: ${todaySchedule.endTime})`;
        todayLeaveBadgeClass = "badge-early-leave";
      } else {
        todayLeaveStatusText = `On Time (Shift end: ${todaySchedule.endTime})`;
        todayLeaveBadgeClass = "badge-ontime";
      }
    }
  }

  // Available Years
  const availableYears = useMemo(() => {
    const years = new Set([
      new Date().getFullYear(),
      ...records.map((r) => parseDate(r.eventTime)?.getFullYear()).filter(Boolean),
    ]);
    return [...years].sort((a, b) => b - a);
  }, [records]);

  // Daily records for selected month & year
  const monthlyDailyData = useMemo(() => {
    const daysInMonth = new Date(selectedYear, selectedMonth + 1, 0).getDate();
    const recordsByDay = new Map();

    for (const r of records) {
      const d = parseDate(r.eventTime);
      if (!d || d.getFullYear() !== selectedYear || d.getMonth() !== selectedMonth) continue;
      const k = d.getDate();
      recordsByDay.set(k, [...(recordsByDay.get(k) ?? []), r]);
    }

    const list = [];
    for (let dayNum = 1; dayNum <= daysInMonth; dayNum += 1) {
      const dayDate = new Date(selectedYear, selectedMonth, dayNum);
      const dayRecords = (recordsByDay.get(dayNum) ?? []).sort(
        (a, b) => parseDate(a.eventTime) - parseDate(b.eventTime)
      );

      const schedule = getScheduleForDate(dayDate, schedules);
      const isWeekend = dayDate.getDay() === 0 || dayDate.getDay() === 6;
      const isFuture = dayDate > now;

      const firstRec = dayRecords[0] ?? null;
      const lastRec = dayRecords.length > 1 ? dayRecords[dayRecords.length - 1] : null;

      const firstDate = parseDate(firstRec?.eventTime);
      const lastDate = parseDate(lastRec?.eventTime);

      let workingMins = 0;
      if (firstDate && lastDate) {
        workingMins = Math.max(0, (lastDate.getTime() - firstDate.getTime()) / (1000 * 60));
      } else if (firstDate && dateKey(dayDate) === todayKey) {
        workingMins = Math.max(0, (Date.now() - firstDate.getTime()) / (1000 * 60));
      }

      // Arrival analysis
      let arrivalStatus = "—";
      let arrivalDiff = null;
      if (firstDate && schedule?.startTime) {
        const arrM = firstDate.getHours() * 60 + firstDate.getMinutes();
        const schedM = clockMinutes(schedule.startTime);
        if (schedM !== null) {
          arrivalDiff = arrM - schedM;
          if (arrivalDiff > 5) arrivalStatus = `+${arrivalDiff}m Late`;
          else if (arrivalDiff < -5) arrivalStatus = `${Math.abs(arrivalDiff)}m Early`;
          else arrivalStatus = "On Time";
        }
      }

      // Leaving analysis
      let leaveStatus = "—";
      let leaveDiff = null;
      if (lastDate && schedule?.endTime) {
        const lveM = lastDate.getHours() * 60 + lastDate.getMinutes();
        const schedEndM = clockMinutes(schedule.endTime);
        if (schedEndM !== null) {
          leaveDiff = lveM - schedEndM;
          if (leaveDiff > 10) leaveStatus = `+${leaveDiff}m OT`;
          else if (leaveDiff < -10) leaveStatus = `-${Math.abs(leaveDiff)}m Early`;
          else leaveStatus = "On Time";
        }
      } else if (firstDate && dateKey(dayDate) === todayKey && !lastDate) {
        leaveStatus = "In Office";
      }

      // Overall status classification
      let statusTag = "No Punch";
      let statusClass = "tag-nopunch";
      if (!schedule && !dayRecords.length) {
        statusTag = isWeekend ? "Weekend" : "Off Duty";
        statusClass = "tag-weekend";
      } else if (isFuture) {
        statusTag = "Upcoming";
        statusClass = "tag-future";
      } else if (dayRecords.length > 0) {
        if (arrivalDiff !== null && arrivalDiff > 10) {
          statusTag = "Late";
          statusClass = "tag-late";
        } else if (leaveDiff !== null && leaveDiff < -30) {
          statusTag = "Early Leave";
          statusClass = "tag-earlyleave";
        } else {
          statusTag = "Present";
          statusClass = "tag-present";
        }
      } else if (schedule) {
        statusTag = "Absent";
        statusClass = "tag-absent";
      }

      list.push({
        dayNum,
        date: dayDate,
        dateFormatted: dayDate.toLocaleDateString("en-US", {
          weekday: "short",
          month: "short",
          day: "numeric",
        }),
        schedule,
        scheduleFormatted: schedule ? `${schedule.startTime || "08:30"} – ${schedule.endTime || "17:30"}` : "Off Duty",
        records: dayRecords,
        firstRec,
        lastRec,
        arrivalFormatted: formatTime(firstRec?.eventTime),
        leaveFormatted: lastRec ? formatTime(lastRec.eventTime) : firstRec && dateKey(dayDate) === todayKey ? "In Office" : "—",
        workingMins,
        workingFormatted: formatDuration(workingMins),
        arrivalStatus,
        arrivalDiff,
        leaveStatus,
        leaveDiff,
        statusTag,
        statusClass,
        isToday: dateKey(dayDate) === todayKey,
      });
    }

    return list;
  }, [records, schedules, selectedYear, selectedMonth, todayKey, now]);

  // Filtered daily list
  const filteredDays = useMemo(() => {
    if (statusFilter === "present") {
      return monthlyDailyData.filter((d) => d.records.length > 0);
    }
    if (statusFilter === "late") {
      return monthlyDailyData.filter((d) => d.statusTag === "Late");
    }
    if (statusFilter === "ontime") {
      return monthlyDailyData.filter((d) => d.statusTag === "Present" && (d.arrivalDiff === null || d.arrivalDiff <= 5));
    }
    return monthlyDailyData;
  }, [monthlyDailyData, statusFilter]);

  // Monthly summary stats
  const monthStats = useMemo(() => {
    const presentDays = monthlyDailyData.filter((d) => d.records.length > 0);
    const lateDays = monthlyDailyData.filter((d) => d.statusTag === "Late");
    const totalMinutes = presentDays.reduce((acc, d) => acc + d.workingMins, 0);
    const avgMinutes = presentDays.length ? Math.round(totalMinutes / presentDays.length) : 0;
    const scheduledDays = monthlyDailyData.filter(
      (d) => d.schedule && d.date <= now && d.date.getDay() !== 0 && d.date.getDay() !== 6
    ).length;
    const targetDailyMinutes = 510; // 8h 30m standard shift target
    const scheduledTargetMinutes = scheduledDays * targetDailyMinutes;
    const netBalanceMinutes = totalMinutes - (presentDays.length * targetDailyMinutes);
    const shiftCompliancePercent = scheduledTargetMinutes > 0
      ? Math.min(100, Math.round((totalMinutes / scheduledTargetMinutes) * 100))
      : 100;
    const punctualityRate = presentDays.length
      ? Math.round(((presentDays.length - lateDays.length) / presentDays.length) * 100)
      : 100;

    return {
      presentCount: presentDays.length,
      scheduledCount: scheduledDays,
      lateCount: lateDays.length,
      totalMinutes,
      totalHoursFormatted: formatDuration(totalMinutes),
      avgDailyHoursFormatted: formatDuration(avgMinutes),
      scheduledTargetMinutes,
      netBalanceMinutes,
      shiftCompliancePercent,
      punctualityRate,
    };
  }, [monthlyDailyData, now]);

  // Export to Excel / CSV
  function handleExportExcel() {
    const employeeName = employee.preferredName || employee.fullName || "Employee";
    const cleanName = employeeName.replace(/[^A-Za-z0-9_-]/g, "_");
    const monthName = monthNames[selectedMonth];
    const filename = `SCOT_Attendance_${cleanName}_${monthName}_${selectedYear}.csv`;

    const headers = [
      "Date",
      "Day",
      "Allocated Shift",
      "Arrival Time (In)",
      "Arrival Status",
      "Leave Time (Out)",
      "Leave Status",
      "Hours Worked",
      "Status",
      "Punches Count",
    ];

    const rows = monthlyDailyData.map((d) => [
      `"${d.date.toISOString().slice(0, 10)}"`,
      `"${weekdays[d.date.getDay()]}"`,
      `"${d.scheduleFormatted}"`,
      `"${d.arrivalFormatted}"`,
      `"${d.arrivalStatus}"`,
      `"${d.leaveFormatted}"`,
      `"${d.leaveStatus}"`,
      `"${d.workingFormatted}"`,
      `"${d.statusTag}"`,
      d.records.length,
    ]);

    const csvContent = "\uFEFF" + [headers.join(","), ...rows.map((r) => r.join(","))].join("\r\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  const staffDisplayName = employee.preferredName || employee.fullName || "Staff Member";

  return (
    <div className="page-content attendance-page">
      {/* 1. TOP HEADER (matches Profile & Directory page UI) */}
      <section className="welcome-row">
        <div>
          <span className="directory-kicker">STAFF PORTAL</span>
          <h1>My Attendance &amp; Shifts</h1>
          <p>Track your daily clock-in/out records, allocated shift timings, and monthly working hours.</p>
        </div>
        <div className="welcome-row-actions">
          <button
            type="button"
            className="outline-button"
            onClick={handleExportExcel}
            title="Download this month's attendance records in Microsoft Excel format"
          >
            <Download size={14} /> Export into Excel
          </button>
          <button
            type="button"
            className="outline-button"
            onClick={() => setReload((v) => v + 1)}
            disabled={loading}
            title="Refresh attendance records"
          >
            <RefreshCw size={14} className={loading ? "spin" : ""} /> Refresh
          </button>
        </div>
      </section>

      {/* 2. FOUR METRIC STATS CARDS (exact Profile page .profile-metrics theme) */}
      <section className="profile-metrics" aria-label="Today's attendance summary">
        <article className="metric-card metric-slate">
          <span className="metric-label">TODAY'S ARRIVAL TIME</span>
          <strong>{hasArrivedToday ? formatTime(todayArrivalEvent?.eventTime) : "—"}</strong>
          <span className="metric-caption">
            {hasArrivedToday ? todayArrivalStatusText : "Not checked in yet today"}
          </span>
          <span className="metric-icon"><LogIn size={23} /></span>
        </article>

        <article className="metric-card metric-red">
          <span className="metric-label">LEAVE TIME (TODAY)</span>
          <strong>{hasLeftToday ? formatTime(todayLeaveEvent?.eventTime) : hasArrivedToday ? "In Office" : "—"}</strong>
          <span className="metric-caption">
            {hasLeftToday ? todayLeaveStatusText : hasArrivedToday ? "Currently in office" : "No departure recorded"}
          </span>
          <span className="metric-icon"><LogOut size={23} /></span>
        </article>

        <article className="metric-card metric-green">
          <span className="metric-label">TOTAL WORKING HOURS</span>
          <strong>{formatDuration(todayWorkingMinutes)}</strong>
          <span className="metric-caption">
            Target: 8h 30m ({Math.min(100, Math.round((todayWorkingMinutes / 510) * 100))}% completed)
          </span>
          <span className="metric-icon"><Timer size={23} /></span>
        </article>

        <article className="metric-card metric-amber">
          <span className="metric-label">ALLOCATED SHIFT</span>
          <strong>{todaySchedule ? `${todaySchedule.startTime || "08:30"} – ${todaySchedule.endTime || "17:30"}` : "Off Duty"}</strong>
          <span className="metric-caption">
            {employee.workingDays || "Monday to Friday"}
          </span>
          <span className="metric-icon"><Briefcase size={23} /></span>
        </article>
      </section>

      {/* 3. PROFILE-GRID (2-Column Layout matching My Profile: Shift Hub & Monthly Summary) */}
      <section className="profile-grid">
        {/* Left Column: Shift Allocation & Punch Hub Panel */}
        <article className="panel attendance-shift-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">DAILY SHIFT ALLOCATION</span>
              <h3>Shift &amp; Clock-In Hub</h3>
            </div>
            <div className="panel-heading-icon">
              <Clock3 size={17} />
            </div>
          </div>

          <div className="detail-list">
            <div className="detail-card detail-card-accent">
              <div className="detail-icon">
                <Briefcase size={16} />
              </div>
              <div className="detail-copy">
                <span className="detail-label">Allocated Shift Time</span>
                <strong className="detail-value">
                  {todaySchedule ? `${todaySchedule.startTime || "08:30"} – ${todaySchedule.endTime || "17:30"}` : "Non-Working Day / Off Duty"} · {employee.workingDays || "Monday to Friday"}
                </strong>
              </div>
            </div>

            <div className="detail-card">
              <div className="detail-icon">
                <UserCheck size={16} />
              </div>
              <div className="detail-copy">
                <span className="detail-label">Current Punch Status</span>
                <strong className="detail-value">
                  {hasArrivedToday && !hasLeftToday ? (
                    <span className="status-clocked-in"><span className="online-dot" /> Clocked In · Currently in office</span>
                  ) : hasArrivedToday && hasLeftToday ? (
                    <span className="status-completed"><Check size={13} /> Shift Completed</span>
                  ) : (
                    <span className="status-unclocked"><Clock3 size={13} /> Not Clocked In Today</span>
                  )}
                </strong>
              </div>
            </div>
          </div>

          <div className="attendance-punch-actions-row">
            <button
              type="button"
              className="btn-punch btn-punch-in"
              onClick={() => handlePunch("in")}
              disabled={punching}
              title="Record an Arrival punch today"
            >
              <LogIn size={14} /> {punching ? "Punching…" : "Clock In"}
            </button>
            <button
              type="button"
              className="btn-punch btn-punch-out"
              onClick={() => handlePunch("out")}
              disabled={punching || !hasArrivedToday}
              title="Record a Leaving punch today"
            >
              <LogOut size={14} /> {punching ? "Punching…" : "Clock Out"}
            </button>
          </div>

          {punchNotice && (
            <div className="attendance-alert-success" role="status">
              {punchNotice}
            </div>
          )}

          <div className="panel-note">
            <LockKeyhole size={14} /> Synced with campus biometric access control terminals.
          </div>
        </article>

        {/* Right Column: Monthly Shift Compliance & Overview */}
        <article className="panel attendance-summary-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">MONTHLY SHIFT SUMMARY</span>
              <h3>{monthNames[selectedMonth]} {selectedYear} Audit</h3>
            </div>
            <div className="panel-heading-icon panel-heading-icon-purple">
              <TrendingUp size={17} />
            </div>
          </div>

          <div className="employment-list">
            <div className="employment-row">
              <span className="employment-label"><UserCheck size={14} /> Days Present</span>
              <strong>{monthStats.presentCount} / {monthStats.scheduledCount} Days</strong>
            </div>
            <div className="employment-row">
              <span className="employment-label"><Timer size={14} /> On-Time Rate</span>
              <strong className="status-text">
                <span className="online-dot" />
                {monthStats.scheduledCount ? Math.round(((monthStats.presentCount - monthStats.lateCount) / Math.max(1, monthStats.presentCount)) * 100) : 100}%
              </strong>
            </div>
            <div className="employment-row">
              <span className="employment-label"><AlertCircle size={14} /> Late Arrivals</span>
              <strong style={{ color: monthStats.lateCount > 0 ? "#df384b" : "#08aa75" }}>
                {monthStats.lateCount} Days
              </strong>
            </div>
            <div className="employment-row">
              <span className="employment-label"><Clock3 size={14} /> Total Working Hours</span>
              <strong>{monthStats.totalHoursFormatted}</strong>
            </div>
            <div className="employment-row">
              <span className="employment-label"><TrendingUp size={14} /> Average Daily Hours</span>
              <strong>{monthStats.avgDailyHoursFormatted}</strong>
            </div>
          </div>

          <div className="employment-footer">
            <span className="employment-footer-icon"><Check size={12} /></span>
            <span>Calculated against allocated shift timings.</span>
          </div>
        </article>
      </section>

      {error && (
        <section className="attendance-state attendance-state-error" role="alert">
          <AlertCircle size={18} />
          <div>
            <strong>Attendance couldn’t be loaded</strong>
            <p>{error}</p>
            <button className="directory-retry" onClick={() => setReload((value) => value + 1)}>Try again</button>
          </div>
        </section>
      )}

      {/* 4. SHIFT & ATTENDANCE GRAPH VISUALIZATION PANEL */}
      {!error && (
        <article className="panel attendance-graph-panel" aria-label="Shift allocation and attendance graph analytics">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">HOURS &amp; SHIFT COMPLIANCE</span>
              <h3>Shift Performance &amp; Attendance Analytics</h3>
            </div>
            <div className="graph-heading-actions">
              <div className="graph-tab-pills">
                <button
                  type="button"
                  className={`graph-tab-pill ${graphTab === "hours" ? "active" : ""}`}
                  onClick={() => setGraphTab("hours")}
                >
                  <BarChart3 size={13} />
                  <span>Daily Hours vs Shift Target</span>
                </button>
                <button
                  type="button"
                  className={`graph-tab-pill ${graphTab === "variance" ? "active" : ""}`}
                  onClick={() => setGraphTab("variance")}
                >
                  <TrendingUp size={13} />
                  <span>Shift Punctuality Variance</span>
                </button>
              </div>
              <div className="panel-heading-icon">
                <BarChart3 size={17} />
              </div>
            </div>
          </div>

          {/* Quick Analytics Summary Strip */}
          <div className="graph-analytics-strip">
            <div className="analytics-chip">
              <span className="chip-label">ALLOCATED SHIFT TARGET</span>
              <strong>8h 30m / day</strong>
              <small>{employee.shiftTime || "8:30 to 5:30"} · {employee.workingDays || "Monday to Friday"}</small>
            </div>
            <div className="analytics-chip">
              <span className="chip-label">MONTHLY SHIFT COMPLIANCE</span>
              <strong className="stat-green">{monthStats.shiftCompliancePercent}%</strong>
              <small>{monthStats.totalHoursFormatted} worked / {(monthStats.scheduledCount * 8.5).toFixed(0)}h scheduled</small>
            </div>
            <div className="analytics-chip">
              <span className="chip-label">PUNCTUALITY INDEX</span>
              <strong className="stat-green">{monthStats.punctualityRate}%</strong>
              <small>{monthStats.presentCount - monthStats.lateCount} on-time / {monthStats.presentCount} days present</small>
            </div>
            <div className="analytics-chip">
              <span className="chip-label">NET SHIFT BALANCE</span>
              <strong style={{ color: monthStats.netBalanceMinutes >= 0 ? "#08aa75" : "#df384b" }}>
                {monthStats.netBalanceMinutes >= 0 ? `+${formatDuration(monthStats.netBalanceMinutes)} OT` : `-${formatDuration(Math.abs(monthStats.netBalanceMinutes))} Deficit`}
              </strong>
              <small>Calculated against 8.5h shift target</small>
            </div>
          </div>

          {/* Tab 1: Daily Hours vs Target Chart */}
          {graphTab === "hours" ? (
            <div className="attendance-chart-wrapper">
              <div className="chart-header-row">
                <span className="chart-subtitle">
                  Daily working hours recorded for {monthNames[selectedMonth]} {selectedYear}. Dashed line indicates your daily 8.5h shift target.
                </span>
                <div className="chart-legend">
                  <span className="legend-item"><span className="legend-dot dot-green" /> Target Met (≥ 8.5h)</span>
                  <span className="legend-item"><span className="legend-dot dot-amber" /> In Progress / Near Target</span>
                  <span className="legend-item"><span className="legend-dot dot-red" /> Deficit / Under Hours</span>
                  <span className="legend-item"><span className="legend-dot dot-gray" /> Off Duty / Weekend</span>
                </div>
              </div>

              {/* The Visual Bar Chart */}
              <div className="bars-chart-container">
                {/* Reference line for 8.5 hours */}
                <div className="target-guideline" style={{ bottom: `${(8.5 / 11) * 100}%` }}>
                  <span className="guideline-badge">Shift Target: 8.5h</span>
                </div>

                <div className="bars-grid">
                  {monthlyDailyData.map((d) => {
                    const hoursVal = d.workingMins ? (d.workingMins / 60) : 0;
                    const heightPercent = Math.min(100, Math.max(hoursVal > 0 ? 8 : 0, (hoursVal / 11) * 100));
                    const isSelected = selectedGraphDay?.dayNum === d.dayNum;
                    const isMet = d.workingMins >= 510;
                    const isPartial = d.workingMins > 0 && d.workingMins < 510;
                    const isWeekendOrOff = !d.schedule;

                    let barColorClass = "bar-off";
                    if (d.isToday && isPartial) barColorClass = "bar-today";
                    else if (isMet) barColorClass = "bar-met";
                    else if (isPartial && d.workingMins >= 420) barColorClass = "bar-near";
                    else if (isPartial) barColorClass = "bar-deficit";
                    else if (d.schedule && d.date < now) barColorClass = "bar-absent";

                    return (
                      <div
                        key={d.dayNum}
                        className={`bar-column ${isSelected ? "selected-col" : ""} ${d.isToday ? "today-col" : ""}`}
                        onClick={() => setSelectedGraphDay(d)}
                        title={`${d.dateFormatted}: ${d.workingFormatted} (Click to inspect)`}
                      >
                        <span className="bar-val-text">
                          {hoursVal > 0 ? `${hoursVal.toFixed(1)}h` : isWeekendOrOff ? "—" : "0h"}
                        </span>
                        <div className="bar-track">
                          <div
                            className={`bar-fill ${barColorClass}`}
                            style={{ height: `${heightPercent}%` }}
                          />
                        </div>
                        <span className="bar-day-num">{d.dayNum}</span>
                        <span className="bar-day-name">{weekdays[d.date.getDay()].slice(0, 2)}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Interactive Selected Day Details Drawer */}
              {selectedGraphDay && (
                <div className="graph-selected-day-card">
                  <div className="selected-day-header">
                    <div>
                      <span className="selected-day-label">INSPECTED DAY ATTENDANCE</span>
                      <strong>{selectedGraphDay.dateFormatted}</strong>
                    </div>
                    <button
                      type="button"
                      className="close-day-btn"
                      onClick={() => setSelectedGraphDay(null)}
                    >
                      Close ×
                    </button>
                  </div>
                  <div className="selected-day-metrics">
                    <div className="day-metric">
                      <span>Allocated Shift</span>
                      <strong>{selectedGraphDay.scheduleFormatted}</strong>
                    </div>
                    <div className="day-metric">
                      <span>Clock In (Arrival)</span>
                      <strong>{selectedGraphDay.arrivalFormatted}</strong>
                      <small className={selectedGraphDay.arrivalDiff > 5 ? "txt-red" : "txt-green"}>
                        {selectedGraphDay.arrivalStatus}
                      </small>
                    </div>
                    <div className="day-metric">
                      <span>Clock Out (Leaving)</span>
                      <strong>{selectedGraphDay.leaveFormatted}</strong>
                      <small className="txt-muted">{selectedGraphDay.leaveStatus}</small>
                    </div>
                    <div className="day-metric">
                      <span>Total Time Worked</span>
                      <strong className="txt-primary">{selectedGraphDay.workingFormatted}</strong>
                      <small className={selectedGraphDay.workingMins >= 510 ? "txt-green" : "txt-red"}>
                        {selectedGraphDay.workingMins >= 510
                          ? `+${formatDuration(selectedGraphDay.workingMins - 510)} vs Target`
                          : `-${formatDuration(510 - selectedGraphDay.workingMins)} vs Target`}
                      </small>
                    </div>
                    <div className="day-metric">
                      <span>Status</span>
                      <span className={`status-pill ${selectedGraphDay.statusClass}`}>
                        {selectedGraphDay.statusTag}
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* Tab 2: Punctuality Variance Track View */
            <div className="attendance-variance-wrapper">
              <div className="chart-header-row">
                <span className="chart-subtitle">
                  Daily arrival punctuality relative to scheduled start time ({employee.shiftTime ? employee.shiftTime.split("to")[0].trim() : "08:30"}).
                </span>
                <div className="chart-legend">
                  <span className="legend-item"><span className="legend-dot dot-green" /> On Time / Early</span>
                  <span className="legend-item"><span className="legend-dot dot-red" /> Late Arrival</span>
                  <span className="legend-item"><span className="legend-dot dot-gray" /> No Punch / Off Day</span>
                </div>
              </div>

              <div className="variance-track-list">
                {monthlyDailyData.filter((d) => d.schedule && d.date <= now).map((d) => {
                  const isLate = d.arrivalDiff && d.arrivalDiff > 5;
                  const isEarly = d.arrivalDiff && d.arrivalDiff < -5;
                  const isPunctual = d.records.length > 0 && !isLate;
                  const noScan = d.records.length === 0;

                  return (
                    <div key={d.dayNum} className="variance-track-row">
                      <div className="variance-date">
                        <strong>{d.dateFormatted}</strong>
                        <span>{d.scheduleFormatted}</span>
                      </div>

                      <div className="variance-bar-area">
                        <div className="variance-axis">
                          <span className="axis-mark">-30m</span>
                          <span className="axis-center">Shift Start (0m)</span>
                          <span className="axis-mark">+30m</span>
                        </div>
                        <div className="variance-track-line">
                          <div className="variance-center-pin" />
                          {d.arrivalDiff !== null && (
                            <div
                              className={`variance-pill-indicator ${isLate ? "is-late" : isEarly ? "is-early" : "is-ontime"}`}
                              style={{
                                left: `${Math.min(95, Math.max(5, 50 + (d.arrivalDiff / 60) * 100))}%`,
                              }}
                            >
                              <span>{d.arrivalFormatted}</span>
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="variance-summary">
                        {noScan ? (
                          <span className="diff-tag diff-absent">No Scan</span>
                        ) : (
                          <span className={`diff-tag ${isLate ? "diff-late" : isEarly ? "diff-early" : "diff-ontime"}`}>
                            {d.arrivalStatus}
                          </span>
                        )}
                        <span className="working-hours-note">{d.workingFormatted} worked</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </article>
      )}

      {/* 5. RECORDS TABLE & DETAILED AUDIT PANEL */}
      {!error && (
        <article className="panel attendance-records-panel" aria-label="Attendance records and shift tracking">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">DETAILED AUDIT LOG</span>
              <h3>Daily Attendance Records</h3>
            </div>
            <div className="panel-heading-icon">
              <CalendarDays size={17} />
            </div>
          </div>

          <div className="stage-controls-wrap">
            <div className="filter-group">
              <label>
                <span>Month</span>
                <select
                  value={selectedMonth}
                  onChange={(e) => setSelectedMonth(Number(e.target.value))}
                  className="select-filter"
                >
                  {monthNames.map((name, idx) => (
                    <option key={name} value={idx}>{name}</option>
                  ))}
                </select>
              </label>

              <label>
                <span>Year</span>
                <select
                  value={selectedYear}
                  onChange={(e) => setSelectedYear(Number(e.target.value))}
                  className="select-filter"
                >
                  {availableYears.map((y) => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </select>
              </label>

              <label>
                <span>Filter By Status</span>
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="select-filter"
                >
                  <option value="all">All Days</option>
                  <option value="present">Present Only</option>
                  <option value="ontime">On-Time Only</option>
                  <option value="late">Late Only</option>
                </select>
              </label>
            </div>

            <button
              type="button"
              className="outline-button btn-export-excel-table"
              onClick={handleExportExcel}
              title="Download this month's attendance records in Microsoft Excel format"
            >
              <Download size={14} /> Export into Excel
            </button>
          </div>

          {/* DETAILED DAILY ATTENDANCE TABLE ACCORDING TO ALLOCATED SHIFT */}
          <div className="attendance-daily-card">
            <div className="attendance-table-wrap">
              <table className="daily-attendance-table">
                <thead>
                  <tr>
                    <th>Date & Day</th>
                    <th>Allocated Shift</th>
                    <th>Arrival (In)</th>
                    <th>Leaving (Out)</th>
                    <th>Total Hours</th>
                    <th>Status</th>
                    <th style={{ textAlign: "right" }}>Raw Punches</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDays.map((d) => {
                    const isExpanded = expandedDate === d.dayNum;
                    return (
                      <tr key={d.dayNum} className={`daily-row ${d.isToday ? "today-highlight-row" : ""}`}>
                        <td className="cell-date">
                          <span className="date-main">{d.dateFormatted}</span>
                          {d.isToday && <span className="today-badge">Today</span>}
                        </td>

                        <td className="cell-shift">
                          <span className="shift-badge">
                            <Briefcase size={12} />
                            {d.scheduleFormatted}
                          </span>
                        </td>

                        <td className="cell-arrival">
                          <div className="punch-time-wrap">
                            <span className="punch-time">{d.arrivalFormatted}</span>
                            {d.firstRec && (
                              <span className={`diff-tag ${d.arrivalDiff && d.arrivalDiff > 5 ? "diff-late" : d.arrivalDiff && d.arrivalDiff < -5 ? "diff-early" : "diff-ontime"}`}>
                                {d.arrivalStatus}
                              </span>
                            )}
                          </div>
                        </td>

                        <td className="cell-leave">
                          <div className="punch-time-wrap">
                            <span className="punch-time">{d.leaveFormatted}</span>
                            {d.lastRec && (
                              <span className={`diff-tag ${d.leaveDiff && d.leaveDiff > 10 ? "diff-ot" : d.leaveDiff && d.leaveDiff < -10 ? "diff-early-leave" : "diff-ontime"}`}>
                                {d.leaveStatus}
                              </span>
                            )}
                          </div>
                        </td>

                        <td className="cell-hours">
                          <strong className="hours-text">{d.workingFormatted}</strong>
                        </td>

                        <td className="cell-status">
                          <span className={`status-pill ${d.statusClass}`}>
                            {d.statusTag}
                          </span>
                        </td>

                        <td className="cell-actions" style={{ textAlign: "right" }}>
                          {d.records.length > 0 ? (
                            <button
                              type="button"
                              className="btn-toggle-punches"
                              onClick={() => setExpandedDate(isExpanded ? null : d.dayNum)}
                              title="Toggle raw access sensor events"
                            >
                              <span>{d.records.length} {d.records.length === 1 ? "log" : "logs"}</span>
                              {isExpanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                            </button>
                          ) : (
                            <span className="no-logs">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* EXPANDED SENSOR LOG VIEW MODAL/DRAWER ACCORDION */}
            {expandedDate && (
              <div className="expanded-logs-panel">
                <div className="expanded-logs-header">
                  <strong>Access Control Swipes for {monthlyDailyData.find((d) => d.dayNum === expandedDate)?.dateFormatted}</strong>
                  <button type="button" className="close-expand" onClick={() => setExpandedDate(null)}>Close</button>
                </div>
                <div className="expanded-logs-list">
                  {monthlyDailyData.find((d) => d.dayNum === expandedDate)?.records.map((r, i) => (
                    <div key={r.id || i} className="expanded-log-item">
                      <span className="log-time"><Clock3 size={13} /> {formatDateTime(r.eventTime, true)}</span>
                      <span className="log-event">{r.eventName || r.readerState || "Access event"}</span>
                      <span className="log-loc"><MapPin size={12} /> {r.areaName || "Campus Door"}</span>
                      <span className="log-reader">{r.readerName || "Terminal"}</span>
                      <span className="log-mode">{r.verificationMode || "Card/Portal"}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </article>
      )}

      {/* FOOTER */}
      <footer className="page-footer attendance-footer">
        <span>© {new Date().getFullYear()} SCOT University Management System · Attendance & Shift Compliance</span>
        <span>Verified against campus biometric access controllers.</span>
      </footer>
    </div>
  );
}