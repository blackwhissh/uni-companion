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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private static final UUID STUDENT_3_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String STUDENT_2 = JwtTestSupport.bearer(STUDENT_2_ID, "alan@uni.test", "STUDENT");
    private static final String STUDENT_3 = JwtTestSupport.bearer(STUDENT_3_ID, "grace@uni.test", "STUDENT");

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
                        .content("{\"optedIn\":true,\"displayName\":\"Ada\"}"))
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
                        .content("{\"optedIn\":true,\"displayName\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.activeCount").value(1));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true,\"displayName\":\"Ada\"}"))
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

    @Test
    void unenrollRemovesTheStudentFromTheActiveCount() throws Exception {
        String courseId = publishedCourse("MATCH-LEAVE");
        enroll(courseId, STUDENT);
        enroll(courseId, STUDENT_2);

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true,\"displayName\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(1));
        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true,\"displayName\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(2));

        mockMvc.perform(delete("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", STUDENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/match/courses/" + courseId + "/active-count")
                        .header("Authorization", STUDENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/match/courses/" + courseId + "/active-count")
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.activeCount").value(1));

        enroll(courseId, STUDENT);
        mockMvc.perform(get("/api/match/courses/" + courseId + "/active-count")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optedIn").value(false))
                .andExpect(jsonPath("$.activeCount").value(1));
    }

    @Test
    void suggestionsListOptedInClassmatesWhoShareAnIntentAndHideEmail() throws Exception {
        String courseId = publishedCourse("MATCH-INTENT");
        String otherCourseId = publishedCourse("MATCH-OTHER");
        enroll(courseId, STUDENT);
        enroll(courseId, STUDENT_2);
        enroll(courseId, STUDENT_3);
        enroll(otherCourseId, STUDENT_2);

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true}"))
                .andExpect(status().isBadRequest());

        optIn(courseId, STUDENT_2, "Alan");
        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alan\",\"intents\":[\"STUDY_GROUP\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discoverable").value(false))
                .andExpect(jsonPath("$.suggestions", hasSize(0)))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Alan"))));
        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\",\"intents\":[\"STUDY_GROUP\"]}"))
                .andExpect(status().isForbidden());

        optIn(courseId, STUDENT, "Ada");
        optIn(courseId, STUDENT_3, "Grace");
        optIn(otherCourseId, STUDENT_2, "Alan");

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discoverable").value(true))
                .andExpect(jsonPath("$.suggestions", hasSize(0)));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT_3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Grace\",\"intents\":[\"PROJECT\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada Lovelace\",\"intents\":[\"STUDY_GROUP\",\"THESIS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intents", contains("STUDY_GROUP", "THESIS")));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alan\",\"intents\":[\"THESIS\",\"LANGUAGE\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/match/courses/" + otherCourseId + "/intents")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alan\",\"intents\":[\"STUDY_GROUP\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].displayName").value("Alan"))
                .andExpect(jsonPath("$.suggestions[0].sharedIntents", contains("THESIS")))
                .andExpect(jsonPath("$.suggestions[0].email").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))));

        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\",\"intents\":[\"COFFEE\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(0)));
    }

    @Test
    void contactEmailStaysHiddenUntilTheRecipientAccepts() throws Exception {
        String courseId = publishedCourse("MATCH-CONTACT");
        enroll(courseId, STUDENT);
        enroll(courseId, STUDENT_2);
        enroll(courseId, STUDENT_3);

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_2_ID + "\"}"))
                .andExpect(status().isForbidden());

        optIn(courseId, STUDENT, "Ada");
        optIn(courseId, STUDENT_2, "Alan");
        optIn(courseId, STUDENT_3, "Grace");
        intents(courseId, STUDENT, "Ada", "STUDY_GROUP");
        intents(courseId, STUDENT_2, "Alan", "STUDY_GROUP");
        intents(courseId, STUDENT_3, "Grace", "PROJECT");

        MvcResult requested = mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_2_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].contact").value("OUTGOING"))
                .andExpect(jsonPath("$.suggestions[0].email").doesNotExist())
                .andExpect(jsonPath("$.requests[0].email").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))))
                .andReturn();
        String requestId = JsonPath.read(requested.getResponse().getContentAsString(), "$.suggestions[0].requestId");

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_ID + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].contact").value("INCOMING"))
                .andExpect(jsonPath("$.suggestions[0].email").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))));

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts/" + requestId + "/accept")
                        .header("Authorization", STUDENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts/" + requestId + "/accept")
                        .header("Authorization", STUDENT_3))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(0)))
                .andExpect(jsonPath("$.requests", hasSize(0)))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))));

        optIn(courseId, STUDENT_2, "Alan");
        intents(courseId, STUDENT_2, "Alan", "STUDY_GROUP");
        requested = mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_2_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))))
                .andReturn();
        requestId = JsonPath.read(requested.getResponse().getContentAsString(), "$.suggestions[0].requestId");

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts/" + requestId + "/decline")
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].contact").value("NONE"))
                .andExpect(jsonPath("$.suggestions[0].email").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("@"))));

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_2_ID + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts/" + requestId + "/accept")
                        .header("Authorization", STUDENT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].email").value("ada@uni.test"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("alan@uni.test"))));

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].email").value("alan@uni.test"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("ada@uni.test"))));

        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT_3))
                .andExpect(status().isOk())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("ada@uni.test"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("alan@uni.test"))));

        mockMvc.perform(post("/api/match/courses/" + courseId + "/contacts")
                        .header("Authorization", STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + STUDENT_3_ID + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", STUDENT_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/match/courses/" + courseId + "/suggestions")
                        .header("Authorization", STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.requests[0].email").value("alan@uni.test"));
    }

    private void intents(String courseId, String bearer, String displayName, String... selected) throws Exception {
        String joined = "\"" + String.join("\",\"", selected) + "\"";
        mockMvc.perform(put("/api/match/courses/" + courseId + "/intents")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\",\"intents\":[" + joined + "]}"))
                .andExpect(status().isOk());
    }

    private void optIn(String courseId, String bearer, String displayName) throws Exception {
        mockMvc.perform(put("/api/match/courses/" + courseId + "/opt-in")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true,\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isOk());
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
