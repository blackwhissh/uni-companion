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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class CourseApiTests {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PLATFORM_ADMIN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ADMIN = JwtTestSupport.bearer(ADMIN_ID, "admin@uni-companion.test", "COURSE_ADMIN");
    private static final String OTHER_PROF = JwtTestSupport.bearer(OTHER_ADMIN_ID, "other@uni-companion.test", "COURSE_ADMIN");
    private static final String PLATFORM_ADMIN = JwtTestSupport.bearer(PLATFORM_ADMIN_ID, "ops@uni-companion.test", "ADMIN", "COURSE_ADMIN");
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
    void courseAdminCreatesAnUnpublishedCourse() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("Distributed Systems", "CISS", "2026WS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Distributed Systems"))
                .andExpect(jsonPath("$.code").value("CISS"))
                .andExpect(jsonPath("$.term").value("2026WS"))
                .andExpect(jsonPath("$.visibility").value("UNPUBLISHED"));
    }

    @Test
    void studentCannotCreateACourse() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("Stolen", "NOPE", "2026WS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestCannotCreateOrListCourses() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("Open", "OPEN", "2026WS")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void courseAdminSeesCoursesThatAreStillUnpublished() throws Exception {
        createCourse("CISS-MINE");

        mockMvc.perform(get("/api/courses").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'CISS-MINE')].visibility").value("UNPUBLISHED"));
    }

    @Test
    void studentCatalogHidesUnpublishedCoursesUntilAnAdminPublishes() throws Exception {
        String code = "CISS-VIS";
        String courseId = createCourse(code);

        String hidden = mockMvc.perform(get("/api/courses").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(hidden).doesNotContain(code);

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));

        mockMvc.perform(get("/api/courses").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].visibility").value("PUBLISHED"));
    }

    @Test
    void studentCannotPublishACourse() throws Exception {
        String courseId = createCourse("CISS-STU");

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void professorCanUnpublishOwnCourseButNotAnotherProfessors() throws Exception {
        String courseId = createCourse("CISS-OWN");
        publish(courseId);

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNPUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.owned").value(true));

        publish(courseId);

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", OTHER_PROF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNPUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminCanUnpublishAnyCourse() throws Exception {
        String courseId = createCourse("CISS-OPS");
        publish(courseId);

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", PLATFORM_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNPUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("UNPUBLISHED"))
                .andExpect(jsonPath("$.owned").value(false));
    }

    @Test
    void ownerCanDeleteCourseAndNonOwnerProfessorCannot() throws Exception {
        String courseId = createCourse("CISS-DEL");

        mockMvc.perform(delete("/api/courses/" + courseId).header("Authorization", OTHER_PROF))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + courseId + "')]").isEmpty());

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingACourseRemovesItsLecturesAndStudyRows() throws Exception {
        String courseId = createCourse("CISS-MAT");
        publish(courseId);
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());
        MvcResult uploaded = mockMvc.perform(multipart("/api/courses/" + courseId + "/materials")
                        .file(pdf("lecture.pdf", "Threads share an address space."))
                        .param("title", "Threads")
                        .header("Authorization", ADMIN))
                .andExpect(status().isCreated())
                .andReturn();
        String materialId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM material_chunks WHERE material_id = ?::uuid",
                Integer.class,
                UUID.fromString(materialId)
        )).isPositive();

        UUID quizId = UUID.randomUUID();
        UUID deckId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO quizzes (id, course_id, user_id, created_at, materials_key, active, practice_quiz)
                VALUES (?::uuid, ?::uuid, ?::uuid, now(), 'lecture', true, false)
                """, quizId, UUID.fromString(courseId), STUDENT_ID);
        jdbc.update("""
                INSERT INTO flashcard_decks (id, course_id, user_id, created_at, materials_key, active)
                VALUES (?::uuid, ?::uuid, ?::uuid, now(), 'lecture', true)
                """, deckId, UUID.fromString(courseId), STUDENT_ID);
        jdbc.update("""
                INSERT INTO quiz_progress (course_id, user_id, materials_key, quiz_id, updated_at)
                VALUES (?::uuid, ?::uuid, 'lecture', ?::uuid, now())
                """, UUID.fromString(courseId), STUDENT_ID, quizId);
        jdbc.update("""
                INSERT INTO flashcard_deck_progress (course_id, user_id, materials_key, deck_id, updated_at)
                VALUES (?::uuid, ?::uuid, 'lecture', ?::uuid, now())
                """, UUID.fromString(courseId), STUDENT_ID, deckId);

        mockMvc.perform(delete("/api/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isNotFound());
        assertThat(count("materials", courseId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM material_chunks WHERE material_id = ?::uuid",
                Integer.class,
                UUID.fromString(materialId)
        )).isZero();
        assertThat(count("quizzes", courseId)).isZero();
        assertThat(count("flashcard_decks", courseId)).isZero();
        assertThat(count("quiz_progress", courseId)).isZero();
        assertThat(count("flashcard_deck_progress", courseId)).isZero();
        assertThat(count("enrollments", courseId)).isZero();
    }

    @Test
    void platformAdminCanDeleteAnyCourse() throws Exception {
        String courseId = createCourse("CISS-DELADM");

        mockMvc.perform(delete("/api/courses/" + courseId).header("Authorization", PLATFORM_ADMIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogRemembersThatTheStudentEnrolled() throws Exception {
        String code = "CISS-JOIN";
        String courseId = createCourse(code);
        publish(courseId);

        mockMvc.perform(get("/api/courses").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].enrolled").value(false));

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].enrolled").value(true));
    }

    @Test
    void studentCanUnenrollAndLosesTheCourseMaterials() throws Exception {
        String code = "CISS-LEAVE";
        String courseId = createCourse(code);
        publish(courseId);
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].enrolled").value(false));

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", STUDENT))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void studentCannotUnenrollFromAnUnpublishedCourse() throws Exception {
        String courseId = createCourse("CISS-OUT");

        mockMvc.perform(delete("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCanEnrollOnlyInAPublishedCourse() throws Exception {
        String courseId = createCourse("CISS-ENR");

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isNotFound());

        publish(courseId);

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(courseId));

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId));
    }

    @Test
    void platformAdminSeesEnrollmentCountsAndOtherRolesCannot() throws Exception {
        String courseId = createCourse("CISS-ADMIN");
        publish(courseId);
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/courses").header("Authorization", STUDENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/courses").header("Authorization", ADMIN))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/courses").header("Authorization", PLATFORM_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'CISS-ADMIN')].enrollmentCount").value(1))
                .andExpect(jsonPath("$[?(@.code == 'CISS-ADMIN')].visibility").value("PUBLISHED"))
                .andExpect(jsonPath("$[?(@.code == 'CISS-ADMIN')].publishedReadyMaterials").value(0))
                .andExpect(jsonPath("$[?(@.code == 'CISS-ADMIN')].ownerId").value(ADMIN_ID.toString()));
    }

    @Test
    void platformAdminOrOwnerCanTransferCourseOwnership() throws Exception {
        String courseId = createCourse("CISS-HANDOFF");

        mockMvc.perform(patch("/api/courses/" + courseId + "/owner")
                        .header("Authorization", OTHER_PROF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + OTHER_ADMIN_ID + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/courses/" + courseId + "/owner")
                        .header("Authorization", PLATFORM_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + OTHER_ADMIN_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(false));

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", OTHER_PROF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true));

        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", OTHER_PROF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));
    }

    @Test
    void openApiSpecificationAndPrivateStudentNotesAreEnforced() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Uni Companion Learning Service API"))
                .andExpect(jsonPath("$.paths").isMap());

        String courseId = createCourse("CISS-NOTES");
        publish(courseId);

        mockMvc.perform(get("/api/notes/courses/" + courseId).header("Authorization", STUDENT))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());

        MvcResult created = mockMvc.perform(post("/api/notes/courses/" + courseId)
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Raft log invariant","body":"Leader completeness holds across elections."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Raft log invariant"))
                .andReturn();
        String noteId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/notes/courses/" + courseId).header("Authorization", ADMIN))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me/data").header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateNotes[?(@.title == 'Raft log invariant')]").isNotEmpty());

        mockMvc.perform(delete("/api/notes/courses/" + courseId + "/" + noteId)
                        .header("Authorization", STUDENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes/courses/" + courseId).header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void studentCannotOpenAnUnpublishedCourse() throws Exception {
        String courseId = createCourse("CISS-HIDE");

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", STUDENT))
                .andExpect(status().isNotFound());
    }

    private String createCourse(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(course("Course " + code, code, "2026WS")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void publish(String courseId) throws Exception {
        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    private int count(String table, String courseId) {
        String sql = "material_chunks".equals(table)
                ? """
                SELECT count(*) FROM material_chunks
                WHERE material_id IN (SELECT id FROM materials WHERE course_id = ?::uuid)
                """
                : "SELECT count(*) FROM " + table + " WHERE course_id = ?::uuid";
        Integer found = jdbc.queryForObject(sql, Integer.class, UUID.fromString(courseId));
        return found == null ? 0 : found;
    }

    private static MockMultipartFile pdf(String fileName, String text) throws Exception {
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
            return new MockMultipartFile("file", fileName, "application/pdf", out.toByteArray());
        }
    }

    private static String course(String title, String code, String term) {
        return """
                {"title":"%s","code":"%s","term":"%s"}
                """.formatted(title, code, term);
    }
}
