package com.unicompanion.learning.lms;

import java.util.List;

/**
 * Port for pulling course files from an LMS. Real Moodle/ILIAS adapters plug in here later.
 */
public interface LmsClient {

    LmsProvider provider();

    List<LmsRemoteFile> listFiles(String externalCourseId);

    byte[] download(String externalCourseId, String externalFileId);
}
