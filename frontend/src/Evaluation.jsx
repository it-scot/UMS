import { useEffect, useState } from "react";
import {
  Activity,
  CalendarDays,
  Check,
  CheckCircle2,
  Clock3,
  FilePlus2,
  FileText,
  Info,
  LockKeyhole,
  MessageSquareText,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Trash2,
  UsersRound,
} from "lucide-react";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";
const reviewYear = new Date().getFullYear() + (new Date().getMonth() >= 3 ? 1 : 0);

async function evaluationRequest(employee, path, options = {}) {
  const response = await fetch(`${apiBase}/api/evaluations${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${employee.idToken}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
    ...(options.body ? { body: JSON.stringify(options.body) } : {}),
  });
  const responseText = await response.text();
  let body;
  try {
    body = responseText ? JSON.parse(responseText) : {};
  } catch {
    if (response.ok) throw new Error("The server returned an invalid evaluation response.");
    body = {};
  }
  if (!response.ok) throw new Error(body.message ?? `Evaluation request failed (HTTP ${response.status}).`);
  return body;
}

function Message({ children, error = false }) {
  return <div className={`evaluation-message${error ? " evaluation-message-error" : ""}`} role={error ? "alert" : "status"}>{error ? <Info size={16} /> : <CheckCircle2 size={16} />}{children}</div>;
}

function Panel({ title, eyebrow, children, action, className = "" }) {
  return (
    <section className={`evaluation-panel ${className}`}>
      <div className="evaluation-panel-heading">
        <div><span className="evaluation-kicker">{eyebrow}</span><h2>{title}</h2></div>
        {action}
      </div>
      {children}
    </section>
  );
}

function Status({ value }) {
  const complete = value === "complete" || value === "published";
  return <span className={`evaluation-status${complete ? " status-complete" : ""}`}>{complete ? <CheckCircle2 size={14} /> : <Clock3 size={14} />}{value}</span>;
}

const defaultMatrixColumns = ["Needs improvement", "Developing", "Meets expectations", "Strong", "Exceptional"];

function normalizeQuestion(question) {
  if (typeof question === "string") return { type: "rating", prompt: question };
  return {
    type: question?.type === "matrix" ? "matrix" : "rating",
    prompt: question?.prompt ?? "",
    ...(question?.type === "matrix"
      ? {
          rows: Array.isArray(question.rows) && question.rows.length ? [...question.rows] : [""],
          columns: Array.isArray(question.columns) && question.columns.length === 5
            ? [...question.columns]
            : [...defaultMatrixColumns],
        }
      : {}),
  };
}

function HistoryPanel({ history }) {
  return (
    <Panel title="Past performance" eyebrow="YOUR PUBLISHED HISTORY">
      {!history?.length ? <p className="empty-results">No previous published evaluation results are available.</p> : (
        <div className="table-scroll"><table className="employee-results-table"><thead><tr><th>Cycle</th><th>Final mark</th><th>Rating</th></tr></thead><tbody>
          {history.map((item) => <tr key={item.id ?? item.cycleYear}><td>{item.cycleYear}</td><td><strong>{item.finalScore}%</strong></td><td>{item.rating}</td></tr>)}
        </tbody></table></div>
      )}
    </Panel>
  );
}

function AdminDashboard({ employee }) {
  const [year, setYear] = useState(reviewYear);
  const [tab, setTab] = useState("cycle");
  const [dashboard, setDashboard] = useState(null);
  const [results, setResults] = useState(null);
  const [template, setTemplate] = useState(null);
  const [selectedDepartments, setSelectedDepartments] = useState([]);
  const [selectedTemplates, setSelectedTemplates] = useState({});
  const [startDate, setStartDate] = useState(`${year}-02-01`);
  const [dueDate, setDueDate] = useState(`${year}-03-31`);
  const [resultsRoundId, setResultsRoundId] = useState("");
  const [alternatives, setAlternatives] = useState({});
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  async function loadDashboard() {
    setError("");
    try {
      const data = await evaluationRequest(employee, `/admin/dashboard?year=${year}`);
      setDashboard(data);
      const publishedDepartmentIds = [
        ...(data.rounds?.filter((round) => round.status === "published").flatMap((round) => round.departmentIds ?? []) ?? []),
        ...(data.cycle?.departmentIds ?? []),
      ];
      setSelectedDepartments((current) => current.filter((id) => !publishedDepartmentIds.includes(id)));
      setSelectedTemplates((current) => {
        const selected = { ...current };
        for (const type of ["self", "primary", "peer"]) {
          const valid = data.templates.find((template) => template.id === selected[type] && template.type === type);
          selected[type] = valid?.id ?? data.templates.find((template) => template.type === type)?.id ?? "";
        }
        return selected;
      });
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  async function loadResults() {
    setError("");
    try {
      const data = await evaluationRequest(employee, `/admin/results?year=${year}`);
      setResults(data);
      setResultsRoundId((current) => data.rounds?.some((round) => round.id === current)
        ? current
        : data.rounds?.find((round) => round.status === "published")?.id ?? "");
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  useEffect(() => { loadDashboard(); }, [employee.idToken, year]);
  useEffect(() => { loadResults(); }, [employee.idToken, year]);
  useEffect(() => {
    setStartDate(`${year}-02-01`);
    setDueDate(`${year}-03-31`);
  }, [year]);

  async function saveTemplate(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await evaluationRequest(employee, `/admin/templates/${encodeURIComponent(template.id)}`, {
        method: "PUT",
        body: {
          name: template.name,
          type: template.type,
          questions: template.questions.map(normalizeQuestion),
        },
      });
      setNotice("Question template saved to Firestore.");
      setTemplate(null);
      await loadDashboard();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function publishCycle() {
    setBusy(true);
    setError("");
    try {
      await evaluationRequest(employee, "/admin/cycles/publish", {
        method: "POST",
        body: { year, startDate, dueDate, departmentIds: selectedDepartments, templateIds: selectedTemplates },
      });
      setNotice(`The ${year} department round was published and assignments were created.`);
      await loadDashboard();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function publishResults() {
    setBusy(true);
    setError("");
    try {
      await evaluationRequest(employee, `/admin/rounds/${encodeURIComponent(resultsRoundId)}/results/publish`, { method: "POST" });
      setNotice(`Results for this ${year} department round are visible privately to each employee.`);
      await Promise.all([loadDashboard(), loadResults()]);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function approveAlternative(assignment) {
    const alternativeStaffId = alternatives[assignment.id] ?? assignment.suggestedAlternativeStaffId ?? "";
    if (!alternativeStaffId.trim()) {
      setError("Enter the staff ID of the alternative evaluator to approve.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await evaluationRequest(employee, `/admin/assignments/${encodeURIComponent(assignment.id)}/approve-alternative`, {
        method: "POST",
        body: { alternativeStaffId },
      });
      setNotice("The alternative evaluator was assigned. They must confirm familiarity before submitting.");
      await loadResults();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  function newTemplate(type = "self") {
    const id = crypto.randomUUID();
    setTemplate({ id, name: "", type, questions: [{ type: "rating", prompt: "" }] });
    setTab("templates");
    setNotice("");
  }

  function updateTemplateQuestion(index, patch) {
    setTemplate((current) => ({
      ...current,
      questions: current.questions.map((question, questionIndex) => questionIndex === index
        ? { ...normalizeQuestion(question), ...patch }
        : normalizeQuestion(question)),
    }));
  }

  const templates = dashboard?.templates ?? [];
  const rounds = dashboard?.rounds ?? [];
  const resultRounds = results?.rounds ?? [];
  const selectedResultsRound = resultRounds.find((round) => round.id === resultsRoundId);
  const assignments = (results?.assignments ?? []).filter((assignment) =>
    resultsRoundId ? assignment.roundId === resultsRoundId : !assignment.roundId);
  const roundResults = (results?.results ?? []).filter((result) =>
    resultsRoundId ? result.roundId === resultsRoundId : !result.roundId);
  const templatesReady = ["self", "primary", "peer"].every((type) => selectedTemplates[type]);
  const activeAssignments = assignments.filter((assignment) => assignment.status !== "replaced");
  const doneAssignments = activeAssignments.filter((assignment) => assignment.status === "complete").length;
  const resultsReady = activeAssignments.length > 0
    && doneAssignments === activeAssignments.length
    && roundResults.length > 0
    && roundResults.every((result) => result.finalScore != null);
  const resultsPublished = Boolean(selectedResultsRound?.resultsPublished);
  const publishedDepartmentIds = [...new Set([
    ...rounds.filter((round) => round.status === "published").flatMap((round) => round.departmentIds ?? []),
    ...(dashboard?.cycle?.departmentIds ?? []),
  ])];

  return (
    <>
      <section className="evaluation-heading evaluation-hero">
        <div><h1>Staff evaluations</h1><p>Set up department rounds, manage evaluation forms and review results.</p></div>
      </section>
      <nav className="evaluation-tabs" aria-label="Evaluation admin">
        <button className={tab === "cycle" ? "evaluation-tab-active" : ""} onClick={() => setTab("cycle")}><CalendarDays size={15} /> Cycle &amp; progress</button>
        <button className={tab === "templates" ? "evaluation-tab-active" : ""} onClick={() => setTab("templates")}><FileText size={15} /> Evaluation forms <span>{templates.length}</span></button>
        <button className={tab === "results" ? "evaluation-tab-active" : ""} onClick={() => setTab("results")}><Activity size={15} /> Marks &amp; history</button>
      </nav>
      {error && <Message error>{error}</Message>}
      {notice && <Message>{notice}</Message>}

      {tab === "cycle" && (
        <>
          {!dashboard ? <div className="evaluation-loading"><RefreshCw size={17} /> Loading database records…</div> : (
            <>
              <div className="evaluation-summary">
                <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-green"><UsersRound size={18} /></span><span className="evaluation-stat-label">ACTIVE STAFF</span><strong>{dashboard.staffCount}</strong><span className="evaluation-stat-detail">From the employees collection</span></article>
                <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-blue"><CheckCircle2 size={18} /></span><span className="evaluation-stat-label">EVALUATOR FORMS</span><strong>{dashboard.completedAssignments} / {dashboard.assignmentCount}</strong><span className="evaluation-stat-detail">Submitted assignments in {year}</span></article>
                <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-amber"><LockKeyhole size={18} /></span><span className="evaluation-stat-label">DEPARTMENT ROUNDS</span><strong>{rounds.length}</strong><span className="evaluation-stat-detail">Results released per round</span></article>
              </div>
              <div className="evaluation-manager-grid">
                <Panel title="Create a department round">
                  <div className="cycle-fields">
                    <label>Evaluation year<input type="number" min="2020" max="2100" value={year} onChange={(event) => setYear(Number(event.target.value))} /></label>
                    <label>Round opens<input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label>
                    <label>Submissions due<input type="date" value={dueDate} min={startDate} onChange={(event) => setDueDate(event.target.value)} /></label>
                  </div>
                  <div className="cycle-template-selects">
                    {["self", "primary", "peer"].map((type) => {
                      const choices = templates.filter((item) => item.type === type);
                      return <label key={type}>{type === "self" ? "Self-evaluation form" : type === "primary" ? "Primary evaluator form" : "Peer evaluation form"}<select value={selectedTemplates[type] ?? ""} onChange={(event) => setSelectedTemplates((current) => ({ ...current, [type]: event.target.value }))}><option value="">Choose a saved form</option>{choices.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>;
                    })}
                  </div>
                  <div className="department-picker-heading"><strong>Participating departments</strong><span>{selectedDepartments.length} selected</span></div>
                  <div className="department-picker">
                    {dashboard.departments.map((department) => (
                      <label key={department.id} className={selectedDepartments.includes(department.id) ? "department-selected" : ""}>
                        <input type="checkbox" disabled={publishedDepartmentIds.includes(department.id)} checked={selectedDepartments.includes(department.id)} onChange={() => setSelectedDepartments((current) => current.includes(department.id) ? current.filter((id) => id !== department.id) : [...current, department.id])} />
                        <span className="department-check"><Check size={11} /></span>{department.name}
                        {publishedDepartmentIds.includes(department.id) && <small className="department-round-tag">Published</small>}
                      </label>
                    ))}
                    {!dashboard.departments.length && <p>No departments were found in Firestore.</p>}
                  </div>
                  <div className="cycle-setup-footer"><span>Each publication creates a separate department round. Previously published departments cannot be added again this year.</span><button className="evaluation-primary-button" disabled={busy || !selectedDepartments.length || !templatesReady || !startDate || !dueDate || dueDate < startDate} onClick={publishCycle}><Send size={14} /> Publish department round</button></div>
                </Panel>
              </div>
              <Panel title="Published department rounds" eyebrow="ANNUAL CYCLE OVERVIEW" className="published-rounds-panel">
                {!rounds.length ? <p className="empty-results">No rounds have been published for {year}.</p> : (
                  <div className="table-scroll"><table className="employee-results-table"><thead><tr><th>Departments</th><th>Evaluation period</th><th>Progress</th><th>Results</th><th /></tr></thead><tbody>
                    {rounds.map((round) => {
                      const roundAssignments = results?.assignments?.filter((assignment) => assignment.roundId === round.id && assignment.status !== "replaced") ?? [];
                      const complete = roundAssignments.filter((assignment) => assignment.status === "complete").length;
                      const departments = dashboard.departments.filter((department) => round.departmentIds?.includes(department.id)).map((department) => department.name).join(", ");
                      return <tr key={round.id}><td><strong>{departments || "Department"}</strong><small>{round.staffCount ?? 0} staff</small></td><td>{round.startDate} – {round.dueDate}</td><td>{complete} / {roundAssignments.length} forms</td><td><Status value={round.resultsPublished ? "published" : "pending"} /><small>{round.resultsPublished ? "Results released" : "Results private"}</small></td><td><button className="text-button" onClick={() => { setResultsRoundId(round.id); setTab("results"); }}>View round</button></td></tr>;
                    })}
                  </tbody></table></div>
                )}
              </Panel>
            </>
          )}
        </>
      )}

      {tab === "templates" && (
        <Panel
          className="forms-panel"
          title="Evaluation form templates"
          eyebrow="FORMS"
          action={<button className="evaluation-secondary-button" onClick={() => newTemplate()}><FilePlus2 size={15} /> New form</button>}
        >
          <p className="cycle-setup-description">Forms are stored in Firestore. A cycle cannot start until a self, primary evaluator, and peer form have been created.</p>
          {template && (
            <form className="template-editor-form" onSubmit={saveTemplate}>
              <div className="role-controls">
                <label>Form name<input required value={template.name} onChange={(event) => setTemplate({ ...template, name: event.target.value })} placeholder="e.g. Annual self-evaluation" /></label>
                <label>Evaluator type<select value={template.type} onChange={(event) => setTemplate({ ...template, type: event.target.value })}><option value="self">Self-evaluation</option><option value="primary">Primary evaluator</option><option value="peer">Peer evaluation</option></select></label>
              </div>
              <div className="template-question-heading"><div><strong>Questions</strong><span>Add rating questions or a table of criteria with a shared scale.</span></div><div><button type="button" className="evaluation-secondary-button" onClick={() => setTemplate({ ...template, questions: [...template.questions, { type: "rating", prompt: "" }] })}><Plus size={14} /> Rating question</button><button type="button" className="evaluation-secondary-button" onClick={() => setTemplate({ ...template, questions: [...template.questions, { type: "matrix", prompt: "", rows: [""], columns: [...defaultMatrixColumns] }] })}><Plus size={14} /> Rating table</button></div></div>
              {template.questions.map((rawQuestion, index) => {
                const question = normalizeQuestion(rawQuestion);
                return (
                  <section className="template-question-card" key={index}>
                    <div className="template-question-card-heading"><span className="question-number">{String(index + 1).padStart(2, "0")}</span><span>{question.type === "matrix" ? "Rating table" : "Rating question"}</span><button type="button" className="remove-question-button" aria-label={`Remove question ${index + 1}`} disabled={template.questions.length === 1} onClick={() => setTemplate({ ...template, questions: template.questions.filter((_, questionIndex) => questionIndex !== index) })}><Trash2 size={15} /></button></div>
                    <label className="template-prompt-label">Question / instruction<textarea rows="2" required value={question.prompt} onChange={(event) => updateTemplateQuestion(index, { prompt: event.target.value })} placeholder={question.type === "matrix" ? "e.g. Rate the employee in each area below" : "Write a clear evaluation question"} /></label>
                    {question.type === "matrix" && (
                      <div className="matrix-editor">
                        <strong>Criteria rows</strong>
                        {question.rows.map((row, rowIndex) => <div className="matrix-edit-row" key={rowIndex}><input aria-label={`Table criterion ${rowIndex + 1}`} required value={row} placeholder={`Criterion ${rowIndex + 1}`} onChange={(event) => updateTemplateQuestion(index, { rows: question.rows.map((item, itemIndex) => itemIndex === rowIndex ? event.target.value : item) })} /><button type="button" className="remove-question-button" aria-label={`Remove criterion ${rowIndex + 1}`} disabled={question.rows.length === 1} onClick={() => updateTemplateQuestion(index, { rows: question.rows.filter((_, itemIndex) => itemIndex !== rowIndex) })}><Trash2 size={14} /></button></div>)}
                        <button type="button" className="add-question-button" onClick={() => updateTemplateQuestion(index, { rows: [...question.rows, ""] })}><Plus size={14} /> Add table row</button>
                        <details className="matrix-column-settings"><summary>Customize rating labels</summary><div className="matrix-column-fields">{question.columns.map((column, columnIndex) => <label key={columnIndex}>{columnIndex + 1}<input required value={column} onChange={(event) => updateTemplateQuestion(index, { columns: question.columns.map((item, itemIndex) => itemIndex === columnIndex ? event.target.value : item) })} /></label>)}</div></details>
                      </div>
                    )}
                  </section>
                );
              })}
              <div className="template-save-row"><span><LockKeyhole size={14} /> Saved to the configured Firestore database.</span><div><button type="button" className="text-button" onClick={() => setTemplate(null)}>Cancel</button><button className="evaluation-primary-button" disabled={busy}><Check size={14} /> Save form</button></div></div>
            </form>
          )}
          <div className="saved-template-list">
            {templates.map((item) => (
              <button key={item.id} onClick={() => setTemplate({ ...item, questions: (item.questions ?? []).map(normalizeQuestion) })}>
                <FileText size={17} /><span><strong>{item.name}</strong><small>{item.type} · {item.questions?.length ?? 0} questions · {(item.questions ?? []).filter((question) => normalizeQuestion(question).type === "matrix").length} tables</small></span><span className="text-button">Edit</span>
              </button>
            ))}
            {!templates.length && !template && <div className="evaluation-empty-state compact-empty"><span><FileText size={23} /></span><h2>No form templates</h2><p>Create the self, primary and peer forms for your evaluation rounds.</p><div className="empty-template-actions">{["self", "primary", "peer"].map((type) => <button key={type} className="evaluation-secondary-button" onClick={() => { setTemplate({ id: type, name: "", type, questions: [{ type: "rating", prompt: "" }] }); setNotice(""); }}><Plus size={13} /> Create {type} form</button>)}</div></div>}
          </div>
        </Panel>
      )}

      {tab === "results" && (
        <div className="results-dashboard marks-dashboard">
          <div className="results-intro"><div><span className="evaluation-kicker">CONFIDENTIAL ADMIN VIEW</span><h2>Evaluation assignments and marks · {year}</h2><p>Only verified HR and IT administrators can access peer identities and individual results.</p></div><button className="evaluation-secondary-button" onClick={loadResults}><RefreshCw size={14} /> Refresh</button></div>
          <label className="round-results-picker">Department round
            <select value={resultsRoundId} onChange={(event) => setResultsRoundId(event.target.value)}>
              <option value="">Select a published round</option>
              {resultRounds.filter((round) => round.status === "published").map((round) => {
                const departmentNames = dashboard?.departments?.filter((department) => round.departmentIds?.includes(department.id)).map((department) => department.name).join(", ");
                return <option key={round.id} value={round.id}>{departmentNames || "Department round"} · {round.startDate} – {round.dueDate}</option>;
              })}
            </select>
          </label>
          {resultsPublished && <div className="cycle-live-banner"><CheckCircle2 size={15} /><span>Results published. Employees can access only their own final mark.</span></div>}
          {!results ? <div className="evaluation-loading"><RefreshCw size={17} /> Loading confidential results…</div> : (
            <>
              <Panel className="staff-results-panel" title="Staff results" eyebrow="INDIVIDUAL PERFORMANCE">
                {!resultsRoundId ? <p className="empty-results">Choose a published department round to view its marks and assignments.</p> : !roundResults.length ? <p className="empty-results">No staff results have been calculated for this round yet.</p> : (
                  <div className="table-scroll"><table className="employee-results-table"><thead><tr><th>Employee</th><th>Department</th><th>Position</th><th>Self</th><th>Primary</th><th>Peer average</th><th>Final mark</th><th>Rating</th></tr></thead><tbody>
                    {roundResults.map((result) => <tr key={result.id}><td><strong>{result.name}</strong><small>{result.staffId}</small></td><td>{result.department}</td><td>{result.designation}</td><td>{result.selfScore == null ? "—" : `${Math.round(result.selfScore)}%`}</td><td>{result.primaryScore == null ? "—" : `${Math.round(result.primaryScore)}%`}</td><td>{result.peerAverage == null ? "—" : `${Math.round(result.peerAverage)}%`}<small className="score-change">{result.peerCount ?? 0} of {result.peerRequiredCount ?? result.peerCount ?? 0} anonymous responses</small><small className="score-change">{result.managerPeerCount ? `Other managers: ${result.managerPeerCount}` : `Same level: ${result.samePeerCount ?? "—"} · ${/head of department|\bhod\b/i.test(result.designation ?? "") ? "Subordinate level" : "One level below"}: ${result.lowerPeerCount === 0 ? "Nil" : result.lowerPeerCount ?? "—"}`}</small></td><td><strong>{result.finalScore == null ? "Pending" : `${result.finalScore}%`}</strong><small className="score-change">{(result.history ?? []).map((entry) => `${entry.cycleYear}: ${entry.finalScore}%`).join(" · ") || "No earlier published marks"}</small></td><td>{result.rating ?? "Pending"}</td></tr>)}
                  </tbody></table></div>
                )}
              </Panel>
              <Panel className="assignment-results-panel" title="Evaluator assignments" eyebrow="EVALUATOR ASSIGNMENTS">
                {!resultsRoundId || !assignments.length ? <p className="empty-results">No evaluator assignments are available for this department round.</p> : (
                  <div className="table-scroll"><table className="employee-results-table"><thead><tr><th>Employee being evaluated</th><th>Evaluator</th><th>Evaluation level</th><th>Status</th><th>Privacy</th><th>Declined peer replacement</th></tr></thead><tbody>
                    {assignments.map((assignment) => <tr key={assignment.id}><td>{assignment.targetName}</td><td>{assignment.evaluatorName}</td><td>{assignment.kind === "peer" ? `Peer · ${assignment.peerGroup}` : assignment.kind}</td><td><Status value={assignment.status} /></td><td>{assignment.anonymous ? "Anonymous to employee" : "Named"}</td><td>{assignment.status === "declined" ? <div className="replacement-action"><input aria-label="Alternative evaluator staff ID" placeholder="Alternative staff ID" value={alternatives[assignment.id] ?? assignment.suggestedAlternativeStaffId ?? ""} onChange={(event) => setAlternatives((current) => ({ ...current, [assignment.id]: event.target.value }))} /><button className="evaluation-secondary-button" disabled={busy} onClick={() => approveAlternative(assignment)}>Approve &amp; assign</button></div> : assignment.status === "replaced" ? "Replacement assigned" : "—"}</td></tr>)}
                  </tbody></table></div>
                )}
              </Panel>
              <div className="results-release-action"><span><LockKeyhole size={15} /> Release final marks privately to each employee only after all assigned forms are complete.</span><button className="evaluation-primary-button" disabled={busy || !resultsRoundId || !resultsReady || resultsPublished} onClick={publishResults}><Send size={14} /> {resultsPublished ? "Results published" : resultsReady ? "Publish round results" : `Awaiting submissions (${doneAssignments}/${activeAssignments.length})`}</button></div>
            </>
          )}
        </div>
      )}
    </>
  );
}

function EmployeeEvaluation({ employee }) {
  const [data, setData] = useState(null);
  const [answers, setAnswers] = useState({});
  const [comments, setComments] = useState({});
  const [decline, setDecline] = useState({});
  const [resultAgreement, setResultAgreement] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");

  async function load() {
    setError("");
    try {
      setData(await evaluationRequest(employee, "/me"));
    } catch (requestError) {
      setError(requestError.message);
    }
  }
  useEffect(() => { load(); }, [employee.idToken]);

  async function submitResponse(assignment) {
    const scores = answers[assignment.id] ?? {};
    const requiredScores = (assignment.questions ?? []).flatMap((rawQuestion, questionIndex) => {
      const question = normalizeQuestion(rawQuestion);
      return question.type === "matrix"
        ? question.rows.map((_, rowIndex) => `${questionIndex}.${rowIndex}`)
        : [String(questionIndex)];
    });
    if (!requiredScores.every((key) => scores[key] != null)) {
      setError("Please complete every rating before submitting.");
      return;
    }
    setBusyId(assignment.id);
    setError("");
    try {
      await evaluationRequest(employee, `/assignments/${encodeURIComponent(assignment.id)}/responses`, {
        method: "POST",
        body: { scores, comment: comments[assignment.id] ?? "" },
      });
      setMessage("Your evaluation was submitted.");
      await load();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusyId("");
    }
  }

  async function confirmPeer(assignment, familiar) {
    const response = decline[assignment.id] ?? {};
    if (!familiar && !response.reason?.trim()) {
      setError("Please enter a reason before declining.");
      return;
    }
    try {
      await evaluationRequest(employee, `/assignments/${encodeURIComponent(assignment.id)}/confirmation`, {
        method: "POST",
        body: { familiar, reason: response.reason, alternativeStaffId: response.alternativeStaffId },
      });
      setMessage(familiar ? "You confirmed that you can provide a fair review." : "Your decline and suggestion were sent to HR for approval.");
      await load();
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  const assignments = data?.assignments ?? [];
  const cycle = data?.cycle ?? {};
  const result = data?.result ?? {};

  return (
    <>
      <section className="evaluation-heading"><div><span className="evaluation-eyebrow"><UsersRound size={14} /> MY STAFF EVALUATIONS</span><h1>My Evaluation</h1><p>Complete assigned forms and review your own published result.</p></div><button className="evaluation-secondary-button" onClick={load}><RefreshCw size={14} /> Refresh</button></section>
      <div className="employee-private-banner"><LockKeyhole size={17} /><span><strong>Your marks are private.</strong> Only you and authorised HR / IT administrators can view your individual result. Peer evaluator identities are hidden from employees.</span></div>
      {data?.ownPeerGroups && <div className="evaluation-preview-note"><UsersRound size={16} /><span>{data.ownPeerGroups.manager != null ? `Your anonymous manager peer pool: ${data.ownPeerGroups.manager} other department managers.` : `Your anonymous peer pool: same level ${data.ownPeerGroups.same ?? 0}; ${data.ownPeerGroups.lowerLabel ?? "one level below"} ${data.ownPeerGroups.lowerStatus ?? "Nil"}.`}</span></div>}
      {error && <Message error>{error}</Message>}
      {message && <Message>{message}</Message>}
      {!data ? <div className="evaluation-loading"><RefreshCw size={17} /> Loading your assigned forms…</div> : !cycle.status ? (
        <>
          <div className="evaluation-empty-state"><span><CalendarDays size={24} /></span><h2>No published evaluation cycle</h2><p>Your forms will appear here after HR publishes the annual cycle for your department.</p></div>
          <HistoryPanel history={data.history} />
        </>
      ) : (
        <>
          <div className="evaluation-summary">
            <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-green"><CalendarDays size={18} /></span><span className="evaluation-stat-label">REVIEW CYCLE</span><strong>{cycle.year}</strong><span className="evaluation-stat-detail">{cycle.startDate} to {cycle.dueDate}</span></article>
            <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-blue"><FileText size={18} /></span><span className="evaluation-stat-label">YOUR ASSIGNED FORMS</span><strong>{assignments.filter((item) => item.status === "complete").length} / {assignments.length}</strong><span className="evaluation-stat-detail">Includes your self-review and colleague reviews</span></article>
            <article className="evaluation-stat"><span className="evaluation-stat-icon stat-icon-amber"><LockKeyhole size={18} /></span><span className="evaluation-stat-label">YOUR FINAL RESULT</span><strong>{result.finalScore == null ? "Not published" : `${result.finalScore}%`}</strong><span className="evaluation-stat-detail">{result.rating ?? "Visible only after HR publishes"}</span></article>
          </div>
          {!assignments.length && <div className="evaluation-empty-state compact-empty"><span><FileText size={22} /></span><h2>No assigned forms yet</h2><p>Assignments will be generated from your department and supervisor records.</p></div>}
          <div className="employee-forms-list">
            {assignments.map((assignment) => (
              <Panel key={assignment.id} title={assignment.kind === "self" ? "Self-evaluation" : assignment.kind === "primary" ? "Primary evaluator form" : "Peer evaluation"} eyebrow={assignment.kind === "peer" ? `ANONYMOUS TO EMPLOYEE · ${assignment.peerGroup ?? "PEER"}` : `${assignment.kind.toUpperCase()} EVALUATION`} action={<Status value={assignment.status} />}>
                {assignment.status === "pending" && assignment.startDate && assignment.dueDate && (new Date().toISOString().slice(0, 10) < assignment.startDate || new Date().toISOString().slice(0, 10) > assignment.dueDate) && <div className="evaluation-window-note"><Clock3 size={14} /> Submission window: {assignment.startDate} to {assignment.dueDate}</div>}
                {assignment.kind === "peer" && <div className="anonymous-note"><ShieldCheck size={14} /><span>Your identity is hidden from the employee being evaluated. HR and IT can see evaluator identities.</span></div>}
                {assignment.kind === "peer" && !assignment.familiarityConfirmed && assignment.status !== "declined" && (
                  <div className="familiarity-box"><strong>Are you sufficiently familiar with {assignment.targetName} to provide a fair evaluation?</strong><div className="invitation-actions"><button className="evaluation-primary-button" onClick={() => confirmPeer(assignment, true)}><Check size={14} /> Yes, I can evaluate</button><button className="decline-button" onClick={() => setDecline((current) => ({ ...current, [assignment.id]: { ...current[assignment.id], open: true } }))}>Decline / suggest an alternative</button></div>
                    {decline[assignment.id]?.open && <div className="decline-form"><label>Reason for declining<input required value={decline[assignment.id]?.reason ?? ""} onChange={(event) => setDecline((current) => ({ ...current, [assignment.id]: { ...current[assignment.id], reason: event.target.value } }))} /></label><label>Suggested alternative staff ID · {assignment.peerGroup} peer group<input value={decline[assignment.id]?.alternativeStaffId ?? ""} onChange={(event) => setDecline((current) => ({ ...current, [assignment.id]: { ...current[assignment.id], alternativeStaffId: event.target.value } }))} /></label><button className="evaluation-secondary-button" onClick={() => confirmPeer(assignment, false)}>Send to HR for approval</button></div>}
                  </div>
                )}
                {assignment.status === "declined" ? <div className="evaluation-message"><Clock3 size={15} /> Declined; awaiting an HR-approved replacement.</div> : assignment.status === "complete" ? <div className="evaluation-message"><CheckCircle2 size={15} /> Submitted. Thank you for completing this evaluation.</div> : (
                  <>
                    {assignment.kind === "primary" && <p className="cycle-setup-description">Employee: {assignment.targetName}. Primary evaluation contributes 75% of the final mark.</p>}
                    {assignment.kind === "peer" && assignment.familiarityConfirmed && <p className="cycle-setup-description">Employee: {assignment.targetName}. Your answers are anonymous to this employee.</p>}
                    {(assignment.kind !== "peer" || assignment.familiarityConfirmed) && (
                      <form className="employee-question-form" onSubmit={(event) => { event.preventDefault(); submitResponse(assignment); }}>
                        {assignment.questions.map((rawQuestion, index) => {
                          const question = normalizeQuestion(rawQuestion);
                          const setScore = (key, value) => setAnswers((current) => ({
                            ...current,
                            [assignment.id]: { ...current[assignment.id], [key]: value },
                          }));
                          return question.type === "matrix" ? (
                            <fieldset className="employee-matrix-question" key={index}>
                              <legend>{question.prompt}</legend>
                              <div className="employee-matrix-scroll"><table><thead><tr><th>Criteria</th>{question.columns.map((column, columnIndex) => <th key={columnIndex}>{column}</th>)}</tr></thead><tbody>
                                {question.rows.map((row, rowIndex) => <tr key={rowIndex}><th scope="row">{row}</th>{question.columns.map((column, columnIndex) => <td key={columnIndex}><label aria-label={`${row}: ${column}`}><input type="radio" name={`${assignment.id}-${index}-${rowIndex}`} value={columnIndex + 1} checked={answers[assignment.id]?.[`${index}.${rowIndex}`] === columnIndex + 1} onChange={() => setScore(`${index}.${rowIndex}`, columnIndex + 1)} /><span className="sr-only">{column}</span></label></td>)}</tr>)}
                              </tbody></table></div>
                            </fieldset>
                          ) : (
                            <label key={index}><strong>{question.prompt}</strong><select aria-label={`Rating for question ${index + 1}`} required value={answers[assignment.id]?.[index] ?? ""} onChange={(event) => setScore(String(index), Number(event.target.value))}><option value="" disabled>Select a rating</option>{question.columns?.map((column, columnIndex) => <option key={columnIndex} value={columnIndex + 1}>{columnIndex + 1} · {column}</option>) ?? defaultMatrixColumns.map((column, columnIndex) => <option key={columnIndex} value={columnIndex + 1}>{columnIndex + 1} · {column}</option>)}</select></label>
                          );
                        })}
                        <label><strong>Comments</strong><textarea rows="3" value={comments[assignment.id] ?? ""} onChange={(event) => setComments((current) => ({ ...current, [assignment.id]: event.target.value }))} placeholder="Optional feedback…" /></label>
                        <div className="employee-form-footer"><span><LockKeyhole size={13} /> Marking is applied using the approved 75% / 5% / 20% weighting.</span><button className="evaluation-primary-button" disabled={busyId === assignment.id || (assignment.startDate && assignment.dueDate && (new Date().toISOString().slice(0, 10) < assignment.startDate || new Date().toISOString().slice(0, 10) > assignment.dueDate))}><Send size={14} /> Submit evaluation</button></div>
                      </form>
                    )}
                  </>
                )}
              </Panel>
            ))}
          </div>
          {result.finalScore != null && (
            <Panel title="Your published result" eyebrow="PRIVATE TO YOUR ACCOUNT" action={<span className="own-result-lock"><LockKeyhole size={14} /> Private</span>}>
              <div className="own-score-display"><strong>{result.finalScore}<span>%</span></strong><span className="own-score-rating">{result.rating}</span><small>Published for the {cycle.year} review cycle.</small></div>
              <div className="acknowledgement-box"><strong>Acknowledge or respond</strong><p>You may acknowledge the result or submit a disagreement with a reason. Your response is sent to HR.</p>{result.acknowledgedAt ? <div className="evaluation-message"><CheckCircle2 size={15} /> Response recorded: {result.employeeAgrees ? "agreed" : "disagreed"}{result.employeeComment ? ` — ${result.employeeComment}` : ""}</div> : <><label className="result-agreement-choice"><input type="radio" name="result-agreement" value="agree" checked={resultAgreement === "agree"} onChange={() => setResultAgreement("agree")} /> I acknowledge and agree with this result</label><label className="result-agreement-choice"><input type="radio" name="result-agreement" value="disagree" checked={resultAgreement === "disagree"} onChange={() => setResultAgreement("disagree")} /> I disagree and want to provide a reason</label>{resultAgreement === "disagree" && <textarea rows="3" value={comments.result ?? ""} onChange={(event) => setComments((current) => ({ ...current, result: event.target.value }))} placeholder="Explain why you disagree…" />}<button className="evaluation-primary-button" disabled={!resultAgreement || (resultAgreement === "disagree" && !comments.result?.trim())} onClick={async () => {
                try {
                  await evaluationRequest(employee, `/results/${cycle.year}/acknowledgement`, { method: "POST", body: { agree: resultAgreement === "agree", comment: comments.result ?? "" } });
                  setMessage(resultAgreement === "agree" ? "Your acknowledgement was sent to HR." : "Your disagreement was sent to HR.");
                  await load();
                } catch (requestError) { setError(requestError.message); }
              }}><MessageSquareText size={14} /> Send response</button></>}</div>
            </Panel>
          )}
          <HistoryPanel history={data.history} />
        </>
      )}
    </>
  );
}

export default function Evaluation({ employee }) {
  const isAdmin = Boolean(employee.evaluationAdmin);
  return (
    <div className="page-content evaluation-page">
      {isAdmin ? <AdminDashboard employee={employee} /> : <EmployeeEvaluation employee={employee} />}
    </div>
  );
}
