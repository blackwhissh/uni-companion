package com.unicompanion.learning.lms;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic in-memory LMS for local/dev/tests — no Moodle credentials required.
 * Use external course id {@code demo-moodle-course} to pull two sample PDFs.
 */
@Component
public class FakeLmsClient implements LmsClient {

    public static final String DEMO_COURSE_ID = "demo-moodle-course";

    private final Map<String, CatalogEntry> demoFiles;

    public FakeLmsClient() {
        CatalogEntry lecture1 = entry("file-consensus", "Consensus", "01-consensus.pdf", "Consensus keeps replicas consistent.");
        CatalogEntry lecture2 = entry("file-replication", "Replication", "02-replication.pdf", "Replication copies state across nodes.");
        this.demoFiles = Map.of(
                lecture1.file().externalFileId(), lecture1,
                lecture2.file().externalFileId(), lecture2
        );
    }

    @Override
    public LmsProvider provider() {
        return LmsProvider.FAKE;
    }

    @Override
    public List<LmsRemoteFile> listFiles(String externalCourseId) {
        if (!DEMO_COURSE_ID.equals(externalCourseId)) {
            return List.of();
        }
        return demoFiles.values().stream().map(CatalogEntry::file).toList();
    }

    @Override
    public byte[] download(String externalCourseId, String externalFileId) {
        if (!DEMO_COURSE_ID.equals(externalCourseId)) {
            throw new IllegalArgumentException("Unknown LMS course: " + externalCourseId);
        }
        CatalogEntry entry = demoFiles.get(externalFileId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown LMS file: " + externalFileId);
        }
        return entry.bytes();
    }

    private static CatalogEntry entry(String id, String title, String fileName, String body) {
        byte[] pdf = minimalPdf(title + " — " + body);
        LmsRemoteFile file = new LmsRemoteFile(
                id,
                title,
                fileName,
                "application/pdf",
                sha256(pdf)
        );
        return new CatalogEntry(file, pdf);
    }

    private static byte[] minimalPdf(String line) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(line.length() > 80 ? line.substring(0, 80) : line);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException ex) {
            return HexFormat.of().formatHex(bytes).substring(0, Math.min(32, bytes.length));
        }
    }

    private record CatalogEntry(LmsRemoteFile file, byte[] bytes) {
    }
}
