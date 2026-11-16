package com.unicompanion.learning.lms;

/**
 * A file discovered in an external LMS course folder.
 */
public record LmsRemoteFile(
        String externalFileId,
        String title,
        String fileName,
        String contentType,
        String checksum
) {
}
