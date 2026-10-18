package lk.scot.ums.news;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lk.scot.ums.auth.EmployeeLookupException;
import lk.scot.ums.auth.InvalidFirebaseSessionException;
import lk.scot.ums.evaluation.EvaluationForbiddenException;
import lk.scot.ums.profile.StaffDirectoryController.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsArticle> list(HttpServletRequest request) {
        return newsService.list(bearerToken(request));
    }

    @PostMapping
    public ResponseEntity<NewsArticle> create(
            @RequestBody NewsRequest body,
            HttpServletRequest request
    ) {
        NewsArticle article = newsService.create(bearerToken(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(article);
    }

    @PutMapping("/{id}")
    public NewsArticle update(
            @PathVariable String id,
            @RequestBody NewsRequest body,
            HttpServletRequest request
    ) {
        return newsService.update(bearerToken(request), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        newsService.delete(bearerToken(request), id);
        return ResponseEntity.noContent().build();
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : "";
    }

    @ExceptionHandler(InvalidFirebaseSessionException.class)
    public ResponseEntity<ApiError> invalidSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_SESSION", "Sign in with a verified SCOT account."));
    }

    @ExceptionHandler(EvaluationForbiddenException.class)
    public ResponseEntity<ApiError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Only HR and Administration can publish or manage news."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_NEWS_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(EmployeeLookupException.class)
    public ResponseEntity<ApiError> newsUnavailable() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("NEWS_SERVICE_UNAVAILABLE", "Unable to load or update SCOT news."));
    }
}
