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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RagApiTests {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ADMIN = JwtTestSupport.bearer(ADMIN_ID, "admin@uni-companion.test", "COURSE_ADMIN");
    private static final String STUDENT = JwtTestSupport.bearer(STUDENT_ID, "ada@uni.test", "STUDENT");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unenrolledStudentCannotQueryRag() throws Exception {
        String courseId = createCourse("RAG-DENY");
        publishCourse(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Consensus algorithms"));
        publishMaterial(materialId);

        mockMvc.perform(post("/api/rag/query")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query(courseId, "What is consensus?", materialId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrolledStudentGetsGroundedAnswerFromPublishedReadyMaterial() throws Exception {
        String courseId = createCourse("RAG-OK");
        publishCourse(courseId);
        String materialId = upload(courseId, "Lecture 1", "lecture.pdf", pdfBytes("Consensus algorithms keep replicas consistent."));
        publishMaterial(materialId);
        enroll(courseId);

        mockMvc.perform(post("/api/rag/query")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query(courseId, "What do consensus algorithms do?", materialId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("Consensus")))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].materialId").value(materialId))
                .andExpect(jsonPath("$.citations[0].title").value("Lecture 1"))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(1))
                .andExpect(jsonPath("$.citations[0].excerpt", containsString("Consensus")));
    }

    @Test
    void unpublishedMaterialIsExcludedFromRagGrounding() throws Exception {
        String courseId = createCourse("RAG-HIDE");
        publishCourse(courseId);
        String publishedId = upload(courseId, "Published", "pub.pdf", pdfBytes("Published topic is replication."));
        publishMaterial(publishedId);
        upload(courseId, "Draft", "draft.pdf", pdfBytes("Secret draft topic is sharding."));
        enroll(courseId);

        mockMvc.perform(post("/api/rag/query")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query(courseId, "What is the secret draft topic?", publishedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("replication")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(containsString("sharding"))))
                .andExpect(jsonPath("$.citations[0].materialId").value(publishedId));
    }

    @Test
    void retrievalUsesOnlyTheSelectedPublishedMaterials() throws Exception {
        String courseId = createCourse("RAG-FILTER");
        publishCourse(courseId);
        String consensusId = upload(courseId, "Consensus", "consensus.pdf", pdfBytes("Consensus keeps replicas consistent."));
        String shardingId = upload(courseId, "Sharding", "sharding.pdf", pdfBytes("Sharding partitions data across nodes."));
        publishMaterial(consensusId);
        publishMaterial(shardingId);
        enroll(courseId);

        mockMvc.perform(post("/api/rag/query")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(query(courseId, "What is sharding?", consensusId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].materialId").value(consensusId))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(containsString("partitions data"))));
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

    private void enroll(String courseId) throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", STUDENT))
                .andExpect(status().isCreated());
    }

    private static String query(String courseId, String question, String... materialIds) {
        String ids = java.util.Arrays.stream(materialIds)
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"courseId":"%s","question":"%s","materialIds":[%s]}
                """.formatted(courseId, question, ids);
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
