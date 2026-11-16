package com.unicompanion.learning.lms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MoodleLmsClientTest {

    private MockRestServiceServer server;
    private MoodleLmsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MoodleLmsClient(
                new MoodleProperties(true, "https://moodle.example.test", "secret-token"),
                builder,
                JsonMapper.builder().build()
        );
    }

    @Test
    void listsOnlyPdfResourcesFromCourseContents() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("core_course_get_contents")))
                .andRespond(withSuccess("""
                        [
                          {
                            "modules": [
                              {
                                "modname": "resource",
                                "name": "Consensus",
                                "contents": [
                                  {
                                    "type": "file",
                                    "filename": "01-consensus.pdf",
                                    "filepath": "/",
                                    "fileurl": "https://moodle.example.test/webservice/pluginfile.php/1/mod_resource/content/0/01-consensus.pdf",
                                    "timemodified": "1700000001"
                                  },
                                  {
                                    "type": "file",
                                    "filename": "notes.txt",
                                    "filepath": "/",
                                    "fileurl": "https://moodle.example.test/webservice/pluginfile.php/1/mod_resource/content/0/notes.txt",
                                    "timemodified": "1700000002"
                                  }
                                ]
                              },
                              {
                                "modname": "forum",
                                "name": "Announcements",
                                "contents": []
                              }
                            ]
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<LmsRemoteFile> files = client.listFiles("42");

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().title()).isEqualTo("Consensus");
        assertThat(files.getFirst().fileName()).isEqualTo("01-consensus.pdf");
        assertThat(files.getFirst().checksum()).isEqualTo("1700000001");
        assertThat(files.getFirst().externalFileId()).isEqualTo("/01-consensus.pdf|1700000001");
        server.verify();
    }

    @Test
    void downloadsPdfWithTokenAppended() {
        String contentsJson = """
                [
                  {
                    "modules": [
                      {
                        "modname": "resource",
                        "name": "Consensus",
                        "contents": [
                          {
                            "type": "file",
                            "filename": "01-consensus.pdf",
                            "filepath": "/",
                            "fileurl": "https://moodle.example.test/webservice/pluginfile.php/1/mod_resource/content/0/01-consensus.pdf",
                            "timemodified": "1700000001"
                          }
                        ]
                      }
                    ]
                  }
                ]
                """;
        server.expect(requestTo(org.hamcrest.Matchers.containsString("core_course_get_contents")))
                .andRespond(withSuccess(contentsJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("pluginfile.php"),
                        org.hamcrest.Matchers.containsString("token=secret-token")
                )))
                .andRespond(withSuccess("%PDF-moodle".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF));

        byte[] bytes = client.download("42", "/01-consensus.pdf|1700000001");

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("%PDF-moodle");
        server.verify();
    }

    @Test
    void rejectsNonNumericCourseId() {
        assertThatThrownBy(() -> client.listFiles("demo-moodle-course"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }
}
