package lk.scot.ums.evaluation;

final class EvaluationScoring {
    private EvaluationScoring() {
    }

    static double finalScore(double primaryScore, double selfScore, double peerAverage) {
        double score = primaryScore * 0.75 + selfScore * 0.05 + peerAverage * 0.20;
        return Math.round(score * 100.0) / 100.0;
    }

    static String rating(double score) {
        if (score >= 90) return "Outstanding";
        if (score >= 80) return "Meets Expectations";
        if (score >= 50) return "Meets Minimal Expectations";
        if (score >= 41) return "Needs Improvement";
        if (score < 40) return "Unsatisfactory";
        return "Unrated";
    }
}
