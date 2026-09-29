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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    private static String course(String title, String code, String term) {
        return """
                {"title":"%s","code":"%s","term":"%s"}
                """.formatted(title, code, term);
    }
}
