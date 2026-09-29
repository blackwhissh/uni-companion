package com.unicompanion.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthApiTests {

    private static final String ADMIN_EMAIL = "admin@uni-companion.test";
    private static final String ADMIN_PASSWORD = "admin-pass-1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void registerCreatesStudentAndJwtIncludesThatRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Ada@Uni.test","password":"password1","displayName":"Ada Lovelace","role":"COURSE_ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("ada@uni.test"))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password1").doesNotContain("passwordHash");
        Jwt jwt = jwtDecoder.decode(token(body));
        assertThat(jwt.getSubject()).isEqualTo(JsonPath.read(body, "$.user.id"));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("STUDENT");
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyUsed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"duplicate@uni.test","password":"password1","displayName":"Ada"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"DUPLICATE@uni.test","password":"password1","displayName":"Ada Again"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerRejectsAWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak@uni.test","password":"short","displayName":"Weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void seededDemoUsersLogInAsAdminAndStudent() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("admin"))
                .andExpect(jsonPath("$.user.roles").value(org.hamcrest.Matchers.containsInAnyOrder("ADMIN", "COURSE_ADMIN")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student","password":"student"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("student"))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"));
    }

    @Test
    void loginReturnsAJwtThatIncludesTheRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@uni.test","password":"password1","displayName":"Login User"}
                                """));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@uni.test","password":"password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andReturn();

        assertThat(jwtDecoder.decode(token(result.getResponse().getContentAsString())).getClaimAsStringList("roles"))
                .containsExactly("STUDENT");
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutRevealingWhetherTheEmailExists() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@uni.test","password":"password1","displayName":"Known"}
                                """));

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@uni.test","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@uni.test","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String wrongBody = wrongPassword.getResponse().getContentAsString();
        String unknownBody = unknownEmail.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(wrongBody, "$.message")).isEqualTo(JsonPath.read(unknownBody, "$.message"));
        assertThat(wrongBody).doesNotContain("known@uni.test").doesNotContain("not found");
        assertThat(unknownBody).doesNotContain("missing@uni.test");
    }

    @Test
    void meRequiresABearerToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateChangesDisplayNameAndInterests() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"profile@uni.test","password":"password1","displayName":"Before"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = token(registered.getResponse().getContentAsString());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Before"));

        mockMvc.perform(patch("/api/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"After","interests":"compilers"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("After"))
                .andExpect(jsonPath("$.interests").value("compilers"))
                .andExpect(jsonPath("$.email").value("profile@uni.test"));
    }

    @Test
    void seededCourseAdminCanLogIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("COURSE_ADMIN"))
                .andReturn();

        assertThat(jwtDecoder.decode(token(result.getResponse().getContentAsString())).getClaimAsStringList("roles"))
                .contains("COURSE_ADMIN");
    }

    private static String token(String body) {
        return JsonPath.read(body, "$.token");
    }
}
