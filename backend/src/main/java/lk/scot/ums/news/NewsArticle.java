package lk.scot.ums.news;

public record NewsArticle(
        String id,
        String title,
        String summary,
        String content,
        String category,
        String authorName,
        String authorEmail,
        String date,
        boolean isSpecial,
        String imageUrl,
        long createdAt
) {}
