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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StudyApiTests {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STUDENT_2_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID STUDENT_3_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String ADMIN = JwtTestSupport.bearer(ADMIN_ID, "admin@uni-companion.test", "COURSE_ADMIN");
    private static final String STUDENT = JwtTestSupport.bearer(STUDENT_ID, "ada@uni.test", "STUDENT");
    private static final String STUDENT_2 = JwtTestSupport.bearer(STUDENT_2_ID, "alan@uni.test", "STUDENT");
    private static final String STUDENT_3 = JwtTestSupport.bearer(STUDENT_3_ID, "grace@uni.test", "STUDENT");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unenrolledStudentCannotGenerateFlashcards() throws Exception {
        ReadyCourse ready = readyCourse("STDY-DENY");

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateWithoutMaterialsReturnsBadRequest() throws Exception {
        ReadyCourse ready = readyCourse("STDY-EMPTY");
        enroll(ready.courseId(), STUDENT);

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrolledStudentCanGenerateAndListFlashcards() throws Exception {
        ReadyCourse ready = readyCourse("STDY-CARDS");
        enroll(ready.courseId(), STUDENT);

        MvcResult generated = mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(ready.courseId()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.availableVersions").value(1))
                .andExpect(jsonPath("$.maxVersions").value(3))
                .andExpect(jsonPath("$.cards", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.cards[0].front").isString())
                .andExpect(jsonPath("$.cards[0].back").isString())
                .andExpect(jsonPath("$.delivery").value("CREATED"))
                .andReturn();

        String deckId = JsonPath.read(generated.getResponse().getContentAsString(), "$.id");
        Integer membershipCount = jdbc.queryForObject(
                "SELECT count(*) FROM flashcard_deck_materials WHERE deck_id = ?::uuid",
                Integer.class,
                deckId
        );
        org.assertj.core.api.Assertions.assertThat(membershipCount).isEqualTo(1);

        mockMvc.perform(get("/api/study/courses/" + ready.courseId() + "/flashcards")
                        .param("materialIds", ready.materialId())
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void enrolledStudentCanGenerateQuizAndSubmitAttempt() throws Exception {
        ReadyCourse ready = readyCourse("STDY-QUIZ");
        enroll(ready.courseId(), STUDENT);

        MvcResult generated = mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/quizzes/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.questions[0].prompt").isString())
                .andExpect(jsonPath("$.questions[0].options", hasSize(4)))
                .andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
                .andReturn();

        String quizId = JsonPath.read(generated.getResponse().getContentAsString(), "$.id");
        String questionId = JsonPath.read(generated.getResponse().getContentAsString(), "$.questions[0].id");

        mockMvc.perform(post("/api/study/quizzes/" + quizId + "/submit")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"%s","selectedIndex":0}]}
                                """.formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.reviews", hasSize(1)))
                .andExpect(jsonPath("$.reviews[0].questionId").value(questionId))
                .andExpect(jsonPath("$.reviews[0].correctIndex").value(0))
                .andExpect(jsonPath("$.reviews[0].correct").value(true))
                .andExpect(jsonPath("$.reviews[0].correctOption").isString())
                .andExpect(jsonPath("$.reviews[0].explanation").isString());
    }

    @Test
    void versionPoolServesFirstThenNextExistingBeforeNewAiGeneration() throws Exception {
        String courseId = createCourse("STDY-SHARE");
        publishCourse(courseId);
        String materialA = upload(courseId, "Lecture A", "a.pdf", pdfBytes("Consensus algorithms keep replicas consistent."));
        String materialB = upload(courseId, "Lecture B", "b.pdf", pdfBytes("Raft elects a leader for log replication."));
        publishMaterial(materialA);
        publishMaterial(materialB);
        enroll(courseId, STUDENT);
        enroll(courseId, STUDENT_2);

        MvcResult deckV1 = mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isCreated())
                .andReturn();
        String deckId1 = JsonPath.read(deckV1.getResponse().getContentAsString(), "$.id");

        MvcResult deckV2 = mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate?force=true")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isCreated())
                .andReturn();
        String deckId2 = JsonPath.read(deckV2.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId1));

        mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate?force=true")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId2))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.availableVersions").value(2));

        MvcResult deckV3 = mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate?force=true")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.availableVersions").value(3))
                .andReturn();
        String deckId3 = JsonPath.read(deckV3.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate?force=true")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId3))
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate?force=true")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId1))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.availableVersions").value(3));

        mockMvc.perform(get("/api/study/courses/" + courseId + "/flashcards")
                        .param("materialIds", materialA)
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId3));

        mockMvc.perform(post("/api/study/courses/" + courseId + "/flashcards/generate")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(deckId1)))
                .andExpect(jsonPath("$.id", not(deckId2)));

        MvcResult quizV1 = mockMvc.perform(post("/api/study/courses/" + courseId + "/quizzes/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA, materialB)))
                .andExpect(status().isCreated())
                .andReturn();
        String quizId1 = JsonPath.read(quizV1.getResponse().getContentAsString(), "$.id");

        MvcResult quizV2 = mockMvc.perform(post("/api/study/courses/" + courseId + "/quizzes/generate?force=true")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA, materialB)))
                .andExpect(status().isCreated())
                .andReturn();
        String quizId2 = JsonPath.read(quizV2.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/study/courses/" + courseId + "/quizzes/generate")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialB, materialA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId1));

        mockMvc.perform(post("/api/study/courses/" + courseId + "/quizzes/generate?force=true")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(materialA, materialB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId2));
    }

    @Test
    void versionsCanBeSelectedAndRepeatedQualityReportsRetireBadContent() throws Exception {
        ReadyCourse ready = readyCourse("STDY-QUALITY");
        enroll(ready.courseId(), STUDENT);
        enroll(ready.courseId(), STUDENT_2);
        enroll(ready.courseId(), STUDENT_3);

        MvcResult generated = mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isCreated())
                .andReturn();
        String deckId = JsonPath.read(generated.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/select")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialIds":["%s"],"versionId":"%s"}
                                """.formatted(ready.materialId(), deckId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deckId))
                .andExpect(jsonPath("$.delivery").value("SELECTED"));

        reportFlashcards(deckId, STUDENT).andExpect(jsonPath("$.retired").value(false));
        reportFlashcards(deckId, STUDENT_2).andExpect(jsonPath("$.retired").value(false));
        reportFlashcards(deckId, STUDENT_3).andExpect(jsonPath("$.retired").value(true));

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(deckId)))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unpublishedSelectedMaterialCannotReuseCachedStudyContent() throws Exception {
        ReadyCourse ready = readyCourse("STDY-LIFECYCLE");
        enroll(ready.courseId(), STUDENT);

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/materials/" + ready.materialId() + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNPUBLISHED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentFirstRequestsCreateOnlyOneSharedVersion() throws Exception {
        ReadyCourse ready = readyCourse("STDY-CONCURRENT");
        enroll(ready.courseId(), STUDENT);
        enroll(ready.courseId(), STUDENT_2);
        CountDownLatch readyToStart = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = executor.submit(() ->
                    concurrentGenerate(ready, STUDENT, readyToStart, start)
            );
            Future<MvcResult> second = executor.submit(() ->
                    concurrentGenerate(ready, STUDENT_2, readyToStart, start)
            );
            readyToStart.await();
            start.countDown();

            String firstId = JsonPath.read(first.get().getResponse().getContentAsString(), "$.id");
            String secondId = JsonPath.read(second.get().getResponse().getContentAsString(), "$.id");
            org.assertj.core.api.Assertions.assertThat(firstId).isEqualTo(secondId);
        }

        Integer deckCount = jdbc.queryForObject(
                "SELECT count(*) FROM flashcard_decks WHERE course_id = ?::uuid",
                Integer.class,
                ready.courseId()
        );
        org.assertj.core.api.Assertions.assertThat(deckCount).isEqualTo(1);
    }

    private MvcResult concurrentGenerate(
            ReadyCourse ready,
            String bearer,
            CountDownLatch readyToStart,
            CountDownLatch start
    ) throws Exception {
        readyToStart.countDown();
        start.await();
        return mockMvc.perform(post("/api/study/courses/" + ready.courseId() + "/flashcards/generate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody(ready.materialId())))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions reportFlashcards(String deckId, String bearer)
            throws Exception {
        return mockMvc.perform(post("/api/study/flashcards/" + deckId + "/report")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate and unclear cards\"}"))
                .andExpect(status().isOk());
    }

    private ReadyCourse readyCourse(String code) throws Exception {
        String courseId = createCourse(code);
        publishCourse(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Consensus algorithms keep replicas consistent."));
        publishMaterial(materialId);
        return new ReadyCourse(courseId, materialId);
    }

    private static String generateBody(String... materialIds) {
        String joined = java.util.Arrays.stream(materialIds)
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"materialIds\":[" + joined + "]}";
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

    private void publishMaterial(String materialId) throws Exception {
        mockMvc.perform(patch("/api/materials/" + materialId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    private void enroll(String courseId, String bearer) throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", bearer))
                .andExpect(status().isCreated());
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

    private record ReadyCourse(String courseId, String materialId) {
    }
}
