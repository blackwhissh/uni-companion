package com.unicompanion.learning.lms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moodle site REST client using a service account token ({@code webservice/rest}).
 * Enable with {@code unicompanion.lms.moodle.enabled=true} plus base URL and token.
 */
@Component
@ConditionalOnProperty(prefix = "unicompanion.lms.moodle", name = "enabled", havingValue = "true")
public class MoodleLmsClient implements LmsClient {

    private static final Logger log = LoggerFactory.getLogger(MoodleLmsClient.class);

    private final MoodleProperties properties;
    private final RestClient http;
    private final JsonMapper json;

    public MoodleLmsClient(MoodleProperties properties, RestClient.Builder restClientBuilder, JsonMapper json) {
        this.properties = properties;
        this.http = restClientBuilder.build();
        this.json = json;
        log.info("Moodle LMS client ready baseUrl={}", properties.normalizedBaseUrl());
    }

    @Override
    public LmsProvider provider() {
        return LmsProvider.MOODLE;
    }

    @Override
    public List<LmsRemoteFile> listFiles(String externalCourseId) {
        return catalog(externalCourseId).stream().map(CatalogEntry::file).toList();
    }

    @Override
    public byte[] download(String externalCourseId, String externalFileId) {
        CatalogEntry entry = catalog(externalCourseId).stream()
                .filter(candidate -> externalFileId.equals(candidate.file().externalFileId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Moodle file: " + externalFileId));
        URI uri = URI.create(withToken(entry.fileUrl()));
        byte[] bytes = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Moodle returned an empty file for " + entry.file().fileName());
        }
        return bytes;
    }

    private List<CatalogEntry> catalog(String externalCourseId) {
        int courseId = parseCourseId(externalCourseId);
        JsonNode body = restCall("core_course_get_contents", Map.of("courseid", courseId));
        if (body == null || !body.isArray()) {
            throw new IllegalStateException("Moodle core_course_get_contents returned no sections.");
        }
        List<CatalogEntry> files = new ArrayList<>();
        for (JsonNode section : body) {
            JsonNode modules = section.path("modules");
            if (!modules.isArray()) {
                continue;
            }
            for (JsonNode module : modules) {
                if (!"resource".equalsIgnoreCase(text(module, "modname"))) {
                    continue;
                }
                String moduleName = text(module, "name");
                JsonNode contents = module.path("contents");
                if (!contents.isArray()) {
                    continue;
                }
                for (JsonNode file : contents) {
                    String type = text(file, "type");
                    String fileName = text(file, "filename");
                    String fileUrl = text(file, "fileurl");
                    if (!"file".equalsIgnoreCase(type) || fileName.isBlank() || fileUrl.isBlank()) {
                        continue;
                    }
                    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        continue;
                    }
                    String externalFileId = externalId(file, fileName, fileUrl);
                    String checksum = text(file, "timemodified");
                    if (checksum.isBlank()) {
                        checksum = sha256(fileUrl.getBytes(StandardCharsets.UTF_8));
                    }
                    String title = moduleName.isBlank() ? stripPdf(fileName) : moduleName;
                    files.add(new CatalogEntry(
                            new LmsRemoteFile(externalFileId, title, fileName, "application/pdf", checksum),
                            fileUrl
                    ));
                }
            }
        }
        log.info("Moodle listed {} PDF resources for courseId={}", files.size(), courseId);
        return files;
    }

    private JsonNode restCall(String function, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.normalizedBaseUrl() + "/webservice/rest/server.php")
                .queryParam("wstoken", properties.token())
                .queryParam("wsfunction", function)
                .queryParam("moodlewsrestformat", "json");
        params.forEach(builder::queryParam);
        String raw = http.get()
                .uri(builder.build(true).toUri())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Moodle returned an empty response for " + function);
        }
        JsonNode node = json.readTree(raw);
        if (node.isObject() && node.path("exception").isValueNode()) {
            throw new IllegalStateException("Moodle error: " + text(node, "message"));
        }
        return node;
    }

    private String withToken(String fileUrl) {
        if (fileUrl.contains("token=")) {
            return fileUrl;
        }
        return fileUrl + (fileUrl.contains("?") ? "&" : "?") + "token=" + properties.token();
    }

    private static String externalId(JsonNode file, String fileName, String fileUrl) {
        String filepath = text(file, "filepath");
        String modified = text(file, "timemodified");
        String id = filepath + fileName + "|" + modified;
        return id.startsWith("|") || id.equals(fileName + "|") ? fileUrl : id;
    }

    private static int parseCourseId(String externalCourseId) {
        try {
            return Integer.parseInt(externalCourseId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Moodle external course id must be numeric, got: " + externalCourseId);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String stripPdf(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            return HexFormat.of().formatHex(bytes).substring(0, Math.min(32, bytes.length));
        }
    }

    private record CatalogEntry(LmsRemoteFile file, String fileUrl) {
    }
}
