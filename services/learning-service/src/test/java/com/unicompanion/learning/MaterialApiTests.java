package com.unicompanion.learning;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MaterialApiTests {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PLATFORM_ADMIN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ADMIN = JwtTestSupport.bearer(ADMIN_ID, "admin@uni-companion.test", "COURSE_ADMIN");
    private static final String OTHER_ADMIN = JwtTestSupport.bearer(OTHER_ADMIN_ID, "other@uni-companion.test", "COURSE_ADMIN");
    private static final String PLATFORM_ADMIN = JwtTestSupport.bearer(
            PLATFORM_ADMIN_ID, "platform@uni-companion.test", "ADMIN", "COURSE_ADMIN");
    private static final String STUDENT = JwtTestSupport.bearer(STUDENT_ID, "ada@uni.test", "STUDENT");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void courseAdminUploadStartsUnpublishedAndUploaded() throws Exception {
        String courseId = createCourse("MAT-UP");

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(pdf("lecture.pdf", "Consensus algorithms"))
                        .param("title", "Lecture 1")
                        .header("Authorization", ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lecture 1"))
                .andExpect(jsonPath("$.fileName").value("lecture.pdf"))
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.visibility").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.processingStatus").value("UPLOADED"));
    }

    @Test
    void studentCannotUploadOrPublishMaterial() throws Exception {
        String courseId = createCourse("MAT-DENY");
        String materialId = upload(courseId, "Lecture", "lecture.pdf", pdfBytes("Hello"));

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(pdf("stolen.pdf", "Stolen"))
                        .header("Authorization", STUDENT))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestCannotUploadOrListMaterials() throws Exception {
        String courseId = createCourse("MAT-AUTH");

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(pdf("lecture.pdf", "Hello")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/courses/" + courseId + "/materials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonPdfUploadIsRejected() throws Exception {
        String courseId = createCourse("MAT-TYPE");

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherAdminCannotUploadToACourseTheyDoNotOwn() throws Exception {
        String courseId = createCourse("MAT-OWN");

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(pdf("lecture.pdf", "Hello"))
                        .header("Authorization", OTHER_ADMIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrolledStudentSeesMaterialOnlyWhenReadyAndPublished() throws Exception {
        String courseId = createCourse("MAT-SEE");
        publishCourse(courseId);
        enroll(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Consensus algorithms"));

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Lecture 1')].visibility").value("UNPUBLISHED"))
                .andExpect(jsonPath("$[?(@.title == 'Lecture 1')].processingStatus").value("READY"));

        String hidden = listMaterials(courseId, STUDENT);
        assertThat(hidden).doesNotContain("Lecture 1");

        publishMaterial(materialId);

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Lecture 1')].visibility").value("PUBLISHED"))
                .andExpect(jsonPath("$[?(@.title == 'Lecture 1')].processingStatus").value("READY"));

        unpublishMaterial(materialId);

        assertThat(listMaterials(courseId, STUDENT)).doesNotContain("Lecture 1");
    }

    @Test
    void publishedMaterialThatFailedProcessingStaysHidden() throws Exception {
        String courseId = createCourse("MAT-FAIL");
        publishCourse(courseId);
        enroll(courseId);
        String materialId = upload(courseId, "Broken slides", "broken.pdf", "not a pdf".getBytes());

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Broken slides')].processingStatus").value("FAILED"));

        publishMaterial(materialId);

        assertThat(listMaterials(courseId, STUDENT)).doesNotContain("Broken slides");
    }

    @Test
    void unenrolledStudentCannotListMaterials() throws Exception {
        String courseId = createCourse("MAT-ENR");
        publishCourse(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Hello"));
        publishMaterial(materialId);

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", STUDENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void readyMaterialChunksHaveEmbeddings() throws Exception {
        String courseId = createCourse("MAT-EMB");
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Consensus algorithms"));

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Lecture 1')].processingStatus").value("READY"));

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        select page_number, chunk_index, embedding
                        from material_chunks
                        where material_id = ?::uuid
                        order by chunk_index
                        """,
                materialId);

        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            assertThat(row.get("embedding")).as("chunk %s", row.get("chunk_index")).isNotNull();
        }
        Integer dimensions = jdbc.queryForObject(
                """
                        select vector_dims(embedding)
                        from material_chunks
                        where material_id = ?::uuid
                        limit 1
                        """,
                Integer.class,
                materialId);
        assertThat(dimensions).isEqualTo(768);
    }

    @Test
    void enrolledStudentDownloadsThePublishedPdf() throws Exception {
        String courseId = createCourse("MAT-DL");
        publishCourse(courseId);
        enroll(courseId);
        byte[] pdf = pdfBytes("Consensus algorithms");
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdf);
        publishMaterial(materialId);

        mockMvc.perform(get("/api/materials/" + materialId + "/file").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("lecture.pdf")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void studentCannotDownloadAMaterialTheyCannotSee() throws Exception {
        String courseId = createCourse("MAT-HID2");
        publishCourse(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Hidden"));
        publishMaterial(materialId);

        mockMvc.perform(get("/api/materials/" + materialId + "/file").header("Authorization", STUDENT))
                .andExpect(status().isForbidden());

        enroll(courseId);
        unpublishMaterial(materialId);

        mockMvc.perform(get("/api/materials/" + materialId + "/file").header("Authorization", STUDENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseAdminCanListAndDownloadMaterialsWithoutOwningTheCourse() throws Exception {
        String courseId = createCourse("MAT-FILE");
        byte[] pdf = pdfBytes("Draft slides");
        String materialId = upload(courseId, "Draft", "draft.pdf", pdf);

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", OTHER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Draft')].fileName").value("draft.pdf"));

        mockMvc.perform(get("/api/materials/" + materialId + "/file").header("Authorization", OTHER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf));

        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", OTHER_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "other.pdf", "application/pdf", pdfBytes("Other")))
                        .param("title", "Other upload")
                        .header("Authorization", OTHER_ADMIN))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminCanUploadMaterialWithoutOwningTheCourse() throws Exception {
        String courseId = createCourse("MAT-UPADM");
        byte[] pdf = pdfBytes("Platform upload");

        mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "ops.pdf", "application/pdf", pdf))
                        .param("title", "Ops lecture")
                        .header("Authorization", PLATFORM_ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ops lecture"))
                .andExpect(jsonPath("$.fileName").value("ops.pdf"));
    }

    @Test
    void platformAdminCanChangeMaterialVisibilityWithoutOwningTheCourse() throws Exception {
        String courseId = createCourse("MAT-PADM");
        String materialId = upload(courseId, "Lecture", "lecture.pdf", pdfBytes("Hello admin"));

        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", PLATFORM_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));

        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", PLATFORM_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNPUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("UNPUBLISHED"));
    }

    @Test
    void ownerCanDeleteMaterialAndNonOwnerCourseAdminCannot() throws Exception {
        String courseId = createCourse("MAT-DEL");
        String materialId = upload(courseId, "To Delete", "delete-me.pdf", pdfBytes("Remove me"));

        mockMvc.perform(delete("/api/materials/" + materialId).header("Authorization", OTHER_ADMIN))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/materials/" + materialId).header("Authorization", ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + materialId + "')]").isEmpty());

        mockMvc.perform(get("/api/materials/" + materialId + "/file").header("Authorization", ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformAdminCanDeleteMaterialWithoutOwningTheCourse() throws Exception {
        String courseId = createCourse("MAT-DELADM");
        String materialId = upload(courseId, "Admin Delete", "admin-del.pdf", pdfBytes("Admin remove"));

        mockMvc.perform(delete("/api/materials/" + materialId).header("Authorization", PLATFORM_ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + materialId + "')]").isEmpty());
    }

    @Test
    void studentCannotListMaterialsForAnUnpublishedCourse() throws Exception {
        String courseId = createCourse("MAT-HIDE");
        upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Hello"));

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", STUDENT))
                .andExpect(status().isNotFound());
    }

    private String createCourse(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Course %s","code":"%s","term":"2026WS"}
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String upload(String courseId, String title, String fileName, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", fileName, "application/pdf", bytes))
                        .param("title", title)
                        .header("Authorization", ADMIN))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void publishCourse(String courseId) throws Exception {
        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    private void enroll(String courseId) throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());
    }

    private void publishMaterial(String materialId) throws Exception {
        changeMaterialVisibility(materialId, "PUBLISHED");
    }

    private void unpublishMaterial(String materialId) throws Exception {
        changeMaterialVisibility(materialId, "UNPUBLISHED");
    }

    private void changeMaterialVisibility(String materialId, String visibility) throws Exception {
        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"" + visibility + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value(visibility));
    }

    private String listMaterials(String courseId, String bearer) throws Exception {
        return mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static MockMultipartFile pdf(String fileName, String text) throws Exception {
        return new MockMultipartFile("file", fileName, "application/pdf", pdfBytes(text));
    }

    private static byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
