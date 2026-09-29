package com.unicompanion.learning;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MatchApiTests {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STUDENT_2_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String ADMIN = JwtTestSupport.bearer(ADMIN_ID, "admin@uni-companion.test", "COURSE_ADMIN");
    private static final String STUDENT = JwtTestSupport.bearer(STUDENT_ID, "ada@uni.test", "STUDENT");
    private static final String STUDENT_2 = JwtTestSupport.bearer(STUDENT_2_ID, "alan@uni.test", "STUDENT");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unenrolledStudentCannotOptIn() throws Exception {
        String courseId = publishedCourse("MATCH-DENY");

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrolledStudentCanOptInAndSeeHonestActiveCount() throws Exception {
        String courseId = publishedCourse("MATCH-OK");
        enroll(courseId, STUDENT);
        enroll(courseId, STUDENT_2);

        mockMvc.perform(get("/api/match/courses/" + courseId + "/active-count")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(false))
                .andExpect(jsonPath("$.activeCount").value(0));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.activeCount").value(1));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.activeCount").value(2));

        mockMvc.perform(get("/api/match/courses/" + courseId + "/active-count")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.activeCount").value(2));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(false))
                .andExpect(jsonPath("$.activeCount").value(1));
    }

    private String publishedCourse(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Course %s","code":"%s","term":"2026WS"}
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn();
        String courseId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(patch("/api/courses/" + courseId + "/visibility")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
        return courseId;
    }

    private void enroll(String courseId, String bearer) throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", bearer))
                .andExpect(status().isCreated());
    }
}
