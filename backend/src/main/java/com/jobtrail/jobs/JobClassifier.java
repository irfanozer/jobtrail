package com.jobtrail.jobs;
import java.util.Locale;
import java.util.regex.Pattern;
public final class JobClassifier {
    private JobClassifier() {}
    private static boolean has(String text, String pattern) { return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find(); }
    public static JobLevel level(String title) {
        if (has(title, "\\b(senior|sr\\.?|staff|principal|lead|manager|director|architect|head|iii|iv)\\b")) return JobLevel.SENIOR;
        if (has(title, "\\b(junior|jr\\.?|entry[ -]level|new grad|graduate|associate)\\b|engineer(?:ing)?\\s+(?:i|1)\\b")) return JobLevel.JUNIOR;
        if (has(title, "\\b(mid[ -]level|intermediate)\\b|engineer(?:ing)?\\s+(?:ii|2)\\b")) return JobLevel.MID;
        return JobLevel.UNKNOWN;
    }
    public static Workplace workplace(String location) {
        if (has(location, "\\bhybrid\\b")) return Workplace.HYBRID;
        if (has(location, "\\bremote\\b")) return Workplace.REMOTE;
        if (has(location, "\\bon[ -]?site\\b")) return Workplace.ONSITE;
        return Workplace.UNKNOWN;
    }
    public static boolean engineeringRole(String title) {
        if (has(title, "\\b(account executive|sales representative|sales manager|sales engineer|marketing|recruiter|recruiting|customer success|product manager|program manager|developer advocate|developer relations)\\b")) return false;
        return has(title, "\\b(engineer|engineering|developer|devops|sre)\\b") &&
            has(title, "\\b(software|backend|back[ -]end|frontend|front[ -]end|full[ -]?stack|platform|devops|site reliability|sre|developer|data|machine learning|ai|security|cloud|infrastructure|quality|test)\\b");
    }
    public static int priority(Job job) {
        int score = engineeringRole(job.getTitle()) ? 100 : 0;
        score += switch (job.getLevel()) { case JUNIOR -> 35; case MID -> 30; case UNKNOWN -> 10; case SENIOR -> 0; };
        if (job.getWorkplace() == Workplace.REMOTE) score += 20;
        String place = job.getLocation().toLowerCase(Locale.ROOT);
        if (has(place, "\\b(arlington|washington[ ,]+d\\.?c\\.?|district of columbia|alexandria|reston|herndon|tysons|fairfax|mclean)\\b")) score += 20;
        if (has(place, "\\b(united states|usa|u\\.s\\.|us remote)\\b")) score += 10;
        return score;
    }
}

