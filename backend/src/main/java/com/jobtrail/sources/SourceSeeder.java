package com.jobtrail.sources;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SourceSeeder implements ApplicationRunner {
    private final JobSourceRepository repository;
    public SourceSeeder(JobSourceRepository repository) { this.repository = repository; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() != 0) return;
        String[][] boards = {
            {"Cloudflare", "GREENHOUSE", "cloudflare"}, {"Datadog", "GREENHOUSE", "datadog"},
            {"Discord", "GREENHOUSE", "discord"}, {"Duolingo", "GREENHOUSE", "duolingo"},
            {"Figma", "GREENHOUSE", "figma"}, {"MongoDB", "GREENHOUSE", "mongodb"},
            {"Reddit", "GREENHOUSE", "reddit"}, {"Samsara", "GREENHOUSE", "samsara"},
            {"Sentry", "GREENHOUSE", "sentry"}, {"Stripe", "GREENHOUSE", "stripe"},
            {"Twilio", "GREENHOUSE", "twilio"}, {"Vercel", "GREENHOUSE", "vercel"},
            {"Palantir", "LEVER", "palantir"}, {"Plaid", "LEVER", "plaid"},
            {"Spotify", "LEVER", "spotify"}
        };
        for (String[] board : boards) {
            JobSource source = new JobSource();
            source.setName(board[0]); source.setProvider(SourceProvider.valueOf(board[1]));
            source.setBoardKey(board[2]); source.setEnabled(true); repository.save(source);
        }
    }
}

