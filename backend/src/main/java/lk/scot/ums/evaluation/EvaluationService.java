package lk.scot.ums.evaluation;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private static final String CYCLES = "evaluationCycles";
    private static final String TEMPLATES = "evaluationTemplates";
    private static final String ASSIGNMENTS = "evaluationAssignments";
    private static final String RESPONSES = "evaluationResponses";
    private static final String RESULTS = "evaluationResults";
    private static final String ROUNDS = "evaluationRounds";

    private final Firestore firestore;
    private final VerifiedSessionService sessions;

    public EvaluationService(Firestore firestore, VerifiedSessionService sessions) {
        this.firestore = firestore;
        this.sessions = sessions;
    }

    public Map<String, Object> confirmPeerAssignment(
            String token,
            String assignmentId,
            Map<String, Object> request
    ) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId();
        if (staffId.isBlank()) throw new EvaluationForbiddenException();
        try {
            DocumentReference assignmentRef = firestore.collection(ASSIGNMENTS).document(assignmentId);
            DocumentSnapshot assignment = assignmentRef.get().get();
            if (!assignment.exists()
                    || !staffId.equals(assignment.getString("evaluatorStaffId"))
                    || !"peer".equals(assignment.getString("kind"))
                    || !"pending".equals(assignment.getString("status"))) {
                throw new EvaluationForbiddenException();
            }
            boolean familiar = Boolean.TRUE.equals(request.get("familiar"));
            Map<String, Object> updates = new HashMap<>();
            updates.put("familiarityConfirmed", familiar);
            updates.put("confirmationAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
            if (!familiar) {
                String reason = text(request.get("reason")).trim();
                if (reason.isBlank()) {
                    throw new EvaluationRequestException("A reason is required when declining a peer evaluation.");
                }
                updates.put("status", "declined");
                updates.put("declineReason", reason);
                String alternative = text(request.get("alternativeStaffId")).trim();
                if (!alternative.isBlank()) updates.put("suggestedAlternativeStaffId", alternative);
            }
            assignmentRef.update(updates).get();
            return Map.of("assignmentId", assignmentId, "familiar", familiar, "status", familiar ? "pending" : "declined");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> approveAlternative(
            String token,
            String assignmentId,
            Map<String, Object> request
    ) {
        requireAdmin(token);
        String alternativeId = text(request.get("alternativeStaffId")).trim();
        if (alternativeId.isBlank()) throw new EvaluationRequestException("Choose an alternative evaluator to approve.");
        try {
            DocumentReference declinedRef = firestore.collection(ASSIGNMENTS).document(assignmentId);
            DocumentSnapshot declined = declinedRef.get().get();
            if (!declined.exists() || !"peer".equals(declined.getString("kind"))
                    || !"declined".equals(declined.getString("status"))) {
                throw new EvaluationConflictException("Only a declined peer assignment can be reassigned.");
            }
            int year = declined.getLong("cycleYear").intValue();
            Staff target = findStaff(declined.getString("targetStaffId"));
            Staff alternative = findStaff(alternativeId);
            if (target == null || alternative == null) throw new EvaluationRequestException("The target or suggested staff record was not found.");
            if (!isValidAlternative(target, alternative, declined.getString("peerGroup"))) {
                throw new EvaluationRequestException("The suggested evaluator does not meet the required department and level.");
            }
            DocumentSnapshot cycle = firestore.collection(CYCLES).document(Integer.toString(year)).get().get();
            List<?> includedDepartments = cycle.get("departmentIds") instanceof List<?> values ? values : List.of();
            boolean crossDepartmentPeer = "manager".equals(declined.getString("peerGroup"))
                    || (isHeadOfDepartment(target.designation()) && "same".equals(declined.getString("peerGroup")));
            if (!crossDepartmentPeer && !includedDepartments.contains(alternative.departmentId())) {
                throw new EvaluationRequestException("The alternative evaluator is outside this cycle's selected departments.");
            }
            if (!activeEmployees().stream().anyMatch(person -> person.id().equals(alternative.id()))) {
                throw new EvaluationRequestException("The alternative evaluator must be an active employee.");
            }
            List<QueryDocumentSnapshot> existing = firestore.collection(ASSIGNMENTS)
                    .whereEqualTo("cycleYear", year)
                    .whereEqualTo("targetStaffId", target.id())
                    .whereEqualTo("evaluatorStaffId", alternative.id())
                    .get().get().getDocuments();
            if (!existing.isEmpty()) throw new EvaluationConflictException("This staff member already has an assignment for the employee.");

            DocumentSnapshot template = firestore.collection(TEMPLATES)
                    .document(declined.getString("templateId")).get().get();
            List<Map<String, Object>> replacement = new ArrayList<>();
            addAssignment(
                    replacement, year, target, alternative, "peer",
                    declined.getString("peerGroup"), true, template
            );
            Map<String, Object> next = replacement.get(0);
            next.put("replacementFor", assignmentId);
            if (declined.getString("roundId") != null) {
                next.put("roundId", declined.getString("roundId"));
                next.put("startDate", declined.getString("startDate"));
                next.put("dueDate", declined.getString("dueDate"));
            }
            var batch = firestore.batch();
            batch.set(firestore.collection(ASSIGNMENTS).document(text(next.get("id"))), next);
            batch.update(
                    declinedRef,
                    "status", "replaced",
                    "replacementAssignmentId", next.get("id"),
                    "reassignedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
            );
            batch.commit().get();
            return Map.of("assignmentId", next.get("id"), "status", "pending");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> acknowledgeResult(
            String token,
            int year,
            Map<String, Object> request
    ) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId();
        if (staffId.isBlank()) throw new EvaluationForbiddenException();
        try {
            DocumentReference resultRef = firestore.collection(RESULTS).document(resultId(year, staffId));
            DocumentSnapshot result = resultRef.get().get();
            if (!result.exists() || !Boolean.TRUE.equals(result.getBoolean("published"))) {
                throw new EvaluationConflictException("Your result has not been published.");
            }
            if (result.get("acknowledgedAt") != null) {
                throw new EvaluationConflictException("You have already responded to this result.");
            }
            if (!(request.get("agree") instanceof Boolean agree)) {
                throw new EvaluationRequestException("Choose whether you agree with the result.");
            }
            String comment = text(request.get("comment")).trim();
            if (!agree && comment.isBlank()) {
                throw new EvaluationRequestException("A reason is required when disagreeing with the result.");
            }
            resultRef.update(
                    "employeeComment", comment,
                    "employeeAgrees", agree,
                    "acknowledgedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
            ).get();
            return Map.of("year", year, "status", "submitted");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public VerifiedSession requireAdmin(String token) {
        VerifiedSession session = sessions.verify(token);
        if (!session.evaluationAdmin()) {
            throw new EvaluationForbiddenException();
        }
        return session;
    }

    public Map<String, Object> dashboard(String token, int year) {
        requireAdmin(token);
        try {
            DocumentSnapshot cycle = firestore.collection(CYCLES).document(Integer.toString(year)).get().get();
            List<Map<String, Object>> departments = new ArrayList<>();
            for (QueryDocumentSnapshot document : firestore.collection("departments").get().get().getDocuments()) {
                departments.add(Map.of(
                        "id", document.getId(),
                        "name", firstText(document, "departmentName", "name", "department")
                ));
            }
            List<Map<String, Object>> templates = readTemplates();
            List<Map<String, Object>> assignments = readAssignmentsForYear(year);
            assignments.removeIf(item -> "replaced".equals(item.get("status")));
            List<Map<String, Object>> rounds = new ArrayList<>();
            for (QueryDocumentSnapshot round : firestore.collection(ROUNDS)
                    .whereEqualTo("cycleYear", year).get().get().getDocuments()) {
                Map<String, Object> row = data(round);
                row.put("id", round.getId());
                rounds.add(row);
            }
            rounds.sort(Comparator.comparing(item -> text(item.get("startDate"))));
            Map<String, Object> cycleData = cycle.exists() ? data(cycle) : Map.of();
            return Map.of(
                    "year", year,
                    "cycle", cycleData,
                    "departments", departments,
                    "templates", templates,
                    "rounds", rounds,
                    "staffCount", activeEmployees().size(),
                    "assignmentCount", assignments.size(),
                    "completedAssignments", assignments.stream()
                            .filter(item -> "complete".equals(item.get("status")))
                            .count(),
                    "results", readResults(year)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public List<Map<String, Object>> templates(String token) {
        requireAdmin(token);
        try {
            return readTemplates();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> saveTemplate(
            String token,
            String templateId,
            Map<String, Object> request
    ) {
        requireAdmin(token);
        Object rawQuestions = request.get("questions");
        if (!(rawQuestions instanceof List<?> questions) || questions.isEmpty()) {
            throw new EvaluationRequestException("Add at least one question to the form.");
        }
        validateTemplateQuestions(questions);
        String type = text(request.get("type"));
        if (!Set.of("self", "primary", "peer").contains(type)) {
            throw new EvaluationRequestException("Template type must be self, primary, or peer.");
        }
        Map<String, Object> template = Map.of(
                "name", requiredText(request, "name"),
                "type", type,
                "questions", questions,
                "updatedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
        );
        try {
            firestore.collection(TEMPLATES).document(templateId).set(template).get();
            return Map.of("id", templateId, "name", template.get("name"), "type", type, "questions", questions);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> publishCycle(String token, Map<String, Object> request) {
        VerifiedSession admin = requireAdmin(token);
        int year = integer(request.get("year"), "year");
        LocalDate startDate = date(request.get("startDate"), "startDate");
        LocalDate dueDate = date(request.get("dueDate"), "dueDate");
        if (dueDate.isBefore(startDate)) {
            throw new EvaluationRequestException("The submission deadline must be on or after the opening date.");
        }
        Object rawDepartments = request.get("departmentIds");
        if (!(rawDepartments instanceof List<?> departmentValues) || departmentValues.isEmpty()) {
            throw new EvaluationRequestException("Choose at least one department.");
        }
        Set<String> departmentIds = new HashSet<>();
        for (Object value : departmentValues) {
            String departmentId = text(value).trim();
            if (!departmentId.isEmpty()) departmentIds.add(departmentId);
        }
        if (departmentIds.isEmpty()) throw new EvaluationRequestException("Choose at least one department.");

        try {
            DocumentReference cycleRef = firestore.collection(CYCLES).document(Integer.toString(year));
            DocumentSnapshot existingCycle = cycleRef.get().get();
            Map<String, DocumentSnapshot> templateByType = readSelectedTemplates(request.get("templateIds"));
            List<QueryDocumentSnapshot> previousAssignments = firestore.collection(ASSIGNMENTS)
                    .whereEqualTo("cycleYear", year).get().get().getDocuments();
            Set<String> departmentsAlreadyAssigned = new HashSet<>();
            for (QueryDocumentSnapshot assignment : previousAssignments) {
                if (!"replaced".equals(assignment.getString("status"))) {
                    departmentsAlreadyAssigned.add(assignment.getString("departmentId"));
                }
            }
            Set<String> duplicateDepartments = new HashSet<>(departmentIds);
            duplicateDepartments.retainAll(departmentsAlreadyAssigned);
            if (!duplicateDepartments.isEmpty()) {
                throw new EvaluationConflictException(
                        "A round has already been published for one or more selected departments in " + year + "."
                );
            }

            List<Staff> allActiveStaff = activeEmployees();
            List<Staff> staff = allActiveStaff.stream()
                    .filter(person -> departmentIds.contains(person.departmentId()))
                    .toList();
            if (staff.isEmpty()) {
                throw new EvaluationRequestException("No active employees were found in the selected departments.");
            }
            Map<String, Staff> staffById = new HashMap<>();
            allActiveStaff.forEach(person -> staffById.put(person.id(), person));
            for (Staff person : staff) {
                if (person.supervisorId().isBlank() || !staffById.containsKey(person.supervisorId())) {
                    throw new EvaluationRequestException(
                            "Cannot assign the primary evaluator for " + person.name()
                                    + " (" + person.id() + "). Check their supervisor record."
                    );
                }
            }

            List<Map<String, Object>> assignments = planAssignments(year, staff, allActiveStaff, templateByType);
            if (assignments.isEmpty()) throw new EvaluationRequestException("No evaluation assignments could be created.");
            String roundId = UUID.randomUUID().toString();
            assignments.forEach(assignment -> {
                assignment.put("roundId", roundId);
                assignment.put("startDate", startDate.toString());
                assignment.put("dueDate", dueDate.toString());
            });
            Map<String, Object> round = new HashMap<>();
            round.put("cycleYear", year);
            round.put("status", "publishing");
            round.put("departmentIds", new ArrayList<>(departmentIds));
            round.put("templateIds", Map.of(
                    "self", templateByType.get("self").getId(),
                    "primary", templateByType.get("primary").getId(),
                    "peer", templateByType.get("peer").getId()
            ));
            round.put("startDate", startDate.toString());
            round.put("dueDate", dueDate.toString());
            round.put("publishedBy", admin.employee().workEmail());
            round.put("staffCount", staff.size());
            round.put("createdAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
            firestore.collection(ROUNDS).document(roundId).set(round).get();
            writeAssignments(assignments);
            firestore.collection(ROUNDS).document(roundId).update(
                    "status", "published",
                    "publishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
            ).get();
            Set<String> allDepartments = new HashSet<>(departmentIds);
            if (existingCycle.get("departmentIds") instanceof List<?> existingDepartments) {
                existingDepartments.stream().map(EvaluationService::text).forEach(allDepartments::add);
            }
            cycleRef.set(Map.of(
                    "year", year,
                    "status", "published",
                    "departmentIds", new ArrayList<>(allDepartments),
                    "updatedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
            ), SetOptions.merge()).get();
            return Map.of("year", year, "roundId", roundId, "status", "published",
                    "staffCount", staff.size(), "assignmentCount", assignments.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> employeeView(String token) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId();
        if (staffId.isBlank()) throw new EvaluationForbiddenException();
        try {
            List<Map<String, Object>> history = readPublishedHistory(staffId);
            Staff employee = findStaff(staffId);
            if (employee == null || employee.departmentId().isBlank()) {
                return Map.of("cycle", Map.of(), "assignments", List.of(), "result", Map.of(), "history", history);
            }
            List<QueryDocumentSnapshot> employeeRounds = findRoundsForDepartment(employee.departmentId());
            DocumentSnapshot employeeRound = chooseRound(employeeRounds);
            if (employeeRound == null) {
                int currentYear = LocalDate.now().getYear();
                for (int candidate : List.of(currentYear, currentYear + 1, currentYear - 1)) {
                    List<QueryDocumentSnapshot> candidateRounds = firestore.collection(ROUNDS)
                            .whereEqualTo("cycleYear", candidate).get().get().getDocuments().stream()
                            .filter(round -> "published".equals(round.getString("status")))
                            .toList();
                    Set<String> candidateRoundIds = new HashSet<>();
                    candidateRounds.forEach(round -> candidateRoundIds.add(round.getId()));
                    List<QueryDocumentSnapshot> assignedRounds = new ArrayList<>();
                    for (QueryDocumentSnapshot assignment : firestore.collection(ASSIGNMENTS)
                            .whereEqualTo("cycleYear", candidate)
                            .whereEqualTo("evaluatorStaffId", staffId)
                            .get().get().getDocuments()) {
                        String roundId = assignment.getString("roundId");
                        if (roundId != null && candidateRoundIds.contains(roundId)) {
                            candidateRounds.stream().filter(round -> round.getId().equals(roundId))
                                    .findFirst().ifPresent(assignedRounds::add);
                        }
                    }
                    employeeRound = chooseRound(assignedRounds);
                    if (employeeRound != null) break;
                }
            }
            if (employeeRound == null) {
                int currentYear = LocalDate.now().getYear();
                for (int candidate : List.of(currentYear, currentYear + 1, currentYear - 1)) {
                    DocumentSnapshot legacyCycle = firestore.collection(CYCLES)
                            .document(Integer.toString(candidate)).get().get();
                    if (!legacyCycle.exists() || !"published".equals(legacyCycle.getString("status"))) continue;
                    boolean departmentParticipates = legacyCycle.get("departmentIds") instanceof List<?> ids
                            && ids.contains(employee.departmentId());
                    boolean hasLegacyAssignment = !firestore.collection(ASSIGNMENTS)
                            .whereEqualTo("cycleYear", candidate)
                            .whereEqualTo("evaluatorStaffId", staffId)
                            .get().get().isEmpty();
                    if (departmentParticipates || hasLegacyAssignment) {
                        employeeRound = legacyCycle;
                        break;
                    }
                }
            }
            if (employeeRound == null) {
                return Map.of("cycle", Map.of(), "assignments", List.of(), "result", Map.of(), "history", history);
            }
            Long roundYear = employeeRound.getLong("cycleYear");
            int year = roundYear == null ? integer(employeeRound.get("year"), "year") : roundYear.intValue();
            boolean hasOwnDepartmentRound = employeeRound.get("departmentIds") instanceof List<?> ids
                    && ids.contains(employee.departmentId());
            List<QueryDocumentSnapshot> roundsForYear = firestore.collection(ROUNDS)
                    .whereEqualTo("cycleYear", year).get().get().getDocuments().stream()
                    .filter(round -> "published".equals(round.getString("status")))
                    .toList();
            Set<String> activeRoundIds = new HashSet<>();
            roundsForYear.forEach(round -> activeRoundIds.add(round.getId()));
            List<Map<String, Object>> assignedForms = new ArrayList<>();
            for (QueryDocumentSnapshot assignment : firestore.collection(ASSIGNMENTS)
                    .whereEqualTo("cycleYear", year)
                    .whereEqualTo("evaluatorStaffId", staffId)
                    .get().get().getDocuments()) {
                if (assignment.getString("roundId") != null
                        && !activeRoundIds.contains(assignment.getString("roundId"))) continue;
                Map<String, Object> form = data(assignment);
                if (!form.containsKey("questions")) {
                    DocumentSnapshot template = firestore.collection(TEMPLATES)
                            .document(text(form.get("templateId"))).get().get();
                    form.put("questions", template.exists() ? template.get("questions") : List.of());
                }
                if (Boolean.TRUE.equals(form.get("anonymous"))) {
                    Staff target = findStaff(text(form.get("targetStaffId")));
                    form.put("targetName", target == null ? "Colleague" : target.name());
                } else if (!"self".equals(form.get("kind"))) {
                    Staff target = findStaff(text(form.get("targetStaffId")));
                    form.put("targetName", target == null ? "Unknown staff member" : target.name());
                }
                form.remove("targetStaffId");
                form.remove("evaluatorStaffId");
                form.remove("evaluatedBy");
                assignedForms.add(form);
            }
            DocumentSnapshot result = firestore.collection(RESULTS)
                    .document(resultId(year, staffId)).get().get();
            boolean hasRoundId = roundYear != null;
            boolean resultMatchesRound = result.exists()
                    && (hasRoundId
                    ? employeeRound.getId().equals(result.getString("roundId"))
                    : result.getString("roundId") == null);
            Map<String, Object> ownResult = result.exists()
                    && hasOwnDepartmentRound
                    && resultMatchesRound
                    && Boolean.TRUE.equals(employeeRound.getBoolean("resultsPublished"))
                    ? data(result)
                    : Map.of();
            Map<String, Object> peerGroups = new HashMap<>();
            if (hasOwnDepartmentRound) {
                QuerySnapshot peerAssignments = (hasRoundId
                        ? firestore.collection(ASSIGNMENTS).whereEqualTo("roundId", employeeRound.getId())
                        : firestore.collection(ASSIGNMENTS).whereEqualTo("cycleYear", year))
                        .whereEqualTo("targetStaffId", staffId)
                        .whereEqualTo("kind", "peer")
                        .get().get();
                for (QueryDocumentSnapshot assignment : peerAssignments.getDocuments()) {
                    String group = assignment.getString("peerGroup");
                    if (group != null) peerGroups.merge(group, 1, (first, second) -> (int) first + (int) second);
                }
            }
            peerGroups.putIfAbsent("same", 0);
            peerGroups.putIfAbsent("lower", 0);
            if (!peerGroups.containsKey("manager")) {
                peerGroups.put("lowerStatus", (int) peerGroups.get("lower") == 0 ? "Nil" : "Assigned");
                    peerGroups.put("lowerLabel", isHeadOfDepartment(employee.designation())
                            ? "subordinate level" : "one level below");
                }
                Map<String, Object> cycle = data(employeeRound);
                cycle.put("year", year);
                cycle.put("id", hasRoundId ? employeeRound.getId() : Integer.toString(year));
                cycle.put("hasOwnEvaluation", hasOwnDepartmentRound);
                Map<String, Object> response = new HashMap<>();
                response.put("cycle", cycle);
                response.put("assignments", assignedForms);
                response.put("result", ownResult);
                response.put("history", history);
                if (hasOwnDepartmentRound) response.put("ownPeerGroups", peerGroups);
                return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> respond(String token, String assignmentId, Map<String, Object> request) {
        VerifiedSession session = sessions.verify(token);
        String staffId = session.employee().staffId();
        if (staffId.isBlank()) throw new EvaluationForbiddenException();
        Object rawScores = request.get("scores");
        if (!(rawScores instanceof Map<?, ?> scores) || scores.isEmpty()) {
            throw new EvaluationRequestException("Provide a score for every question.");
        }
        double total = 0;
        for (Object value : scores.values()) {
            if (!(value instanceof Number number) || number.doubleValue() < 1 || number.doubleValue() > 5) {
                throw new EvaluationRequestException("Question scores must be from 1 to 5.");
            }
            total += number.doubleValue();
        }
        try {
            DocumentReference assignmentRef = firestore.collection(ASSIGNMENTS).document(assignmentId);
            DocumentSnapshot assignment = assignmentRef.get().get();
            if (!assignment.exists() || !staffId.equals(assignment.getString("evaluatorStaffId"))) {
                throw new EvaluationForbiddenException();
            }
            if (!"pending".equals(assignment.getString("status"))) {
                throw new EvaluationConflictException("This evaluation has already been submitted or declined.");
            }
            if ("peer".equals(assignment.getString("kind"))
                    && !Boolean.TRUE.equals(assignment.getBoolean("familiarityConfirmed"))) {
                throw new EvaluationConflictException("Confirm that you can provide a fair review before submitting.");
            }
            if (!isRoundOpen(assignment)) throw new EvaluationConflictException("This evaluation round is not open for submissions.");
            Object rawQuestions = assignment.get("questions");
            if (!(rawQuestions instanceof List<?> questions)) {
                throw new EvaluationRequestException("This evaluation form has no valid questions.");
            }
            Set<String> expectedScoreKeys = expectedScoreKeys(questions);
            if (scores.size() != expectedScoreKeys.size()
                    || !scores.keySet().containsAll(expectedScoreKeys)) {
                throw new EvaluationRequestException("Provide a score for every question and table row in this form.");
            }
            double average = total / scores.size();
            double scorePercent = average * 20;
            Map<String, Object> response = new HashMap<>();
            response.put("assignmentId", assignmentId);
            response.put("cycleYear", assignment.getLong("cycleYear"));
            response.put("scores", scores);
            response.put("scorePercent", scorePercent);
            response.put("comment", text(request.get("comment")));
            response.put("submittedAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
            if (!Boolean.TRUE.equals(assignment.getBoolean("anonymous"))) {
                response.put("evaluatorStaffId", staffId);
            }
            firestore.collection(RESPONSES).document(assignmentId).set(response).get();
            assignmentRef.update("status", "complete").get();
            return Map.of("assignmentId", assignmentId, "status", "complete");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> adminResults(String token, int year) {
        requireAdmin(token);
        try {
            DocumentSnapshot cycle = firestore.collection(CYCLES).document(Integer.toString(year)).get().get();
            List<QueryDocumentSnapshot> assignmentDocuments = firestore.collection(ASSIGNMENTS)
                    .whereEqualTo("cycleYear", year).get().get().getDocuments();
            List<Map<String, Object>> results = new ArrayList<>();
            List<QueryDocumentSnapshot> rounds = firestore.collection(ROUNDS)
                    .whereEqualTo("cycleYear", year).get().get().getDocuments();
            for (QueryDocumentSnapshot round : rounds) {
                if (!"published".equals(round.getString("status"))) continue;
                List<QueryDocumentSnapshot> roundAssignments = assignmentDocuments.stream()
                        .filter(assignment -> round.getId().equals(assignment.getString("roundId")))
                        .toList();
                if (Boolean.TRUE.equals(round.getBoolean("resultsPublished"))) {
                    results.addAll(readResults(year).stream()
                            .filter(result -> round.getId().equals(result.get("roundId"))).toList());
                } else {
                    results.addAll(calculateResults(year, roundAssignments, false, ""));
                }
            }
            List<QueryDocumentSnapshot> legacyAssignments = assignmentDocuments.stream()
                    .filter(assignment -> assignment.getString("roundId") == null)
                    .toList();
            if (!legacyAssignments.isEmpty()) {
                if (cycle.exists() && Boolean.TRUE.equals(cycle.getBoolean("resultsPublished"))) {
                    results.addAll(readResults(year).stream()
                            .filter(result -> result.get("roundId") == null).toList());
                } else {
                    results.addAll(calculateResults(year, legacyAssignments, false, ""));
                }
            }
            List<Map<String, Object>> employees = new ArrayList<>();
            for (Map<String, Object> result : results) {
                Staff staff = findStaff(text(result.get("staffId")));
                Map<String, Object> row = new HashMap<>(result);
                row.put("id", resultId(year, text(result.get("staffId"))));
                row.put("name", staff == null ? "Unknown staff member" : staff.name());
                row.put("department", staff == null ? "" : staff.departmentName());
                row.put("designation", staff == null ? "" : staff.designation());
                if (staff != null) row.put("history", readPublishedHistory(staff.id()));
                employees.add(row);
            }
            return Map.of(
                    "year", year,
                    "cycle", cycle.exists() ? data(cycle) : Map.of(),
                    "results", employees,
                    "assignments", readAssignments(assignmentDocuments),
                    "rounds", rounds.stream().map(round -> {
                        Map<String, Object> row = data(round);
                        row.put("id", round.getId());
                        return row;
                    }).toList()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    public Map<String, Object> publishResults(String token, String roundId) {
        VerifiedSession admin = requireAdmin(token);
        try {
            DocumentReference roundRef = firestore.collection(ROUNDS).document(roundId);
            DocumentSnapshot round = roundRef.get().get();
            if (!round.exists() || !"published".equals(round.getString("status"))) {
                throw new EvaluationConflictException("There is no published evaluation round with that ID.");
            }
            if (Boolean.TRUE.equals(round.getBoolean("resultsPublished"))) {
                throw new EvaluationConflictException("Results have already been published for this round.");
            }
            int year = round.getLong("cycleYear").intValue();
            List<QueryDocumentSnapshot> allAssignments = firestore.collection(ASSIGNMENTS)
                    .whereEqualTo("roundId", roundId).get().get().getDocuments();
            List<QueryDocumentSnapshot> assignments = allAssignments.stream()
                    .filter(item -> !"replaced".equals(item.getString("status")))
                    .toList();
            if (assignments.isEmpty() || assignments.stream()
                    .anyMatch(item -> !"complete".equals(item.getString("status")))) {
                throw new EvaluationConflictException("All evaluation assignments must be complete before results can be published.");
            }
            List<Map<String, Object>> saved = calculateResults(year, assignments, true, admin.employee().workEmail());
            roundRef.update(
                    "resultsPublished", true,
                    "resultsPublishedBy", admin.employee().workEmail(),
                    "resultsPublishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp()
            ).get();
            return Map.of("year", year, "roundId", roundId, "publishedCount", saved.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvaluationDataException(exception);
        } catch (ExecutionException exception) {
            throw new EvaluationDataException(exception.getCause());
        }
    }

    private List<Map<String, Object>> planAssignments(
            int year,
            List<Staff> staff,
            List<Staff> allActiveStaff,
            Map<String, DocumentSnapshot> templates
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Staff> staffById = new HashMap<>();
        allActiveStaff.forEach(person -> staffById.put(person.id(), person));
        Map<String, Integer> peerLoad = new HashMap<>();

        List<Staff> managers = allActiveStaff.stream().filter(person -> isDepartmentManager(person.designation())).toList();
        if (staff.stream().anyMatch(person -> isDepartmentManager(person.designation())) && managers.size() < 3) {
            throw new EvaluationRequestException(
                    "At least two other Department Managers must be included to evaluate each manager."
            );
        }
        List<Staff> heads = allActiveStaff.stream().filter(person -> isHeadOfDepartment(person.designation())).toList();
        for (Staff target : staff) {
            addAssignment(result, year, target, target, "self", "self", false, templates.get("self"));
            addAssignment(result, year, target, staffById.get(target.supervisorId()), "primary", "primary", false, templates.get("primary"));
            List<Staff> peerCandidates;
            if (isDepartmentManager(target.designation())) {
                peerCandidates = new ArrayList<>(managers.stream()
                        .filter(candidate -> !candidate.id().equals(target.id()))
                        .toList());
                Collections.shuffle(peerCandidates);
                for (Staff candidate : peerCandidates) {
                    addAssignment(result, year, target, candidate, "peer", "manager", true, templates.get("peer"));
                    peerLoad.merge(candidate.id(), 1, Integer::sum);
                }
            } else if (isHeadOfDepartment(target.designation())) {
                List<Staff> sameLevelHeads = new ArrayList<>(heads.stream()
                        .filter(candidate -> !candidate.id().equals(target.id()))
                        .toList());
                if (sameLevelHeads.size() < 2) {
                    throw new EvaluationRequestException(
                            "At least two other Heads of Department are required to evaluate " + target.name() + "."
                    );
                }
                Collections.shuffle(sameLevelHeads);
                for (Staff candidate : sameLevelHeads.subList(0, 2)) {
                    addAssignment(result, year, target, candidate, "peer", "same", true, templates.get("peer"));
                    peerLoad.merge(candidate.id(), 1, Integer::sum);
                }
                List<Staff> subordinates = new ArrayList<>(allActiveStaff.stream()
                        .filter(candidate -> candidate.departmentId().equals(target.departmentId()))
                        .filter(candidate -> !candidate.id().equals(target.id()))
                        .filter(candidate -> !isHeadOfDepartment(candidate.designation()))
                        .filter(candidate -> !isDepartmentManager(candidate.designation()))
                        .toList());
                if (subordinates.size() < 3) {
                    throw new EvaluationRequestException(
                            "At least three subordinate-level evaluators are required for " + target.name() + "."
                    );
                }
                Collections.shuffle(subordinates);
                for (Staff candidate : subordinates.subList(0, 3)) {
                    addAssignment(result, year, target, candidate, "peer", "lower", true, templates.get("peer"));
                    peerLoad.merge(candidate.id(), 1, Integer::sum);
                }
            } else {
                int targetRank = rank(target.designation());
                if (targetRank < 0) {
                    throw new EvaluationRequestException("No evaluator level mapping exists for " + target.designation() + ".");
                }
                List<Staff> sameLevel = staff.stream()
                        .filter(candidate -> candidate.departmentId().equals(target.departmentId()))
                        .filter(candidate -> !candidate.id().equals(target.id()))
                        .filter(candidate -> !candidate.id().equals(target.supervisorId()))
                        .filter(candidate -> rank(candidate.designation()) == targetRank)
                        .toList();
                if (sameLevel.size() < 2) {
                    throw new EvaluationRequestException(
                            "At least two same-level peer evaluators are required for " + target.name()
                                    + "; only " + sameLevel.size() + " eligible staff were found."
                    );
                }
                addRandomBalanced(result, year, target, sameLevel, 2, "same", peerLoad, templates.get("peer"));
                List<Staff> lowerLevel = staff.stream()
                        .filter(candidate -> candidate.departmentId().equals(target.departmentId()))
                        .filter(candidate -> !candidate.id().equals(target.id()))
                        .filter(candidate -> !candidate.id().equals(target.supervisorId()))
                        .filter(candidate -> rank(candidate.designation()) == targetRank - 1)
                        .toList();
                addRandomBalanced(result, year, target, lowerLevel, 2, "lower", peerLoad, templates.get("peer"));
            }
        }
        ensureMinimumPeerParticipation(result, year, staff, peerLoad, templates.get("peer"));
        return result;
    }

    private void ensureMinimumPeerParticipation(
            List<Map<String, Object>> assignments,
            int year,
            List<Staff> staff,
            Map<String, Integer> peerLoad,
            DocumentSnapshot peerTemplate
    ) {
        Set<String> assignedPairs = new HashSet<>();
        for (Map<String, Object> assignment : assignments) {
            if ("peer".equals(assignment.get("kind"))) {
                assignedPairs.add(assignment.get("targetStaffId") + "|" + assignment.get("evaluatorStaffId"));
            }
        }
        List<Staff> evaluators = new ArrayList<>(staff);
        Collections.shuffle(evaluators);
        evaluators.sort(Comparator.comparingInt(person -> peerLoad.getOrDefault(person.id(), 0)));
        for (Staff evaluator : evaluators) {
            while (peerLoad.getOrDefault(evaluator.id(), 0) < 2) {
                List<Staff> eligibleTargets = staff.stream()
                        .filter(target -> !target.id().equals(evaluator.id()))
                        .filter(target -> !target.supervisorId().equals(evaluator.id()))
                        .filter(target -> !assignedPairs.contains(target.id() + "|" + evaluator.id()))
                        .filter(target -> peerGroupFor(target, evaluator) != null)
                        .toList();
                if (eligibleTargets.isEmpty()) {
                    throw new EvaluationRequestException(
                            evaluator.name() + " cannot be assigned to at least two other peer evaluations. "
                                    + "Review the selected departments and staff levels."
                    );
                }
                List<Staff> choices = new ArrayList<>(eligibleTargets);
                Collections.shuffle(choices);
                Staff target = choices.get(0);
                String group = peerGroupFor(target, evaluator);
                addAssignment(assignments, year, target, evaluator, "peer", group, true, peerTemplate);
                assignedPairs.add(target.id() + "|" + evaluator.id());
                peerLoad.merge(evaluator.id(), 1, Integer::sum);
            }
        }
    }

    private static String peerGroupFor(Staff target, Staff evaluator) {
        if (isDepartmentManager(target.designation())) {
            return isDepartmentManager(evaluator.designation()) ? "manager" : null;
        }
        if (isHeadOfDepartment(target.designation())) {
            if (isHeadOfDepartment(evaluator.designation())) return "same";
            return !isDepartmentManager(evaluator.designation())
                    && target.departmentId().equals(evaluator.departmentId()) ? "lower" : null;
        }
        if (!target.departmentId().equals(evaluator.departmentId())) return null;
        int targetRank = rank(target.designation());
        int evaluatorRank = rank(evaluator.designation());
        if (targetRank < 0 || evaluatorRank < 0) return null;
        if (targetRank == evaluatorRank) return "same";
        return evaluatorRank == targetRank - 1 ? "lower" : null;
    }

    private void addRandomBalanced(
            List<Map<String, Object>> assignments,
            int year,
            Staff target,
            List<Staff> candidates,
            int count,
            String group,
            Map<String, Integer> peerLoad,
            DocumentSnapshot template
    ) {
        List<Staff> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        shuffled.sort(Comparator.comparingInt(candidate -> peerLoad.getOrDefault(candidate.id(), 0)));
        Set<String> chosen = new HashSet<>();
        for (Staff candidate : shuffled) {
            if (chosen.size() >= count) break;
            if (chosen.add(candidate.id())) {
                addAssignment(assignments, year, target, candidate, "peer", group, true, template);
                peerLoad.merge(candidate.id(), 1, Integer::sum);
            }
        }
    }

    private void addAssignment(
            List<Map<String, Object>> assignments,
            int year,
            Staff target,
            Staff evaluator,
            String kind,
            String group,
            boolean anonymous,
            DocumentSnapshot template
    ) {
        String key = year + "|" + target.id() + "|" + evaluator.id() + "|" + kind + "|" + group;
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("cycleYear", year);
        item.put("targetStaffId", target.id());
        item.put("evaluatorStaffId", evaluator.id());
        item.put("kind", kind);
        item.put("peerGroup", group);
        item.put("anonymous", anonymous);
        item.put("templateId", template.getId());
        item.put("templateName", template.getString("name"));
        item.put("questions", template.get("questions"));
        item.put("status", "pending");
        item.put("departmentId", target.departmentId());
        assignments.add(item);
    }

    private void writeAssignments(List<Map<String, Object>> assignments)
            throws InterruptedException, ExecutionException {
        for (int start = 0; start < assignments.size(); start += 450) {
            var batch = firestore.batch();
            for (Map<String, Object> assignment : assignments.subList(start, Math.min(start + 450, assignments.size()))) {
                String id = text(assignment.get("id"));
                batch.set(firestore.collection(ASSIGNMENTS).document(id), assignment);
            }
            batch.commit().get();
        }
    }

    private List<Map<String, Object>> readTemplates() throws InterruptedException, ExecutionException {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (QueryDocumentSnapshot document : firestore.collection(TEMPLATES).get().get().getDocuments()) {
            Map<String, Object> item = data(document);
            item.put("id", document.getId());
            templates.add(item);
        }
        return templates;
    }

    private Map<String, DocumentSnapshot> readSelectedTemplates(Object value)
            throws InterruptedException, ExecutionException {
        if (!(value instanceof Map<?, ?> ids)) {
            throw new EvaluationRequestException("Choose self, primary, and peer templates for this cycle.");
        }
        Map<String, DocumentSnapshot> templates = new HashMap<>();
        for (String type : List.of("self", "primary", "peer")) {
            String id = text(ids.get(type)).trim();
            if (id.isEmpty()) throw new EvaluationRequestException("Choose a " + type + " evaluation template.");
            DocumentSnapshot template = firestore.collection(TEMPLATES).document(id).get().get();
            if (!template.exists() || !type.equals(template.getString("type"))
                    || !(template.get("questions") instanceof List<?> questions)
                    || questions.isEmpty()) {
                throw new EvaluationRequestException("The selected " + type + " evaluation template is invalid.");
            }
            validateTemplateQuestions(questions);
            templates.put(type, template);
        }
        return templates;
    }

    static void validateTemplateQuestions(List<?> questions) {
        for (Object rawQuestion : questions) {
            if (rawQuestion instanceof String legacyQuestion) {
                if (legacyQuestion.isBlank()) throw new EvaluationRequestException("Questions cannot be empty.");
                continue;
            }
            if (!(rawQuestion instanceof Map<?, ?> question)) {
                throw new EvaluationRequestException("Each question must be a rating question or rating table.");
            }
            String type = text(question.get("type"));
            if (!Set.of("rating", "matrix").contains(type) || text(question.get("prompt")).isBlank()) {
                throw new EvaluationRequestException("Each question needs a prompt and a supported question type.");
            }
            if (!"matrix".equals(type)) continue;
            Object rawRows = question.get("rows");
            Object rawColumns = question.get("columns");
            if (!(rawRows instanceof List<?> rows) || rows.isEmpty() || rows.size() > 30
                    || rows.stream().anyMatch(row -> !(row instanceof String value) || value.isBlank())) {
                throw new EvaluationRequestException("A rating table must have between 1 and 30 named criteria.");
            }
            if (!(rawColumns instanceof List<?> columns) || columns.size() != 5
                    || columns.stream().anyMatch(column -> !(column instanceof String value) || value.isBlank())) {
                throw new EvaluationRequestException("A rating table must have five non-empty rating labels.");
            }
        }
    }

    static Set<String> expectedScoreKeys(List<?> questions) {
        Set<String> keys = new HashSet<>();
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            Object question = questions.get(questionIndex);
            if (question instanceof Map<?, ?> values && "matrix".equals(text(values.get("type")))) {
                if (!(values.get("rows") instanceof List<?> rows)) {
                    throw new EvaluationRequestException("This rating table has no valid criteria rows.");
                }
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    keys.add(questionIndex + "." + rowIndex);
                }
            } else {
                keys.add(Integer.toString(questionIndex));
            }
        }
        return keys;
    }

    private List<Map<String, Object>> calculateResults(
            int year,
            List<QueryDocumentSnapshot> assignments,
            boolean persist,
            String publishedBy
    ) throws InterruptedException, ExecutionException {
        Map<String, Map<String, Object>> byStaff = new HashMap<>();
        for (QueryDocumentSnapshot assignment : assignments) {
            if ("replaced".equals(assignment.getString("status"))) continue;
            String staffId = assignment.getString("targetStaffId");
            Map<String, Object> result = byStaff.computeIfAbsent(staffId, ignored -> {
                Map<String, Object> initial = new HashMap<>();
                initial.put("staffId", staffId);
                initial.put("cycleYear", year);
                initial.put("roundId", assignment.getString("roundId"));
                initial.put("primaryScore", null);
                initial.put("selfScore", null);
                initial.put("peerTotal", 0.0);
                initial.put("peerCount", 0);
                initial.put("peerRequiredCount", 0);
                initial.put("samePeerCount", 0);
                initial.put("lowerPeerCount", 0);
                initial.put("managerPeerCount", 0);
                initial.put("primaryComplete", false);
                initial.put("selfComplete", false);
                initial.put("completeAssignments", 0);
                initial.put("requiredAssignments", 0);
                return initial;
            });
            String kind = assignment.getString("kind");
            result.put("requiredAssignments", (int) result.get("requiredAssignments") + 1);
            if ("peer".equals(kind)) {
                result.put("peerRequiredCount", (int) result.get("peerRequiredCount") + 1);
                if ("same".equals(assignment.getString("peerGroup"))) {
                    result.put("samePeerCount", (int) result.get("samePeerCount") + 1);
                } else if ("lower".equals(assignment.getString("peerGroup"))) {
                    result.put("lowerPeerCount", (int) result.get("lowerPeerCount") + 1);
                } else if ("manager".equals(assignment.getString("peerGroup"))) {
                    result.put("managerPeerCount", (int) result.get("managerPeerCount") + 1);
                }
            }
            if (!"complete".equals(assignment.getString("status"))) continue;
            DocumentSnapshot response = firestore.collection(RESPONSES).document(assignment.getId()).get().get();
            if (!response.exists()) throw new EvaluationConflictException("An evaluation response is missing.");
            Number rawScore = response.get("scorePercent") instanceof Number number ? number : null;
            if (rawScore == null) throw new EvaluationConflictException("An evaluation response has an invalid score.");
            double score = rawScore.doubleValue();
            result.put("completeAssignments", (int) result.get("completeAssignments") + 1);
            if ("primary".equals(kind)) {
                result.put("primaryScore", score);
                result.put("primaryComplete", true);
            } else if ("self".equals(kind)) {
                result.put("selfScore", score);
                result.put("selfComplete", true);
            } else if ("peer".equals(kind)) {
                result.put("peerTotal", (double) result.get("peerTotal") + score);
                result.put("peerCount", (int) result.get("peerCount") + 1);
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> result : byStaff.values()) {
            int peerCount = (int) result.get("peerCount");
            int peerRequiredCount = (int) result.get("peerRequiredCount");
            double peerAverage = peerCount == 0 ? 0 : (double) result.get("peerTotal") / peerCount;
            result.remove("peerTotal");
            result.put("peerAverage", peerCount == 0 ? null : peerAverage);
            result.put("completeAssignments", result.get("completeAssignments"));
            result.put("requiredAssignments", result.get("requiredAssignments"));
            boolean complete = (int) result.get("completeAssignments") == (int) result.get("requiredAssignments");
            boolean hasComponents = Boolean.TRUE.equals(result.get("primaryComplete"))
                    && Boolean.TRUE.equals(result.get("selfComplete"))
                    && peerCount > 0 && peerCount == peerRequiredCount;
            Object finalScore = null;
            if (complete && hasComponents) {
                finalScore = EvaluationScoring.finalScore(
                        (double) result.get("primaryScore"),
                        (double) result.get("selfScore"),
                        peerAverage
                );
            }
            if (persist && finalScore == null) {
                throw new EvaluationConflictException(
                        "A staff member is missing a completed self, primary, or peer evaluation."
                );
            }
            result.put("roundId", assignments.stream()
                    .filter(assignment -> text(assignment.getString("targetStaffId")).equals(text(result.get("staffId"))))
                    .map(assignment -> assignment.getString("roundId"))
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null));
            result.put("finalScore", finalScore);
            result.put("rating", finalScore == null ? "Pending" : EvaluationScoring.rating((double) finalScore));
            result.put("published", persist);
            if (persist) {
                result.remove("primaryComplete");
                result.remove("selfComplete");
                result.remove("peerRequiredCount");
                result.remove("completeAssignments");
                result.remove("requiredAssignments");
                result.put("publishedBy", publishedBy);
                result.put("publishedAt", com.google.cloud.firestore.FieldValue.serverTimestamp());
                firestore.collection(RESULTS)
                        .document(resultId(year, text(result.get("staffId")))).set(result).get();
            }
            results.add(result);
        }
        return results;
    }

    private List<Map<String, Object>> readPublishedHistory(String staffId)
            throws InterruptedException, ExecutionException {
        List<Map<String, Object>> history = new ArrayList<>();
        for (QueryDocumentSnapshot document : firestore.collection(RESULTS)
                .whereEqualTo("staffId", staffId).get().get().getDocuments()) {
            if (!Boolean.TRUE.equals(document.getBoolean("published"))) continue;
            Map<String, Object> item = data(document);
            item.put("id", document.getId());
            history.add(item);
        }
        history.sort(Comparator.comparingInt(item -> -integer(item.get("cycleYear"), "cycleYear")));
        return history;
    }

    private List<Map<String, Object>> readAssignments(List<QueryDocumentSnapshot> documents)
            throws InterruptedException, ExecutionException {
        List<Map<String, Object>> assignments = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            Map<String, Object> item = data(document);
            item.put("id", document.getId());
            Staff evaluator = findStaff(text(item.get("evaluatorStaffId")));
            Staff target = findStaff(text(item.get("targetStaffId")));
            item.put("evaluatorName", evaluator == null ? "Unknown staff member" : evaluator.name());
            item.put("targetName", target == null ? "Unknown staff member" : target.name());
            assignments.add(item);
        }
        return assignments;
    }

    private List<Map<String, Object>> readAssignmentsForYear(int year)
            throws InterruptedException, ExecutionException {
        return readAssignments(firestore.collection(ASSIGNMENTS)
                .whereEqualTo("cycleYear", year).get().get().getDocuments());
    }

    private List<Map<String, Object>> readResults(int year)
            throws InterruptedException, ExecutionException {
        List<Map<String, Object>> results = new ArrayList<>();
        for (QueryDocumentSnapshot document : firestore.collection(RESULTS)
                .whereEqualTo("cycleYear", year).get().get().getDocuments()) {
            Map<String, Object> item = data(document);
            item.put("id", document.getId());
            results.add(item);
        }
        return results;
    }

    private List<Staff> activeEmployees() throws InterruptedException, ExecutionException {
        List<Staff> staff = new ArrayList<>();
        for (QueryDocumentSnapshot document : firestore.collection("employees").get().get().getDocuments()) {
            String status = firstText(document, "status", "employmentStatus");
            if (!Set.of("permanent", "probationary", "intern").contains(status.toLowerCase(Locale.ROOT))) continue;
            String departmentId = firstText(document, "department", "departmentId");
            DocumentSnapshot department = departmentId.isBlank()
                    ? null
                    : firestore.collection("departments").document(departmentId).get().get();
            String departmentName = department == null || !department.exists()
                    ? departmentId
                    : firstText(department, "departmentName", "name");
            staff.add(new Staff(
                    firstText(document, "staffId").isBlank() ? document.getId() : firstText(document, "staffId"),
                    firstText(document, "preferredName", "nameInFull", "fullName"),
                    firstText(document, "designation", "title"),
                    departmentId,
                    departmentName,
                    firstText(document, "supervisor", "supervisorId"),
                    firstText(document, "email")
            ));
        }
        return staff;
    }

    private Staff findStaff(String id) throws InterruptedException, ExecutionException {
        if (id.isBlank()) return null;
        DocumentSnapshot document = firestore.collection("employees").document(id).get().get();
        if (!document.exists()) {
            for (Staff staff : activeEmployees()) if (staff.id().equals(id)) return staff;
            return null;
        }
        String departmentId = firstText(document, "department", "departmentId");
        DocumentSnapshot department = departmentId.isBlank()
                ? null
                : firestore.collection("departments").document(departmentId).get().get();
        return new Staff(
                firstText(document, "staffId").isBlank() ? document.getId() : firstText(document, "staffId"),
                firstText(document, "preferredName", "nameInFull", "fullName"),
                firstText(document, "designation", "title"),
                departmentId,
                department == null || !department.exists() ? departmentId : firstText(department, "departmentName", "name"),
                firstText(document, "supervisor", "supervisorId"),
                firstText(document, "email")
        );
    }

    private List<QueryDocumentSnapshot> findRoundsForDepartment(String departmentId)
            throws InterruptedException, ExecutionException {
        int year = LocalDate.now().getYear();
        List<QueryDocumentSnapshot> rounds = new ArrayList<>();
        for (int candidate : List.of(year, year + 1, year - 1)) {
            rounds.addAll(firestore.collection(ROUNDS)
                    .whereEqualTo("cycleYear", candidate).get().get().getDocuments().stream()
                    .filter(round -> "published".equals(round.getString("status")))
                    .filter(round -> round.get("departmentIds") instanceof List<?> ids && ids.contains(departmentId))
                    .toList());
        }
        return rounds;
    }

    private static QueryDocumentSnapshot chooseRound(List<QueryDocumentSnapshot> rounds) {
        LocalDate today = LocalDate.now();
        return rounds.stream()
                .filter(round -> !text(round.get("startDate")).isBlank() && !text(round.get("dueDate")).isBlank())
                .min(Comparator
                        .comparingInt((QueryDocumentSnapshot round) -> {
                            LocalDate start = LocalDate.parse(round.getString("startDate"));
                            LocalDate due = LocalDate.parse(round.getString("dueDate"));
                            if (!today.isBefore(start) && !today.isAfter(due)) return 0;
                            return today.isBefore(start) ? 1 : 2;
                        })
                        .thenComparing(round -> {
                            LocalDate start = LocalDate.parse(round.getString("startDate"));
                            LocalDate due = LocalDate.parse(round.getString("dueDate"));
                            return !today.isBefore(start) && !today.isAfter(due) ? start
                                    : today.isBefore(start) ? start : due;
                        }))
                .orElse(null);
    }

    private boolean isRoundOpen(DocumentSnapshot assignment)
            throws InterruptedException, ExecutionException {
        String roundId = assignment.getString("roundId");
        if (roundId == null || roundId.isBlank()) {
            return "published".equals(firestore.collection(CYCLES)
                    .document(Integer.toString(assignment.getLong("cycleYear").intValue()))
                    .get().get().getString("status"));
        }
        DocumentSnapshot round = firestore.collection(ROUNDS).document(roundId).get().get();
        if (!round.exists() || !"published".equals(round.getString("status"))) return false;
        LocalDate today = LocalDate.now();
        return !today.isBefore(LocalDate.parse(round.getString("startDate")))
                && !today.isAfter(LocalDate.parse(round.getString("dueDate")));
    }

    static LocalDate date(Object value, String field) {
        try {
            return LocalDate.parse(requiredTextValue(value, field));
        } catch (java.time.DateTimeException exception) {
            throw new EvaluationRequestException(field + " must be a valid date in YYYY-MM-DD format.");
        }
    }

    private static String requiredTextValue(Object value, String field) {
        String text = text(value).trim();
        if (text.isBlank()) throw new EvaluationRequestException(field + " is required.");
        return text;
    }

    private static int rank(String designation) {
        String value = normalize(designation);
        if (value.contains("head of department") || value.equals("hod") || value.contains("department manager")) return 6;
        if (value.contains("senior lecturer")) return 5;
        if (value.contains("lecturer grade i") || value.contains("lecturer grade 1")) return 4;
        if (value.contains("lecturer grade ii") || value.contains("lecturer grade 2")) return 3;
        if (value.contains("assistant lecturer")) return 2;
        if (value.contains("assistant manager")) return 5;
        if (value.contains("senior executive")) return 4;
        if (value.equals("executive") || value.startsWith("executive ")) return 3;
        if (value.contains("junior executive")) return 2;
        if (value.contains("lab technician")) return 1;
        if (value.contains("academic coordinator")) return 1;
        return -1;
    }

    private static boolean isDepartmentManager(String designation) {
        return normalize(designation).contains("department manager");
    }

    private static boolean isHeadOfDepartment(String designation) {
        String value = normalize(designation);
        return value.contains("head of department") || value.equals("hod");
    }

    private static boolean isValidAlternative(Staff target, Staff alternative, String group) {
        if (target.id().equals(alternative.id())) return false;
        if ("manager".equals(group)) return isDepartmentManager(alternative.designation());
        if (isHeadOfDepartment(target.designation())) {
            if ("same".equals(group)) return isHeadOfDepartment(alternative.designation());
            return "lower".equals(group)
                    && target.departmentId().equals(alternative.departmentId())
                    && !isHeadOfDepartment(alternative.designation())
                    && !isDepartmentManager(alternative.designation());
        }
        if (!target.departmentId().equals(alternative.departmentId())) return false;
        int targetRank = rank(target.designation());
        int alternativeRank = rank(alternative.designation());
        return "same".equals(group)
                ? targetRank >= 0 && targetRank == alternativeRank
                : targetRank >= 0 && alternativeRank == targetRank - 1;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().replaceAll("\\s+", " ");
    }

    private static String resultId(int year, String staffId) {
        return year + "_" + staffId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static Map<String, Object> data(DocumentSnapshot document) {
        Map<String, Object> value = document.getData();
        return value == null ? new HashMap<>() : new HashMap<>(value);
    }

    private static String firstText(DocumentSnapshot document, String... fields) {
        for (String field : fields) {
            Object value = document.get(field);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String requiredText(Map<String, Object> request, String field) {
        String value = text(request.get(field)).trim();
        if (value.isBlank()) throw new EvaluationRequestException(field + " is required.");
        return value;
    }

    private static int integer(Object value, String name) {
        if (value instanceof Number number && number.intValue() >= 2020 && number.intValue() <= 2100) {
            return number.intValue();
        }
        throw new EvaluationRequestException(name + " must be a valid year.");
    }

    private record Staff(
            String id,
            String name,
            String designation,
            String departmentId,
            String departmentName,
            String supervisorId,
            String email
    ) {
    }
}
