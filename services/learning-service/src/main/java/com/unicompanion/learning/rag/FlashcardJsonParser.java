package com.unicompanion.learning.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses model JSON into flashcard drafts. Tolerates optional markdown fences.
 * Implemented without Jackson so learning-service stays compatible with Boot's JSON stack.
 */
final class FlashcardJsonParser {

    private static final int FRONT_MAX = 500;

    private FlashcardJsonParser() {
    }

    static List<ChatModel.FlashcardDraft> parse(String raw, int limit) {
        if (raw == null || raw.isBlank() || limit <= 0) {
            return List.of();
        }
        String json = stripFences(raw.trim());
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            return List.of();
        }
        String arrayBody = json.substring(arrayStart + 1, arrayEnd);
        List<ChatModel.FlashcardDraft> cards = new ArrayList<>();
        int cursor = 0;
        while (cursor < arrayBody.length() && cards.size() < limit) {
            int objectStart = arrayBody.indexOf('{', cursor);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = findMatchingBrace(arrayBody, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = arrayBody.substring(objectStart, objectEnd + 1);
            String front = readStringField(object, "front");
            String back = readStringField(object, "back");
            if (!front.isBlank() && !back.isBlank()) {
                cards.add(new ChatModel.FlashcardDraft(truncate(front, FRONT_MAX), back.trim()));
            }
            cursor = objectEnd + 1;
        }
        return List.copyOf(cards);
    }

    private static int findMatchingBrace(String text, int openIndex) {
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

    private static String readStringField(String objectJson, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = objectJson.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }
        int colon = objectJson.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < objectJson.length() && Character.isWhitespace(objectJson.charAt(i))) {
            i++;
        }
        if (i >= objectJson.length() || objectJson.charAt(i) != '"') {
            return "";
        }
        i++;
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        while (i < objectJson.length()) {
            char ch = objectJson.charAt(i);
            if (escaped) {
                value.append(switch (ch) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    default -> ch;
                });
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                break;
            } else {
                value.append(ch);
            }
            i++;
        }
        return value.toString().trim();
    }

    private static String stripFences(String raw) {
        String value = raw;
        if (value.startsWith("```")) {
            int firstNl = value.indexOf('\n');
            if (firstNl > 0) {
                value = value.substring(firstNl + 1);
            }
            int fence = value.lastIndexOf("```");
            if (fence >= 0) {
                value = value.substring(0, fence);
            }
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1).trim() + "…";
    }
}
