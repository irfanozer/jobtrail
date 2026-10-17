package com.jobtrail.sources.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

final class BoardAdapterSupport {
    private static final Pattern BOARD_KEY = Pattern.compile("[a-zA-Z0-9_-]{1,100}");
    private static final int MAX_TEXT_LENGTH = 200_000;

    private BoardAdapterSupport() {
    }

    static RestClient client(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return builder.clone().requestFactory(factory).build();
    }

    static String boardKey(JobSource source, SourceProvider expected) {
        if (source == null || source.getProvider() != expected) {
            throw new IllegalArgumentException("The job source provider does not match this adapter.");
        }
        String key = source.getBoardKey();
        if (key == null || !BOARD_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Use a board key containing only letters, numbers, underscores, or hyphens.");
        }
        return key;
    }

    static JsonNode fetchJson(RestClient client, URI uri, String provider) {
        try {
            JsonNode body = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new JobSourceFetchException(provider + " returned HTTP "
                                + response.getStatusCode().value() + ". Try again later or check the board key.");
                    })
                    .body(JsonNode.class);
            if (body == null) {
                throw new JobSourceFetchException(provider + " returned an empty response.");
            }
            return body;
        } catch (ResourceAccessException exception) {
            throw new JobSourceFetchException(provider + " could not be reached within the connection or response limit. Try again later.");
        } catch (RestClientException exception) {
            throw new JobSourceFetchException(provider + " returned an unreadable response. Try again later.");
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() || value.isNumber() ? value.asText().strip() : "";
    }

    static String requiredText(JsonNode node, String field, String provider) {
        String value = text(node, field);
        if (value.isBlank()) {
            throw new JobSourceFetchException(provider + " returned a job without a required " + field + " field.");
        }
        return value;
    }

    static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        if (html.length() > MAX_TEXT_LENGTH) {
            throw new JobSourceFetchException("The job board returned a description larger than the import limit.");
        }
        String decoded = html;
        for (int pass = 0; pass < 3; pass++) {
            String next = Parser.unescapeEntities(decoded, false);
            if (next.equals(decoded)) {
                break;
            }
            decoded = next;
        }
        return Jsoup.parseBodyFragment(decoded).text().strip();
    }

    static String httpsUrl(String value, String provider) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.toASCIIString().length() > 2_000) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new JobSourceFetchException(provider + " returned a job without a safe HTTPS link.");
        }
    }

    static Instant timestamp(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
