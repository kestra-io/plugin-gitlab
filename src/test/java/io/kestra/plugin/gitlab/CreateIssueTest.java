package io.kestra.plugin.gitlab;

import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.issues.CreateIssue;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CreateIssueTest extends WireMockTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testCreateIssue() throws Exception {
        // Mock the GitLab API endpoint for creating an issue
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/12345/issues"))
            .withRequestBody(equalToJson("{\"title\":\"Test issue\",\"description\":\"This is a test issue\"}"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1,\"iid\":1,\"project_id\":12345,\"title\":\"Test issue\",\"web_url\":\"https://gitlab.example.com/test-group/test-project/issues/1\"}")
            ));

        RunContext runContext = runContextFactory.of();

        CreateIssue task = CreateIssue.builder()
            .id("create-issue")
            .projectId("12345")
            .token("test-token")
            .url(wireMock.baseUrl())
            .title("Test issue")
            .description("This is a test issue")
            .build();

        CreateIssue.Output runOutput = task.run(runContext);

        assertThat(runOutput.getIssueId(), is(notNullValue()));
        assertThat(runOutput.getWebUrl(), is(notNullValue()));
    }

    @Test
    void testCreateIssueNotFound() {
        // Mock the GitLab API endpoint for a non-existent project
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/54321/issues"))
            .willReturn(notFound()));

        RunContext runContext = runContextFactory.of();

        CreateIssue task = CreateIssue.builder()
            .id("create-issue")
            .projectId("54321")
            .token("test-token")
            .url(wireMock.baseUrl())
            .title("Test issue")
            .description("This is a test issue")
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateIssue_missingProjectId() throws Exception {
        RunContext runContext = runContextFactory.of();
        
        CreateIssue task = CreateIssue.builder()
            .id("create-issue")
            .token("test-token")
            .url(wireMock.baseUrl())
            .title("Test issue")
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateIssue_missingToken() throws Exception {
        RunContext runContext = runContextFactory.of();
        
        CreateIssue task = CreateIssue.builder()
            .id("create-issue")
            .projectId("12345")
            .url(wireMock.baseUrl())
            .title("Test issue")
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateIssueWithMinimalData() throws Exception {
        // Mock endpoint for minimal data
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/12345/issues"))
            .withRequestBody(equalToJson("{\"title\":\"Minimal Issue\"}"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":2,\"web_url\":\"https://gitlab.example.com/test/issues/2\"}")));

        RunContext runContext = runContextFactory.of();
        
        CreateIssue task = CreateIssue.builder()
            .id("create-issue")
            .projectId("12345")
            .token("test-token")
            .url(wireMock.baseUrl())
            .title("Minimal Issue")
            .build();

        CreateIssue.Output runOutput = task.run(runContext);
        assertThat(runOutput.getIssueId(), is("2"));
        assertThat(runOutput.getWebUrl(), is("https://gitlab.example.com/test/issues/2"));
    }
}

