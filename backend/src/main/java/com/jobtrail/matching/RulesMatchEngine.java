package com.jobtrail.matching;

import com.jobtrail.profile.ProfileResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RulesMatchEngine {
    private static final List<Skill> KNOWN_SKILLS = List.of(
            skill("Java", "Java"), skill("JavaScript", "JavaScript", "JS"),
            skill("TypeScript", "TypeScript"), skill("Python", "Python"),
            skill("C++", "C++"), skill("C#", "C#", "C Sharp"),
            skill(".NET", ".NET", "dotnet"), skill("Golang", "Golang", "Go programming"),
            skill("SQL", "SQL"), skill("PostgreSQL", "PostgreSQL", "Postgres"),
            skill("MySQL", "MySQL"), skill("MongoDB", "MongoDB"), skill("Redis", "Redis"),
            skill("Docker", "Docker"), skill("Kubernetes", "Kubernetes", "K8s"),
            skill("AWS", "AWS", "Amazon Web Services"),
            skill("Azure", "Azure"), skill("Google Cloud", "Google Cloud", "GCP"),
            skill("React", "React", "React.js", "ReactJS"),
            skill("Angular", "Angular"), skill("Vue", "Vue.js", "VueJS"),
            skill("Spring Boot", "Spring Boot", "SpringBoot"),
            skill("Spring", "Spring framework"),
            skill("Node.js", "Node.js", "NodeJS"),
            skill("FastAPI", "FastAPI"), skill("Django", "Django"),
            skill("Linux", "Linux"), skill("Git", "Git"),
            skill("Terraform", "Terraform"), skill("Kafka", "Kafka", "Apache Kafka"),
            skill("REST", "REST API", "REST APIs", "RESTful"),
            skill("GraphQL", "GraphQL"), skill("CI/CD", "CI/CD", "CI / CD"),
            skill("JUnit", "JUnit"), skill("pytest", "pytest"),
            skill("HTML", "HTML", "HTML5"), skill("CSS", "CSS", "CSS3"));

    public record JobDetails(Long id, String title, String location, String description,
                             String level, String workplace) {
    }

    public MatchResponse compare(JobDetails job, ProfileResponse profile) {
        List<MatchResponse.Finding> strengths = new ArrayList<>();
        List<MatchResponse.Finding> gaps = new ArrayList<>();
        List<MatchResponse.Finding> unknowns = new ArrayList<>();
        String text = value(job.title()) + "\n" + value(job.description());
        Map<String, Skill> vocabulary = new LinkedHashMap<>();
        KNOWN_SKILLS.forEach(skill -> vocabulary.put(skill.name(), skill));
        for (String statedSkill : profile.skills()) {
            Skill recognized = recognize(statedSkill);
            vocabulary.putIfAbsent(recognized.name(), recognized);
        }

        int mentionedSkills = 0;
        for (Skill skill : vocabulary.values()) {
            Matcher mention = skill.pattern().matcher(text);
            if (!mention.find()) {
                continue;
            }
            mentionedSkills++;
            int profileIndex = indexOfSkill(profile.skills(), skill.name());
            MatchResponse.Evidence jobEvidence = new MatchResponse.Evidence(
                    "JOB_TEXT", "job:" + job.id() + ":title-and-description",
                    snippet(text, mention.start(), mention.end()));
            if (profileIndex >= 0) {
                strengths.add(new MatchResponse.Finding("SKILL",
                        "Your profile lists " + skill.name() + ", which appears in the job text.",
                        List.of(new MatchResponse.Evidence("PROFILE_SKILL", "skills/" + profileIndex,
                                profile.skills().get(profileIndex)), jobEvidence)));
            } else {
                gaps.add(new MatchResponse.Finding("SKILL",
                        skill.name() + " appears in the job text but is not listed in your profile.",
                        List.of(jobEvidence)));
            }
        }

        if (profile.skills().isEmpty()) {
            unknowns.add(finding("PROFILE", "No skills are saved in your profile. Add only skills you can support."));
        }
        if (value(job.description()).isBlank()) {
            unknowns.add(finding("REQUIREMENTS", "This posting has no description available to compare."));
        } else if (mentionedSkills == 0) {
            unknowns.add(finding("REQUIREMENTS", "No supported skill terms were identified. Review the original posting."));
        }
        comparePreferences(job, profile, strengths, unknowns);
        unknowns.add(finding("REQUIREMENTS",
                "Keyword mentions do not establish mandatory requirements. Review the context and required versus preferred qualifications."));
        unknowns.add(finding("EXPERIENCE",
                "Rules do not assess skill depth, years of experience, work authorization, or hiring probability. Career facts do not automatically become skill claims."));
        return new MatchResponse(job.id(), profile.version(), "RULES_BASED",
                "Rules-based match; AI not enabled", List.copyOf(strengths), List.copyOf(gaps), List.copyOf(unknowns));
    }

    private void comparePreferences(JobDetails job, ProfileResponse profile,
                                    List<MatchResponse.Finding> strengths,
                                    List<MatchResponse.Finding> unknowns) {
        if (profile.targetRoles().isEmpty()) {
            unknowns.add(finding("ROLE", "No target roles are saved in your profile."));
        } else {
            boolean found = false;
            for (int index = 0; index < profile.targetRoles().size(); index++) {
                String role = profile.targetRoles().get(index);
                if (literalPattern(role).matcher(value(job.title())).find()) {
                    strengths.add(new MatchResponse.Finding("ROLE",
                            "The job title contains your target role: " + role + ".",
                            List.of(new MatchResponse.Evidence("PROFILE_PREFERENCE", "targetRoles/" + index, role),
                                    new MatchResponse.Evidence("JOB_TEXT", "job:" + job.id() + ":title", value(job.title())))));
                    found = true;
                }
            }
            if (!found) {
                unknowns.add(finding("ROLE", "The title does not explicitly contain a saved target role; related titles need review."));
            }
        }
        if (profile.preferredLocations().isEmpty()) {
            unknowns.add(finding("LOCATION", "No preferred locations are saved in your profile."));
            return;
        }
        boolean found = false;
        for (int index = 0; index < profile.preferredLocations().size(); index++) {
            String location = profile.preferredLocations().get(index);
            if (literalPattern(location).matcher(value(job.location())).find()) {
                strengths.add(new MatchResponse.Finding("LOCATION",
                        "The listed location contains your preference: " + location + ". Confirm the employer's hiring boundaries.",
                        List.of(new MatchResponse.Evidence("PROFILE_PREFERENCE", "preferredLocations/" + index, location),
                                new MatchResponse.Evidence("JOB_TEXT", "job:" + job.id() + ":location", value(job.location())))));
                found = true;
            }
        }
        if (!found) {
            unknowns.add(finding("LOCATION", "The listed location does not explicitly match your preferences. Remote labels do not establish geographic eligibility."));
        }
    }

    private static int indexOfSkill(List<String> skills, String canonical) {
        for (int index = 0; index < skills.size(); index++) {
            if (recognize(skills.get(index)).name().equalsIgnoreCase(canonical)) {
                return index;
            }
        }
        return -1;
    }

    private static Skill recognize(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("go")) {
            return KNOWN_SKILLS.stream().filter(skill -> skill.name().equals("Golang")).findFirst().orElseThrow();
        }
        return KNOWN_SKILLS.stream()
                .filter(skill -> skill.name().equalsIgnoreCase(normalized)
                        || skill.aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
                .findFirst().orElseGet(() -> skill(value.strip(), value.strip()));
    }

    private static Skill skill(String name, String... aliases) {
        String alternatives = String.join("|", java.util.Arrays.stream(aliases).map(Pattern::quote).toList());
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + alternatives + ")(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new Skill(name, List.of(aliases), pattern);
    }

    private static Pattern literalPattern(String value) {
        return skill(value, value).pattern();
    }

    private static String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 70);
        int to = Math.min(text.length(), end + 110);
        return (from > 0 ? "..." : "") + text.substring(from, to).replaceAll("\\s+", " ").strip()
                + (to < text.length() ? "..." : "");
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static MatchResponse.Finding finding(String type, String message) {
        return new MatchResponse.Finding(type, message, List.of());
    }

    private record Skill(String name, List<String> aliases, Pattern pattern) {
    }
}
