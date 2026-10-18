import { useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  Bell,
  Bookmark,
  Calendar as CalendarIcon,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Clock3,
  Edit3,
  ExternalLink,
  Filter,
  Flame,
  LayoutDashboard,
  MapPin,
  Megaphone,
  Plus,
  Search,
  Share2,
  Sparkles,
  Trash2,
  User,
  UsersRound,
  Video,
  X,
} from "lucide-react";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";

function formatTime(date) {
  return new Intl.DateTimeFormat("en-US", {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  }).format(date);
}

function formatDateFull(date) {
  return new Intl.DateTimeFormat("en-US", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(date);
}

function formatDateShort(dateString) {
  if (!dateString) return "";
  const d = new Date(`${dateString}T00:00:00`);
  if (Number.isNaN(d.getTime())) return dateString;
  return new Intl.DateTimeFormat("en-US", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(d);
}

// Generates Google Calendar Template link to sync/add event
function generateGoogleCalendarUrl({ title, description, location, startDate, startTime, endTime }) {
  const base = "https://calendar.google.com/calendar/render?action=TEMPLATE";
  const start = startDate ? startDate.replace(/-/g, "") : new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const sTime = startTime ? startTime.replace(":", "") + "00" : "090000";
  const eTime = endTime ? endTime.replace(":", "") + "00" : "100000";
  const dates = `${start}T${sTime}/${start}T${eTime}`;

  const params = new URLSearchParams({
    text: title || "SCOT University Event",
    dates,
    details: description || "Event from SCOT University Portal",
    location: location || "SCOT Campus",
  });
  return `${base}&${params.toString()}`;
}

export default function Dashboard({ employee, onNavigate }) {
  const [currentTime, setCurrentTime] = useState(() => new Date());
  const [news, setNews] = useState([]);
  const [newsLoading, setNewsLoading] = useState(true);
  const [newsError, setNewsError] = useState("");
  const [newsCategory, setNewsCategory] = useState("All");
  const [newsSearch, setNewsSearch] = useState("");

  // Modals
  const [selectedArticle, setSelectedArticle] = useState(null);
  const [newsModalMode, setNewsModalMode] = useState(null); // 'create' | 'edit'
  const [editingArticle, setEditingArticle] = useState(null);
  const [reminderModalNews, setReminderModalNews] = useState(null);
  const [showAddEventModal, setShowAddEventModal] = useState(false);

  // Calendar State
  const [calendarMonth, setCalendarMonth] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().slice(0, 10));

  // Events List State (persisted in localStorage)
  const [events, setEvents] = useState(() => {
    try {
      const saved = localStorage.getItem("scot_portal_events");
      if (saved) return JSON.parse(saved);
    } catch {}
    const today = new Date().toISOString().slice(0, 10);
    const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
    const inThreeDays = new Date(Date.now() + 86400000 * 3).toISOString().slice(0, 10);
    return [
      {
        id: "evt-1",
        title: "Academic Senate - Curriculum Review Meeting",
        date: today,
        startTime: "09:30",
        endTime: "11:00",
        category: "Academic",
        location: "Senate Boardroom & Google Meet",
        meetLink: "https://meet.google.com/scot-senate-meet",
        description: "Review of proposed 2026/2027 curriculum changes for Computing and Engineering faculties.",
      },
      {
        id: "evt-2",
        title: "Faculty Mentorship & Research Sync",
        date: today,
        startTime: "14:00",
        endTime: "15:30",
        category: "Meeting",
        location: "Google Meet",
        meetLink: "https://meet.google.com/scot-res-sync",
        description: "Bi-weekly departmental sync on ongoing student research guidance and seed grant applications.",
      },
      {
        id: "evt-3",
        title: "Staff Wellness Program - Health Camp",
        date: tomorrow,
        startTime: "10:00",
        endTime: "16:00",
        category: "HR",
        location: "University Health Centre, Ground Floor",
        meetLink: "",
        description: "Annual health checkup camp conducted by Asiri Health. Free tests for permanent and probationary staff.",
      },
      {
        id: "evt-4",
        title: "Semester 2 Final Exam Briefing",
        date: inThreeDays,
        startTime: "15:00",
        endTime: "16:30",
        category: "Admin",
        location: "Auditorium A",
        meetLink: "",
        description: "Mandatory briefing for all exam invigilators, supervisors, and department exam coordinators.",
      },
    ];
  });

  // Reminders State (persisted in localStorage)
  const [reminders, setReminders] = useState(() => {
    try {
      const saved = localStorage.getItem(`scot_reminders_${employee.workEmail || "user"}`);
      if (saved) return JSON.parse(saved);
    } catch {}
    return [
      {
        id: "rem-1",
        title: "Confirm Attendance for Convocation 2026",
        sourceTitle: "Annual University Convocation & Awards Ceremony 2026",
        dateTime: new Date(Date.now() + 3600000 * 5).toISOString(),
        notes: "Submit academic regalia measurements to HR office.",
        completed: false,
      },
      {
        id: "rem-2",
        title: "Wellness Week Health Check Registration",
        sourceTitle: "Staff Health & Wellness Week",
        dateTime: new Date(Date.now() + 86400000 * 2).toISOString(),
        notes: "Book morning time slot for fasting blood sugar test.",
        completed: false,
      },
    ];
  });

  // Live Toast Notification
  const [activeToast, setActiveToast] = useState(null);

  // Authorization check for SCOT News management
  const canManageNews = useMemo(() => {
    const email = (employee.workEmail || employee.email || "").trim().toLowerCase();
    const role = (employee.role || "").trim().toLowerCase();
    const isHrAdmin = ["hr@scot.lk", "admin@scot.lk", "sheran@scot.lk", "it@scot.lk"].includes(email);
    const hasAdminRole = role.includes("admin");
    return employee.evaluationAdmin === true || isHrAdmin || hasAdminRole;
  }, [employee]);

  // Live Clock update
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Save events to localStorage
  useEffect(() => {
    try {
      localStorage.setItem("scot_portal_events", JSON.stringify(events));
    } catch {}
  }, [events]);

  // Save reminders to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(`scot_reminders_${employee.workEmail || "user"}`, JSON.stringify(reminders));
    } catch {}
  }, [reminders, employee.workEmail]);

  // Reminder Checker (checks every 15 seconds)
  useEffect(() => {
    const checker = setInterval(() => {
      const now = new Date().getTime();
      reminders.forEach((reminder) => {
        if (!reminder.completed && !reminder.notified) {
          const target = new Date(reminder.dateTime).getTime();
          // If within 2 minutes of reminder time or past
          if (now >= target && now - target < 120000) {
            // Trigger in-app Toast
            setActiveToast({
              title: "🔔 Reminder Alert",
              message: reminder.title,
              source: reminder.sourceTitle,
            });

            // Trigger Desktop Notification if permitted
            if ("Notification" in window && Notification.permission === "granted") {
              try {
                new Notification(`SCOT Portal: ${reminder.title}`, {
                  body: reminder.notes || `Reminder for: ${reminder.sourceTitle || "SCOT Event"}`,
                  icon: "/favicon.ico",
                });
              } catch {}
            }

            // Mark notified
            setReminders((prev) =>
              prev.map((r) => (r.id === reminder.id ? { ...r, notified: true } : r))
            );
          }
        }
      });
    }, 15000);
    return () => clearInterval(checker);
  }, [reminders]);

  // Load News from Backend
  useEffect(() => {
    let cancelled = false;
    setNewsLoading(true);
    fetch(`${apiBase}/api/news`, {
      headers: { Authorization: `Bearer ${employee.idToken}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error("Could not load SCOT News");
        return res.json();
      })
      .then((data) => {
        if (!cancelled && Array.isArray(data)) {
          setNews(data);
          setNewsLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setNewsError(err.message);
          setNewsLoading(false);
        }
      });
    return () => { cancelled = true; };
  }, [employee.idToken]);

  // Filtered News
  const filteredNews = useMemo(() => {
    let list = news;
    if (newsCategory === "Special") {
      list = list.filter((n) => n.isSpecial);
    } else if (newsCategory !== "All") {
      list = list.filter((n) => n.category.toLowerCase().includes(newsCategory.toLowerCase()));
    }
    if (newsSearch.trim()) {
      const q = newsSearch.toLowerCase();
      list = list.filter((n) =>
        n.title.toLowerCase().includes(q) ||
        n.summary.toLowerCase().includes(q) ||
        n.category.toLowerCase().includes(q)
      );
    }
    return list;
  }, [news, newsCategory, newsSearch]);

  // Save / Delete News Handlers (Admin)
  async function handleSaveNews(newsData) {
    const isEdit = Boolean(editingArticle?.id);
    const url = isEdit ? `${apiBase}/api/news/${editingArticle.id}` : `${apiBase}/api/news`;
    const method = isEdit ? "PUT" : "POST";

    const response = await fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${employee.idToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(newsData),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || "Failed to save news bulletin.");
    }
    const saved = await response.json();
    if (isEdit) {
      setNews((prev) => prev.map((item) => (item.id === saved.id ? saved : item)));
    } else {
      setNews((prev) => [saved, ...prev]);
    }
    setNewsModalMode(null);
    setEditingArticle(null);
    setActiveToast({
      title: isEdit ? "News Updated" : "News Published",
      message: `"${saved.title}" is now published to all staff.`,
    });
  }

  async function handleDeleteNews(articleId) {
    if (!window.confirm("Are you sure you want to delete this news bulletin?")) return;
    try {
      const response = await fetch(`${apiBase}/api/news/${articleId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${employee.idToken}` },
      });
      if (!response.ok) throw new Error("Could not delete news article");
      setNews((prev) => prev.filter((item) => item.id !== articleId));
      setActiveToast({
        title: "News Deleted",
        message: "The announcement was removed successfully.",
      });
    } catch (e) {
      alert(e.message);
    }
  }

  // Reminder Management
  function handleAddReminder(data) {
    const newRem = {
      id: `rem-${Date.now()}`,
      title: data.title,
      sourceTitle: data.sourceTitle,
      dateTime: `${data.date}T${data.time}:00`,
      notes: data.notes || "",
      completed: false,
      notified: false,
    };
    setReminders((prev) => [newRem, ...prev]);
    setReminderModalNews(null);

    // Request desktop permission if not already asked
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission();
    }

    setActiveToast({
      title: "Reminder Scheduled 🔔",
      message: `You'll be notified on ${formatDateShort(data.date)} at ${data.time}`,
    });
  }

  function toggleReminderComplete(id) {
    setReminders((prev) =>
      prev.map((r) => (r.id === id ? { ...r, completed: !r.completed } : r))
    );
  }

  function deleteReminder(id) {
    setReminders((prev) => prev.filter((r) => r.id !== id));
  }

  // Calendar Days Calculation
  const calendarDays = useMemo(() => {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const firstDay = new Date(year, month, 1).getDay(); // 0 is Sunday
    // Convert to Monday-start (0 = Mon, 6 = Sun)
    const startOffset = (firstDay + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const days = [];
    // Previous month padding
    for (let i = 0; i < startOffset; i++) {
      days.push({ dayNumber: null, isCurrentMonth: false, dateStr: null });
    }
    // Days in current month
    for (let d = 1; d <= daysInMonth; d++) {
      const monthPadded = String(month + 1).padStart(2, "0");
      const dayPadded = String(d).padStart(2, "0");
      const dateStr = `${year}-${monthPadded}-${dayPadded}`;
      const dayEvents = events.filter((e) => e.date === dateStr);
      days.push({
        dayNumber: d,
        isCurrentMonth: true,
        dateStr,
        hasEvents: dayEvents.length > 0,
        eventCount: dayEvents.length,
      });
    }
    return days;
  }, [calendarMonth, events]);

  const selectedDayEvents = useMemo(() => {
    return events.filter((e) => e.date === selectedDate);
  }, [events, selectedDate]);

  function changeMonth(offset) {
    setCalendarMonth(new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + offset, 1));
  }

  const name = employee.preferredName || employee.fullName || "Colleague";

  return (
    <div className="page-content dashboard-page">
      {/* Toast Notification Banner */}
      {activeToast && (
        <div className="dashboard-toast" role="alert">
          <div className="toast-icon">
            <Bell size={17} />
          </div>
          <div className="toast-content">
            <strong>{activeToast.title}</strong>
            <p>{activeToast.message}</p>
          </div>
          <button className="toast-close" onClick={() => setActiveToast(null)}>
            <X size={15} />
          </button>
        </div>
      )}

      {/* Top Welcome Row (matches Profile & Directory page UI) */}
      <section className="welcome-row">
        <div>
          <span className="directory-kicker">STAFF PORTAL</span>
          <h1>Dashboard</h1>
          <p>Campus announcements, academic calendar schedules, and personal reminders.</p>
        </div>
        <div className="welcome-row-actions">
          {canManageNews && (
            <button
              type="button"
              className="outline-button"
              onClick={() => {
                setEditingArticle(null);
                setNewsModalMode("create");
              }}
            >
              <Plus size={15} /> Post Bulletin
            </button>
          )}
        </div>
      </section>

      {/* 4 Metric Stats Cards (using exact Profile page .profile-metrics theme) */}
      <section className="profile-metrics" aria-label="Dashboard summary">
        <article className="metric-card metric-slate">
          <span className="metric-label">STAFF SHIFT</span>
          <strong>{employee.shiftTime || "8:30 to 5:30"}</strong>
          <span className="metric-caption">{employee.workingDays || "Monday to Friday"}</span>
          <span className="metric-icon"><Clock3 size={23} /></span>
        </article>

        <article className="metric-card metric-green">
          <span className="metric-label">CALENDAR SCHEDULE</span>
          <strong>{events.length} Events</strong>
          <span className="metric-caption">
            {selectedDayEvents.length} scheduled on {formatDateShort(selectedDate)}
          </span>
          <span className="metric-icon"><CalendarDays size={23} /></span>
        </article>

        <article className="metric-card metric-amber">
          <span className="metric-label">SCOT BULLETINS</span>
          <strong>{news.length} Notices</strong>
          <span className="metric-caption">
            {news.filter((n) => n.isSpecial).length} Special Alert{news.filter((n) => n.isSpecial).length === 1 ? "" : "s"}
          </span>
          <span className="metric-icon"><Megaphone size={23} /></span>
        </article>

        <article className="metric-card metric-red">
          <span className="metric-label">MY REMINDERS</span>
          <strong>{reminders.filter((r) => !r.completed).length} Pending</strong>
          <span className="metric-caption">
            {reminders.filter((r) => r.completed).length} completed tasks
          </span>
          <span className="metric-icon"><Bell size={23} /></span>
        </article>
      </section>

      {/* Main 2-Column Dashboard Layout */}
      <div className="dashboard-main-layout">
        {/* Left Column: SCOT News Section Panel */}
        <article className="panel dashboard-news-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">CAMPUS &amp; STAFF NOTICES</span>
              <h3>SCOT News &amp; Bulletins</h3>
            </div>
            <div className="panel-heading-icon">
              <Megaphone size={16} />
            </div>
          </div>

          {/* Search & Category Filter Pills */}
          <div className="news-filter-bar">
            <div className="news-search-box">
              <Search size={16} />
              <input
                type="text"
                placeholder="Search bulletins..."
                value={newsSearch}
                onChange={(e) => setNewsSearch(e.target.value)}
              />
              {newsSearch && (
                <button className="clear-search-btn" onClick={() => setNewsSearch("")}>
                  <X size={14} />
                </button>
              )}
            </div>

            <div className="news-categories">
              {["All", "Special", "Events", "Academic", "HR & Welfare", "Administration"].map((cat) => (
                <button
                  key={cat}
                  type="button"
                  className={`category-pill${newsCategory === cat ? " active" : ""}`}
                  onClick={() => setNewsCategory(cat)}
                >
                  {cat === "Special" && <Flame size={13} className="special-flame-icon" />}
                  <span>{cat}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Equal-Sized News Grid */}
          {newsLoading ? (
            <div className="news-cards-grid">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="news-card-skeleton" />
              ))}
            </div>
          ) : filteredNews.length === 0 ? (
            <div className="news-empty-state">
              <Megaphone size={36} />
              <h3>No announcements found</h3>
              <p>There are currently no bulletins matching your search or category filter.</p>
              {(newsSearch || newsCategory !== "All") && (
                <button
                  type="button"
                  className="clear-filters-btn"
                  onClick={() => {
                    setNewsSearch("");
                    setNewsCategory("All");
                  }}
                >
                  Reset filters
                </button>
              )}
            </div>
          ) : (
            <div className="news-cards-grid">
              {filteredNews.map((article) => (
                <article key={article.id} className={`news-card${article.isSpecial ? " is-special-card" : ""}`}>
                  {/* Top Image Banner */}
                  <div className="news-card-media">
                    <img
                      src={article.imageUrl}
                      alt={article.title}
                      loading="lazy"
                      onError={(e) => {
                        e.currentTarget.src =
                          "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=800&q=80";
                      }}
                    />
                    <div className="news-card-media-overlay" />
                    <span className="news-category-badge">{article.category}</span>
                    {article.isSpecial && (
                      <span className="news-special-badge">
                        <Flame size={12} />
                        <span>SPECIAL ALERT</span>
                      </span>
                    )}
                  </div>

                  {/* Card Content Body */}
                  <div className="news-card-body">
                    <div className="news-card-meta">
                      <span className="news-date">{formatDateShort(article.date)}</span>
                      <span className="meta-dot">·</span>
                      <span className="news-author">{article.authorName}</span>
                    </div>

                    <h3 className="news-card-title" title={article.title}>
                      {article.title}
                    </h3>

                    <p className="news-card-summary" title={article.summary}>
                      {article.summary}
                    </p>
                  </div>

                  {/* Card Footer Actions */}
                  <div className="news-card-footer">
                    <div className="footer-left-actions">
                      <button
                        type="button"
                        className={`action-btn reminder-btn${article.isSpecial ? " reminder-highlight" : ""}`}
                        onClick={() => setReminderModalNews(article)}
                        title="Add this announcement to your personal reminders with custom notifications"
                      >
                        <Bell size={14} />
                        <span>Remind Me</span>
                      </button>

                      <button
                        type="button"
                        className="action-btn read-btn"
                        onClick={() => setSelectedArticle(article)}
                      >
                        <span>Details</span>
                      </button>
                    </div>

                    {canManageNews && (
                      <div className="footer-admin-actions">
                        <button
                          type="button"
                          className="icon-action-btn edit-btn"
                          onClick={() => {
                            setEditingArticle(article);
                            setNewsModalMode("edit");
                          }}
                          title="Edit news announcement"
                        >
                          <Edit3 size={14} />
                        </button>
                        <button
                          type="button"
                          className="icon-action-btn delete-btn"
                          onClick={() => handleDeleteNews(article.id)}
                          title="Delete news announcement"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </article>

        {/* Right Column: Google Calendar & Schedule + Reminders Widget */}
        <aside className="dashboard-side-panel">
          {/* Google Calendar Panel */}
          <article className="panel dashboard-calendar-panel">
            <div className="panel-heading">
              <div>
                <span className="panel-kicker">SCHEDULE &amp; GOOGLE CALENDAR</span>
                <h3>Google Calendar</h3>
              </div>
              <div className="panel-heading-icon">
                <CalendarDays size={16} />
              </div>
            </div>

            <div className="gcal-account-row">
              <span className="gcal-account-badge">
                <span className="sync-dot" /> Linked to {employee.workEmail || "SCOT Account"}
              </span>
              <button
                type="button"
                className="gcal-launch-btn"
                onClick={() => {
                  const url = `https://calendar.google.com/calendar/u/0/r?authuser=${encodeURIComponent(
                    employee.workEmail || ""
                  )}`;
                  window.open(url, "_blank");
                }}
                title="Launch Google Calendar"
              >
                <span>Open Calendar</span>
                <ExternalLink size={12} />
              </button>
            </div>

            {/* Interactive Month Grid */}
            <div className="mini-calendar-widget">
              <div className="calendar-nav-row">
                <strong>
                  {new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(calendarMonth)}
                </strong>
                <div className="calendar-nav-controls">
                  <button type="button" onClick={() => changeMonth(-1)} aria-label="Previous month">
                    <ChevronLeft size={16} />
                  </button>
                  <button
                    type="button"
                    className="today-pill"
                    onClick={() => {
                      setCalendarMonth(new Date());
                      setSelectedDate(new Date().toISOString().slice(0, 10));
                    }}
                  >
                    Today
                  </button>
                  <button type="button" onClick={() => changeMonth(1)} aria-label="Next month">
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>

              <div className="calendar-weekdays">
                <span>Mo</span>
                <span>Tu</span>
                <span>We</span>
                <span>Th</span>
                <span>Fr</span>
                <span>Sa</span>
                <span>Su</span>
              </div>

              <div className="calendar-days-grid">
                {calendarDays.map((d, index) => {
                  if (!d.isCurrentMonth) {
                    return <div key={`empty-${index}`} className="calendar-day-cell empty" />;
                  }
                  const todayStr = new Date().toISOString().slice(0, 10);
                  const isToday = d.dateStr === todayStr;
                  const isSelected = d.dateStr === selectedDate;

                  return (
                    <button
                      key={d.dateStr}
                      type="button"
                      className={`calendar-day-cell${isToday ? " is-today" : ""}${
                        isSelected ? " is-selected" : ""
                      }${d.hasEvents ? " has-events" : ""}`}
                      onClick={() => setSelectedDate(d.dateStr)}
                    >
                      <span className="day-number">{d.dayNumber}</span>
                      {d.hasEvents && (
                        <span className="event-dot" title={`${d.eventCount} event(s)`} />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Selected Date Schedule List */}
            <div className="selected-date-schedule">
              <div className="schedule-header">
                <div>
                  <span className="schedule-label">SCHEDULE &amp; EVENTS</span>
                  <strong>{formatDateShort(selectedDate)}</strong>
                </div>
                <button
                  type="button"
                  className="add-event-btn"
                  onClick={() => setShowAddEventModal(true)}
                  title="Schedule a new calendar event"
                >
                  <Plus size={13} />
                  <span>Event</span>
                </button>
              </div>

              <div className="events-timeline">
                {selectedDayEvents.length === 0 ? (
                  <div className="no-events-banner">
                    <CalendarIcon size={18} />
                    <span>No events scheduled on this date.</span>
                  </div>
                ) : (
                  selectedDayEvents.map((evt) => (
                    <div key={evt.id} className="timeline-event-card">
                      <div className="event-time-badge">
                        <Clock size={12} />
                        <span>{evt.startTime} – {evt.endTime}</span>
                      </div>

                      <div className="event-details">
                        <strong className="event-title">{evt.title}</strong>
                        {evt.description && <p className="event-desc">{evt.description}</p>}

                        <div className="event-meta-tags">
                          {evt.location && (
                            <span className="meta-tag location">
                              <MapPin size={11} />
                              <span>{evt.location}</span>
                            </span>
                          )}
                          {evt.meetLink && (
                            <a
                              href={evt.meetLink}
                              target="_blank"
                              rel="noreferrer"
                              className="meta-tag meet-link"
                            >
                              <Video size={11} />
                              <span>Join Meet</span>
                            </a>
                          )}
                        </div>
                      </div>

                      <div className="event-card-actions">
                        <button
                          type="button"
                          className="sync-to-gcal-btn"
                          title="Add this event to your Google Calendar directly"
                          onClick={() => {
                            const url = generateGoogleCalendarUrl({
                              title: evt.title,
                              description: evt.description,
                              location: evt.location,
                              startDate: evt.date,
                              startTime: evt.startTime,
                              endTime: evt.endTime,
                            });
                            window.open(url, "_blank");
                          }}
                        >
                          <CalendarIcon size={13} />
                          <span>Sync to Google</span>
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </article>

          {/* My Reminders Section Card */}
          <article className="panel dashboard-reminders-panel">
            <div className="panel-heading">
              <div>
                <span className="panel-kicker">PERSONAL NOTIFICATIONS</span>
                <h3>My Reminders</h3>
              </div>
              <div className="panel-heading-icon panel-heading-icon-purple">
                <Bell size={16} />
              </div>
            </div>

            <div className="reminders-meta-row">
              <span className="reminders-caption">Alerts &amp; scheduled news follow-ups</span>
              <span className="reminders-count-badge">
                {reminders.filter((r) => !r.completed).length} Pending
              </span>
            </div>

            <div className="reminders-list">
              {reminders.length === 0 ? (
                <div className="no-reminders-banner">
                  <Bell size={20} />
                  <p>No reminders set. Click "Remind Me" on any bulletin to get notified!</p>
                </div>
              ) : (
                reminders.map((rem) => {
                  const targetTime = new Date(rem.dateTime).getTime();
                  const isDue = Date.now() >= targetTime;

                  return (
                    <div
                      key={rem.id}
                      className={`reminder-item${rem.completed ? " is-completed" : ""}${
                        isDue && !rem.completed ? " is-due" : ""
                      }`}
                    >
                      <button
                        type="button"
                        className="reminder-check-btn"
                        onClick={() => toggleReminderComplete(rem.id)}
                        title={rem.completed ? "Mark incomplete" : "Mark done"}
                      >
                        {rem.completed ? <CheckCircle2 size={16} /> : <div className="check-ring" />}
                      </button>

                      <div className="reminder-info">
                        <strong className="reminder-title">{rem.title}</strong>
                        {rem.sourceTitle && (
                          <span className="reminder-source">Re: {rem.sourceTitle}</span>
                        )}
                        <span className="reminder-due-time">
                          <Clock3 size={11} />
                          <span>
                            {new Intl.DateTimeFormat("en-US", {
                              month: "short",
                              day: "numeric",
                              hour: "numeric",
                              minute: "2-digit",
                              hour12: true,
                            }).format(new Date(rem.dateTime))}
                          </span>
                          {isDue && !rem.completed && (
                            <span className="due-chip">DUE NOW 🔔</span>
                          )}
                        </span>
                      </div>

                      <button
                        type="button"
                        className="reminder-delete-btn"
                        onClick={() => deleteReminder(rem.id)}
                        title="Delete reminder"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  );
                })
              )}
            </div>
          </article>
        </aside>
      </div>

      {/* Modal: Read Full Article Details */}
      {selectedArticle && (
        <div
          className="directory-modal-backdrop"
          role="presentation"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setSelectedArticle(null);
          }}
        >
          <div className="directory-modal news-details-modal">
            <button className="directory-modal-close" onClick={() => setSelectedArticle(null)}>
              <X size={19} />
            </button>

            <div className="news-details-banner">
              <img src={selectedArticle.imageUrl} alt="" />
              <div className="news-details-overlay" />
              <div className="news-details-badge-row">
                <span className="news-category-badge">{selectedArticle.category}</span>
                {selectedArticle.isSpecial && (
                  <span className="news-special-badge">
                    <Flame size={12} />
                    <span>SPECIAL ANNOUNCEMENT</span>
                  </span>
                )}
              </div>
            </div>

            <div className="news-details-content">
              <div className="news-details-meta">
                <span>Published on {formatDateFull(new Date(`${selectedArticle.date}T00:00:00`))}</span>
                <span>·</span>
                <span>Author: {selectedArticle.authorName} ({selectedArticle.authorEmail})</span>
              </div>

              <h2>{selectedArticle.title}</h2>
              <p className="news-lead-summary">{selectedArticle.summary}</p>

              <div className="news-full-text">
                {selectedArticle.content.split("\n\n").map((para, i) => (
                  <p key={i}>{para}</p>
                ))}
              </div>

              <div className="news-details-footer">
                <button
                  type="button"
                  className="modal-reminder-btn"
                  onClick={() => {
                    setReminderModalNews(selectedArticle);
                    setSelectedArticle(null);
                  }}
                >
                  <Bell size={16} />
                  <span>Set a Reminder for this Notice</span>
                </button>
                <button
                  type="button"
                  className="directory-modal-secondary"
                  onClick={() => setSelectedArticle(null)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Set Reminder with Time Picker */}
      {reminderModalNews && (
        <SetReminderModal
          article={reminderModalNews}
          onClose={() => setReminderModalNews(null)}
          onSave={handleAddReminder}
        />
      )}

      {/* Modal: Admin Post / Edit SCOT News */}
      {newsModalMode && (
        <NewsFormModal
          article={editingArticle}
          onClose={() => {
            setNewsModalMode(null);
            setEditingArticle(null);
          }}
          onSave={handleSaveNews}
        />
      )}

      {/* Modal: Add Calendar Event */}
      {showAddEventModal && (
        <AddEventModal
          initialDate={selectedDate}
          onClose={() => setShowAddEventModal(false)}
          onSave={(newEvent) => {
            setEvents((prev) => [...prev, { ...newEvent, id: `evt-${Date.now()}` }]);
            setShowAddEventModal(false);
            setActiveToast({
              title: "Event Scheduled",
              message: `"${newEvent.title}" was added to your calendar.`,
            });
          }}
        />
      )}
    </div>
  );
}

// Modal Component: Set Reminder
function SetReminderModal({ article, onClose, onSave }) {
  const [title, setTitle] = useState(() => `Follow up: ${article.title}`);
  const [date, setDate] = useState(() => {
    return article.date && article.date >= new Date().toISOString().slice(0, 10)
      ? article.date
      : new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  });
  const [time, setTime] = useState("09:00");
  const [notes, setNotes] = useState("");

  function handleSubmit(e) {
    e.preventDefault();
    onSave({
      title,
      sourceTitle: article.title,
      date,
      time,
      notes,
    });
  }

  return (
    <div
      className="directory-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="directory-modal reminder-modal">
        <button className="directory-modal-close" onClick={onClose}>
          <X size={19} />
        </button>

        <div className="directory-modal-header">
          <div className="reminder-modal-icon">
            <Bell size={24} />
          </div>
          <span className="directory-kicker">PERSONAL REMINDER</span>
          <h2>Set Reminder for Notice</h2>
          <p>Configure date &amp; time to receive an automated notification alert.</p>
        </div>

        <form onSubmit={handleSubmit} className="directory-edit-form">
          <div className="directory-edit-grid">
            <label className="directory-edit-field directory-edit-field-wide">
              Reminder Title *
              <input required value={title} onChange={(e) => setTitle(e.target.value)} />
            </label>

            <label className="directory-edit-field">
              Reminder Date *
              <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
            </label>

            <label className="directory-edit-field">
              Reminder Time *
              <input type="time" required value={time} onChange={(e) => setTime(e.target.value)} />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Additional Notes
              <textarea
                rows={3}
                placeholder="e.g. Bring ID card, complete form online..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
          </div>

          <div className="reminder-notification-info">
            <Bell size={15} />
            <span>
              A notification will fire in the portal at the scheduled time. Browser desktop alerts will also be sent.
            </span>
          </div>

          <div className="directory-modal-actions">
            <button type="button" className="directory-modal-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="directory-modal-save">
              <Check size={16} />
              <span>Schedule Reminder</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// Modal Component: Post / Edit News (Admin)
function NewsFormModal({ article, onClose, onSave }) {
  const isEdit = Boolean(article?.id);
  const [title, setTitle] = useState(() => article?.title ?? "");
  const [summary, setSummary] = useState(() => article?.summary ?? "");
  const [content, setContent] = useState(() => article?.content ?? "");
  const [category, setCategory] = useState(() => article?.category ?? "Events");
  const [isSpecial, setIsSpecial] = useState(() => Boolean(article?.isSpecial));
  const [imageUrl, setImageUrl] = useState(() => article?.imageUrl ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const presetImages = [
    { label: "Graduation / Convocation", url: "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=800&q=80" },
    { label: "Health & Welfare", url: "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80" },
    { label: "Research & Science", url: "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=800&q=80" },
    { label: "Exams & Academics", url: "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?auto=format&fit=crop&w=800&q=80" },
  ];

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      await onSave({
        title,
        summary,
        content: content || summary,
        category,
        isSpecial,
        imageUrl: imageUrl || presetImages[0].url,
      });
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  }

  return (
    <div
      className="directory-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="directory-modal directory-modal-editing directory-modal-add">
        <button className="directory-modal-close" onClick={onClose}>
          <X size={19} />
        </button>

        <div className="directory-modal-header">
          <span className="directory-kicker">HR &amp; ADMINISTRATION</span>
          <h2>{isEdit ? "Edit SCOT News Bulletin" : "Publish SCOT News Bulletin"}</h2>
          <p>Broadcast university announcements to all employees and staff members.</p>
        </div>

        <form onSubmit={submit} className="directory-edit-form">
          <div className="directory-edit-grid">
            <label className="directory-edit-field directory-edit-field-wide">
              Announcement Title *
              <input required value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Annual Staff Health Camp 2026" />
            </label>

            <label className="directory-edit-field">
              Category *
              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="Events">Events</option>
                <option value="Academic">Academic</option>
                <option value="HR & Welfare">HR &amp; Welfare</option>
                <option value="Administration">Administration</option>
                <option value="Urgent Notice">Urgent Notice</option>
              </select>
            </label>

            <div className="directory-edit-field special-toggle-field">
              <span>Special Announcement</span>
              <label className="switch-toggle-label">
                <input
                  type="checkbox"
                  checked={isSpecial}
                  onChange={(e) => setIsSpecial(e.target.checked)}
                />
                <span className="switch-slider" />
                <span className="switch-text">{isSpecial ? "Marked as Special Alert ⭐" : "Normal Bulletin"}</span>
              </label>
            </div>

            <label className="directory-edit-field directory-edit-field-wide">
              Short Summary (Visible on news card) *
              <textarea
                required
                rows={2}
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="2-3 line overview for staff on the dashboard..."
              />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Full Content &amp; Details
              <textarea
                rows={4}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="Full article content, guidelines, instructions, or venue details..."
              />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Card Image URL (Optional)
              <input
                type="url"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                placeholder="https://images.unsplash.com/..."
              />
              <div className="preset-image-chips">
                <span className="preset-label">Quick Presets:</span>
                {presetImages.map((p) => (
                  <button
                    key={p.label}
                    type="button"
                    className="preset-chip"
                    onClick={() => setImageUrl(p.url)}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </label>
          </div>

          {error && <div className="directory-edit-error" role="alert">{error}</div>}

          <div className="directory-modal-actions">
            <button type="button" className="directory-modal-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="directory-modal-save" disabled={saving}>
              <Megaphone size={15} />
              <span>{saving ? "Publishing…" : isEdit ? "Update Bulletin" : "Publish Announcement"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// Modal Component: Add Event
function AddEventModal({ initialDate, onClose, onSave }) {
  const [title, setTitle] = useState("");
  const [date, setDate] = useState(() => initialDate || new Date().toISOString().slice(0, 10));
  const [startTime, setStartTime] = useState("10:00");
  const [endTime, setEndTime] = useState("11:30");
  const [category, setCategory] = useState("Academic");
  const [location, setLocation] = useState("Senate Hall Room A");
  const [meetLink, setMeetLink] = useState("");
  const [description, setDescription] = useState("");
  const [syncToGcal, setSyncToGcal] = useState(true);

  function handleSubmit(e) {
    e.preventDefault();
    const eventObj = {
      title,
      date,
      startTime,
      endTime,
      category,
      location,
      meetLink,
      description,
    };

    if (syncToGcal) {
      const gcalUrl = generateGoogleCalendarUrl({
        title,
        description,
        location,
        startDate: date,
        startTime,
        endTime,
      });
      window.open(gcalUrl, "_blank");
    }

    onSave(eventObj);
  }

  return (
    <div
      className="directory-modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="directory-modal directory-modal-editing">
        <button className="directory-modal-close" onClick={onClose}>
          <X size={19} />
        </button>

        <div className="directory-modal-header">
          <span className="directory-kicker">GOOGLE CALENDAR SCHEDULE</span>
          <h2>Schedule New Event</h2>
          <p>Add to your dashboard schedule and synchronize with Google Calendar.</p>
        </div>

        <form onSubmit={handleSubmit} className="directory-edit-form">
          <div className="directory-edit-grid">
            <label className="directory-edit-field directory-edit-field-wide">
              Event Title *
              <input
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Faculty Board Meeting"
              />
            </label>

            <label className="directory-edit-field">
              Date *
              <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
            </label>

            <label className="directory-edit-field">
              Category
              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="Academic">Academic</option>
                <option value="Meeting">Meeting</option>
                <option value="Admin">Admin</option>
                <option value="HR">HR &amp; Welfare</option>
              </select>
            </label>

            <label className="directory-edit-field">
              Start Time *
              <input
                type="time"
                required
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </label>

            <label className="directory-edit-field">
              End Time *
              <input
                type="time"
                required
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Location / Room
              <input
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="e.g. Senate Hall / Faculty Boardroom"
              />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Google Meet Link (Optional)
              <input
                type="url"
                value={meetLink}
                onChange={(e) => setMeetLink(e.target.value)}
                placeholder="https://meet.google.com/..."
              />
            </label>

            <label className="directory-edit-field directory-edit-field-wide">
              Description / Agenda
              <textarea
                rows={2}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief agenda or instructions for attendees..."
              />
            </label>

            <div className="directory-edit-field directory-edit-field-wide gcal-sync-checkbox">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={syncToGcal}
                  onChange={(e) => setSyncToGcal(e.target.checked)}
                />
                <span>Automatically open in Google Calendar to invite attendees</span>
              </label>
            </div>
          </div>

          <div className="directory-modal-actions">
            <button type="button" className="directory-modal-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="directory-modal-save">
              <CalendarIcon size={15} />
              <span>Save &amp; Sync Event</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
