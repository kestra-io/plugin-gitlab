package io.kestra.plugin.gitlab.issues;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.WireMockTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CreateTest extends WireMockTest {
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

        Create task = Create.builder()
            .id("create-issue")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test issue"))
            .issueDescription(Property.ofValue("This is a test issue"))
            .build();

        Create.Output runOutput = task.run(runContext);

        assertThat(runOutput.getIssueId(), is(notNullValue()));
        assertThat(runOutput.getWebUrl(), is(notNullValue()));
    }

    @Test
    void testCreateIssueNotFound() {
        // Mock the GitLab API endpoint for a non-existent project
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/54321/issues"))
            .willReturn(notFound()));

        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-issue")
            .projectId(Property.ofValue("54321"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test issue"))
            .issueDescription(Property.ofValue("This is a test issue"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateIssue_missingProjectId() throws Exception {
        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-issue")
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test issue"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateIssue_missingToken() throws Exception {
        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-issue")
            .projectId(Property.ofValue("12345"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test issue"))
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

        Create task = Create.builder()
            .id("create-issue")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Minimal Issue"))
            .build();

        Create.Output runOutput = task.run(runContext);
        assertThat(runOutput.getIssueId(), is("2"));
        assertThat(runOutput.getWebUrl(), is("https://gitlab.example.com/test/issues/2"));
    }
}

