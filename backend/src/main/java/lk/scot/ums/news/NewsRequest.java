package lk.scot.ums.news;

public record NewsRequest(
        String title,
        String summary,
        String content,
        String category,
        boolean isSpecial,
        String imageUrl
) {}
