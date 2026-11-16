package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Minimal JSON encode/decode for persisted Q&A payloads (no Jackson on the compile classpath).
 */
final class QaTurnJson {

    private QaTurnJson() {
    }

    static String citations(List<RagService.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < citations.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            RagService.Citation citation = citations.get(i);
            out.append('{')
                    .append("\"materialId\":").append(quote(citation.materialId().toString())).append(',')
                    .append("\"title\":").append(quote(citation.title())).append(',')
                    .append("\"sectionHint\":").append(nullableQuote(citation.sectionHint())).append(',')
                    .append("\"pageNumber\":").append(citation.pageNumber()).append(',')
                    .append("\"excerpt\":").append(quote(citation.excerpt()))
                    .append('}');
        }
        return out.append(']').toString();
    }

    static List<RagService.Citation> parseCitations(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        List<RagService.Citation> out = new ArrayList<>();
        int i = 0;
        char[] chars = json.toCharArray();
        while (i < chars.length) {
            if (chars[i] != '{') {
                i++;
                continue;
            }
            int end = findMatchingBrace(chars, i);
            if (end < 0) {
                break;
            }
            String object = json.substring(i, end + 1);
            UUID materialId = UUID.fromString(requiredString(object, "materialId"));
            String title = requiredString(object, "title");
            String sectionHint = optionalString(object, "sectionHint");
            int pageNumber = requiredInt(object, "pageNumber");
            String excerpt = requiredString(object, "excerpt");
            out.add(new RagService.Citation(materialId, title, sectionHint, pageNumber, excerpt));
            i = end + 1;
        }
        return List.copyOf(out);
    }

    static String materialIds(List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < materialIds.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(materialIds.get(i).toString()));
        }
        return out.append(']').toString();
    }

    private static int findMatchingBrace(char[] chars, int open) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = open; i < chars.length; i++) {
            char ch = chars[i];
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String requiredString(String object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    private static String optionalString(String object, String key) {
        String needle = "\"" + key + "\":";
        int at = object.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int valueStart = at + needle.length();
        while (valueStart < object.length() && Character.isWhitespace(object.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= object.length()) {
            return null;
        }
        if (object.startsWith("null", valueStart)) {
            return null;
        }
        if (object.charAt(valueStart) != '"') {
            throw new IllegalArgumentException("Expected string for " + key);
        }
        return readQuoted(object, valueStart);
    }

    private static int requiredInt(String object, String key) {
        String needle = "\"" + key + "\":";
        int at = object.indexOf(needle);
        if (at < 0) {
            throw new IllegalArgumentException("Missing " + key);
        }
        int valueStart = at + needle.length();
        while (valueStart < object.length() && Character.isWhitespace(object.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < object.length() && (Character.isDigit(object.charAt(valueEnd)) || object.charAt(valueEnd) == '-')) {
            valueEnd++;
        }
        return Integer.parseInt(object.substring(valueStart, valueEnd));
    }

    private static String readQuoted(String source, int openQuote) {
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = openQuote + 1; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (escape) {
                out.append(switch (ch) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> ch;
                });
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                return out.toString();
            }
            out.append(ch);
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private static String nullableQuote(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        StringBuilder out = new StringBuilder(safe.length() + 2);
        out.append('"');
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
