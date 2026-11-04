package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.matching.domain.ContactStatus;

import java.util.UUID;

/**
 * An email is visible only to the two people on an accepted request, and only the other person's address.
 */
public final class ContactExchange {

    private ContactExchange() {
    }

    public static String emailForViewer(
            ContactStatus status,
            UUID viewerId,
            UUID requesterId,
            UUID recipientId,
            String requesterEmail,
            String recipientEmail
    ) {
        if (status != ContactStatus.ACCEPTED || viewerId == null) {
            return null;
        }
        if (viewerId.equals(requesterId)) {
            return blankToNull(recipientEmail);
        }
        if (viewerId.equals(recipientId)) {
            return blankToNull(requesterEmail);
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
