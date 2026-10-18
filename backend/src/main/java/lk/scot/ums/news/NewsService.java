package lk.scot.ums.news;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.VerifiedSession;
import lk.scot.ums.auth.VerifiedSessionService;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsService {
    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);
    private static final Set<String> NEWS_MANAGER_EMAILS =
            Set.of("hr@scot.lk", "admin@scot.lk", "sheran@scot.lk", "it@scot.lk");

    private final Firestore firestore;
    private final VerifiedSessionService sessions;

    public NewsService(Firestore firestore, VerifiedSessionService sessions) {
        this.firestore = firestore;
        this.sessions = sessions;
    }

    public boolean isAuthorizedNewsManager(String token) {
        try {
            VerifiedSession session = sessions.verify(token);
            if (session.evaluationAdmin()) return true;
            String email = session.employee().workEmail().trim().toLowerCase(Locale.ROOT);
            if (NEWS_MANAGER_EMAILS.contains(email)) return true;
            String role = session.employee().role();
            return role != null && (role.equalsIgnoreCase("Super Admin")
                    || role.equalsIgnoreCase("Administrator")
                    || role.equalsIgnoreCase("Admin"));
        } catch (Exception e) {
            return false;
        }
    }

    public List<NewsArticle> list(String token) {
        sessions.verify(token);
        try {
            var collection = firestore.collection("scotNews");
            List<QueryDocumentSnapshot> docs = collection.get().get().getDocuments();
            if (docs.isEmpty()) {
                seedDefaultNews();
                docs = collection.get().get().getDocuments();
            }

            List<NewsArticle> articles = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                articles.add(toNewsArticle(doc));
            }
            articles.sort(Comparator.comparing(NewsArticle::createdAt, Comparator.reverseOrder()));
            return articles;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(e);
        } catch (ExecutionException e) {
            logger.error("Failed to fetch news from Firestore", e.getCause());
            return defaultStaticNews();
        } catch (Exception e) {
            logger.warn("Returning fallback default news: {}", e.getMessage());
            return defaultStaticNews();
        }
    }

    public NewsArticle create(String token, NewsRequest request) {
        if (!isAuthorizedNewsManager(token)) {
            throw new EvaluationForbiddenException();
        }
        VerifiedSession session = sessions.verify(token);

        String title = clean(request.title());
        String summary = clean(request.summary());
        if (title.isBlank() || summary.isBlank()) {
            throw new IllegalArgumentException("Title and summary are required.");
        }

        String content = clean(request.content());
        if (content.isBlank()) content = summary;
        String category = clean(request.category());
        if (category.isBlank()) category = "General";
        String imageUrl = clean(request.imageUrl());
        if (imageUrl.isBlank()) {
            imageUrl = defaultImageForCategory(category);
        }

        long now = System.currentTimeMillis();
        String date = LocalDate.now().toString();
        String authorName = session.employee().preferredName() != null && !session.employee().preferredName().isBlank()
                ? session.employee().preferredName() : session.employee().fullName();
        String authorEmail = session.employee().workEmail();

        try {
            DocumentReference docRef = firestore.collection("scotNews").document();
            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("summary", summary);
            data.put("content", content);
            data.put("category", category);
            data.put("isSpecial", request.isSpecial());
            data.put("imageUrl", imageUrl);
            data.put("authorName", authorName);
            data.put("authorEmail", authorEmail);
            data.put("date", date);
            data.put("createdAt", now);

            docRef.set(data).get();
            return new NewsArticle(docRef.getId(), title, summary, content, category, authorName, authorEmail, date, request.isSpecial(), imageUrl, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(e);
        } catch (ExecutionException e) {
            throw new EmployeeLookupException(e.getCause());
        }
    }

    public NewsArticle update(String token, String id, NewsRequest request) {
        if (!isAuthorizedNewsManager(token)) {
            throw new EvaluationForbiddenException();
        }

        try {
            DocumentReference docRef = firestore.collection("scotNews").document(id);
            DocumentSnapshot existing = docRef.get().get();
            if (!existing.exists()) {
                throw new IllegalArgumentException("News article not found.");
            }

            Map<String, Object> updates = new HashMap<>();
            if (request.title() != null && !request.title().isBlank()) updates.put("title", clean(request.title()));
            if (request.summary() != null && !request.summary().isBlank()) updates.put("summary", clean(request.summary()));
            if (request.content() != null && !request.content().isBlank()) updates.put("content", clean(request.content()));
            if (request.category() != null && !request.category().isBlank()) updates.put("category", clean(request.category()));
            updates.put("isSpecial", request.isSpecial());
            if (request.imageUrl() != null && !request.imageUrl().isBlank()) updates.put("imageUrl", clean(request.imageUrl()));

            docRef.update(updates).get();
            DocumentSnapshot updated = docRef.get().get();
            return toNewsArticle(updated);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(e);
        } catch (ExecutionException e) {
            throw new EmployeeLookupException(e.getCause());
        }
    }

    public void delete(String token, String id) {
        if (!isAuthorizedNewsManager(token)) {
            throw new EvaluationForbiddenException();
        }
        try {
            firestore.collection("scotNews").document(id).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmployeeLookupException(e);
        } catch (ExecutionException e) {
            throw new EmployeeLookupException(e.getCause());
        }
    }

    private void seedDefaultNews() {
        try {
            for (NewsArticle article : defaultStaticNews()) {
                DocumentReference docRef = firestore.collection("scotNews").document(article.id());
                Map<String, Object> data = new HashMap<>();
                data.put("title", article.title());
                data.put("summary", article.summary());
                data.put("content", article.content());
                data.put("category", article.category());
                data.put("isSpecial", article.isSpecial());
                data.put("imageUrl", article.imageUrl());
                data.put("authorName", article.authorName());
                data.put("authorEmail", article.authorEmail());
                data.put("date", article.date());
                data.put("createdAt", article.createdAt());
                docRef.set(data).get();
            }
        } catch (Exception e) {
            logger.warn("Could not seed default news into Firestore: {}", e.getMessage());
        }
    }

    private static List<NewsArticle> defaultStaticNews() {
        return List.of(
                new NewsArticle(
                        "news-1",
                        "Annual University Convocation & Awards Ceremony 2026",
                        "The annual convocation will be held on November 15 at BMICH Main Hall. All academic and administrative staff are invited to confirm attendance.",
                        "The Board of Governors and Academic Senate cordially invite all faculty members and staff to the SCOT University Annual Convocation 2026. Staff regalia collection will commence on November 5 from the Faculty Office. Please notify HR for special seating allocations.",
                        "Events",
                        "HR Administrator",
                        "hr@scot.lk",
                        LocalDate.now().toString(),
                        true,
                        "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=800&q=80",
                        System.currentTimeMillis() - 1000
                ),
                new NewsArticle(
                        "news-2",
                        "Staff Health & Wellness Week - Comprehensive Medical Screening",
                        "HR announces the annual comprehensive health checkup camp for all permanent and probationary staff members from Oct 28 - Nov 2.",
                        "In partnership with Asiri Health, SCOT HR is hosting the Annual Wellness Week at the University Health Centre. Free cardiac screening, eye tests, and dental checkups will be provided during work hours without leave deduction.",
                        "HR & Welfare",
                        "Sheran Gomez",
                        "sheran@scot.lk",
                        LocalDate.now().minusDays(1).toString(),
                        true,
                        "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80",
                        System.currentTimeMillis() - 86400000
                ),
                new NewsArticle(
                        "news-3",
                        "New Research Grant Allocations & Innovation Lab Launch",
                        "Applications are now open for faculty seed grants of up to LKR 2.5 Million for AI and Engineering innovation projects.",
                        "The Research and Innovation Council is pleased to open applications for the 2026 Faculty Research Grants. Proposals should be submitted through the portal before the 10th of next month. High-priority areas include Cloud ERP and Sustainable Energy.",
                        "Academic",
                        "University Administrator",
                        "admin@scot.lk",
                        LocalDate.now().minusDays(3).toString(),
                        false,
                        "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=800&q=80",
                        System.currentTimeMillis() - (86400000 * 3)
                ),
                new NewsArticle(
                        "news-4",
                        "Semester Exam Timetable & Invigilation Duty Rosters Released",
                        "Examination rosters for the upcoming Semester 2 finals are now published. Lecturers and supervisors can check duty assignments.",
                        "The Examination Unit has published the finalized exam schedule for Semester 2. All exam invigilators are required to attend the briefing session on Friday at 3:00 PM in Senate Hall Room A.",
                        "Administration",
                        "Examination Unit",
                        "admin@scot.lk",
                        LocalDate.now().minusDays(5).toString(),
                        false,
                        "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?auto=format&fit=crop&w=800&q=80",
                        System.currentTimeMillis() - (86400000 * 5)
                )
        );
    }

    private static NewsArticle toNewsArticle(DocumentSnapshot doc) {
        String id = doc.getId();
        String title = text(doc, "title");
        String summary = text(doc, "summary");
        String content = text(doc, "content");
        if (content.isBlank()) content = summary;
        String category = text(doc, "category");
        if (category.isBlank()) category = "General";
        String authorName = text(doc, "authorName");
        String authorEmail = text(doc, "authorEmail");
        String date = text(doc, "date");
        Boolean isSpecial = doc.getBoolean("isSpecial");
        String imageUrl = text(doc, "imageUrl");
        if (imageUrl.isBlank()) imageUrl = defaultImageForCategory(category);
        Long createdAt = doc.getLong("createdAt");
        if (createdAt == null) createdAt = System.currentTimeMillis();

        return new NewsArticle(id, title, summary, content, category, authorName, authorEmail, date, Boolean.TRUE.equals(isSpecial), imageUrl, createdAt);
    }

    private static String text(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        return val == null ? "" : val.toString().trim();
    }

    private static String clean(String val) {
        return val == null ? "" : val.trim();
    }

    private static String defaultImageForCategory(String category) {
        String cat = category.toLowerCase(Locale.ROOT);
        if (cat.contains("academic") || cat.contains("research")) {
            return "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=800&q=80";
        }
        if (cat.contains("hr") || cat.contains("welfare") || cat.contains("health")) {
            return "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80";
        }
        if (cat.contains("event") || cat.contains("convocation")) {
            return "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=800&q=80";
        }
        return "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?auto=format&fit=crop&w=800&q=80";
    }
}
