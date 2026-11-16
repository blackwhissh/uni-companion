package com.unicompanion.learning;

import com.jayway.jsonpath.JsonPath;
import com.unicompanion.learning.lms.FakeLmsClient;
import com.unicompanion.learning.lms.LmsSyncResult;
import com.unicompanion.learning.lms.LmsSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class LmsApiTests {

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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LmsSyncService lmsSync;

    @Test
    void courseAdminCanLinkFakeLmsAndSyncImportsPdfs() throws Exception {
        String courseId = createCourse("LMS-SYNC");

        mockMvc.perform(get("/api/courses/" + courseId + "/lms").header("Authorization", ADMIN))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/courses/" + courseId + "/lms")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"FAKE","externalCourseId":"%s","enabled":true}
                                """.formatted(FakeLmsClient.DEMO_COURSE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("FAKE"))
                .andExpect(jsonPath("$.externalCourseId").value(FakeLmsClient.DEMO_COURSE_ID))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/courses/" + courseId + "/lms/sync").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.materialIds.length()").value(2));

        mockMvc.perform(get("/api/courses/" + courseId + "/materials").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sourceProvider").value("FAKE"))
                .andExpect(jsonPath("$[0].processingStatus").value("READY"));

        Integer auditRows = jdbc.queryForObject(
                "select count(*) from audit_events where action = 'LMS_SYNC' and resource_id = ?",
                Integer.class,
                courseId
        );
        assertThat(auditRows).isEqualTo(1);

        // Second sync with unchanged checksums should skip both files.
        mockMvc.perform(post("/api/courses/" + courseId + "/lms/sync").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void scheduledSyncImportsForEnabledLinks() throws Exception {
        String courseId = createCourse("LMS-SCHED");

        mockMvc.perform(put("/api/courses/" + courseId + "/lms")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"FAKE","externalCourseId":"%s","enabled":true}
                                """.formatted(FakeLmsClient.DEMO_COURSE_ID)))
                .andExpect(status().isOk());

        assertThat(lmsSync.listEnabledLinks()).extracting(link -> link.getCourseId().toString()).contains(courseId);

        LmsSyncResult result = lmsSync.syncScheduled(UUID.fromString(courseId));
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);

        Integer auditRows = jdbc.queryForObject(
                "select count(*) from audit_events where action = 'LMS_SYNC' and resource_id = ? and correlation_id = 'scheduled-lms-sync'",
                Integer.class,
                courseId
        );
        assertThat(auditRows).isEqualTo(1);
    }

    @Test
    void studentCannotLinkOrSyncLms() throws Exception {
        String courseId = createCourse("LMS-DENY");

        mockMvc.perform(put("/api/courses/" + courseId + "/lms")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"FAKE","externalCourseId":"%s"}
                                """.formatted(FakeLmsClient.DEMO_COURSE_ID)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses/" + courseId + "/lms/sync").header("Authorization", STUDENT))
                .andExpect(status().isForbidden());
    }

    private String createCourse(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Distributed Systems","code":"%s","term":"2026WS"}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
