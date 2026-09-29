package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.List;

final class QuizJsonParser {

    private static final int PROMPT_MAX = 1_000;
    private static final int OPTION_MAX = 500;

    private QuizJsonParser() {
    }

    static List<ChatModel.QuizQuestionDraft> parse(String raw, int limit) {
        if (raw == null || raw.isBlank() || limit <= 0) {
            return List.of();
        }
        String json = stripFences(raw.trim());
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return List.of();
        }

        String body = json.substring(arrayStart + 1, arrayEnd);
        List<ChatModel.QuizQuestionDraft> questions = new ArrayList<>();
        int cursor = 0;
        while (cursor < body.length() && questions.size() < limit) {
            int objectStart = body.indexOf('{', cursor);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = findMatching(body, objectStart, '{', '}');
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String prompt = readStringField(object, "prompt");
            List<String> options = readStringArrayField(object, "options");
            int correctIndex = readIntField(object, "correctIndex", -1);
            String explanation = readStringField(object, "explanation");
            if (!prompt.isBlank()
                    && options.size() == 4
                    && correctIndex >= 0
                    && correctIndex < options.size()
                    && !explanation.isBlank()) {
                questions.add(new ChatModel.QuizQuestionDraft(
                        truncate(prompt, PROMPT_MAX),
                        options.stream().map(option -> truncate(option, OPTION_MAX)).toList(),
                        correctIndex,
                        explanation.trim()
                ));
            }
            cursor = objectEnd + 1;
        }
        return List.copyOf(questions);
    }

    private static List<String> readStringArrayField(String object, String field) {
        int valueStart = fieldValueStart(object, field);
        if (valueStart < 0 || object.charAt(valueStart) != '[') {
            return List.of();
        }
        int end = findMatching(object, valueStart, '[', ']');
        if (end < 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int cursor = valueStart + 1;
        while (cursor < end) {
            while (cursor < end && (Character.isWhitespace(object.charAt(cursor)) || object.charAt(cursor) == ',')) {
                cursor++;
            }
            if (cursor >= end || object.charAt(cursor) != '"') {
                break;
            }
            ParsedString parsed = readString(object, cursor);
            if (parsed == null) {
                break;
            }
            values.add(parsed.value());
            cursor = parsed.endIndex() + 1;
        }
        return List.copyOf(values);
    }

    private static String readStringField(String object, String field) {
        int valueStart = fieldValueStart(object, field);
        if (valueStart < 0 || object.charAt(valueStart) != '"') {
            return "";
        }
        ParsedString parsed = readString(object, valueStart);
        return parsed == null ? "" : parsed.value().trim();
    }

    private static int readIntField(String object, String field, int fallback) {
        int start = fieldValueStart(object, field);
        if (start < 0) {
            return fallback;
        }
        int end = start;
        if (object.charAt(end) == '-') {
            end++;
        }
        while (end < object.length() && Character.isDigit(object.charAt(end))) {
            end++;
        }
        if (end == start) {
            return fallback;
        }
        try {
            return Integer.parseInt(object.substring(start, end));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int fieldValueStart(String object, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = object.indexOf(key);
        if (keyIndex < 0) {
            return -1;
        }
        int colon = object.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return -1;
        }
        int cursor = colon + 1;
        while (cursor < object.length() && Character.isWhitespace(object.charAt(cursor))) {
            cursor++;
        }
        return cursor < object.length() ? cursor : -1;
    }

    private static ParsedString readString(String text, int quoteIndex) {
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                value.append(switch (ch) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"', '\\', '/' -> ch;
                    default -> ch;
                });
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return new ParsedString(value.toString(), i);
            } else {
                value.append(ch);
            }
        }
        return null;
    }

    private static int findMatching(String text, int openIndex, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == open) {
                depth++;
            } else if (ch == close && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String stripFences(String raw) {
        if (!raw.startsWith("```")) {
            return raw;
        }
        int firstNewline = raw.indexOf('\n');
        String value = firstNewline < 0 ? raw : raw.substring(firstNewline + 1);
        int closingFence = value.lastIndexOf("```");
        return (closingFence < 0 ? value : value.substring(0, closingFence)).trim();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1).trim() + "…";
    }

    private record ParsedString(String value, int endIndex) {
    }
}
