package com.unicompanion.learning.ingestion;

public class EmbeddingException extends RuntimeException {

    public enum Kind {
        QUOTA,
        FAILED
    }

    private final Kind kind;

    private EmbeddingException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public static EmbeddingException quota(Throwable cause) {
        return new EmbeddingException(
                Kind.QUOTA,
                "Vertex embedding quota was exceeded. Wait a minute, then try again.",
                cause
        );
    }

    public static EmbeddingException failed(String message, Throwable cause) {
        return new EmbeddingException(Kind.FAILED, message, cause);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isQuota() {
        return kind == Kind.QUOTA;
    }
}
