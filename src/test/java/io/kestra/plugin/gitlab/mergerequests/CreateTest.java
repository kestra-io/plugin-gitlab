package io.kestra.plugin.gitlab.mergerequests;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCreateMergeRequest() throws Exception {
        // Mock the GitLab API endpoint for creating a merge request
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/12345/merge_requests"))
            .withRequestBody(equalToJson("{\"title\":\"Test merge request\",\"description\":\"This is a test merge request\",\"source_branch\":\"feature/test-branch\",\"target_branch\":\"main\"}"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1,\"iid\":1,\"project_id\":12345,\"title\":\"Test merge request\",\"web_url\":\"https://gitlab.example.com/test-group/test-project/merge_requests/1\"}")));

        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test merge request"))
            .mergeRequestDescription(Property.ofValue("This is a test merge request"))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        Create.Output runOutput = task.run(runContext);

        assertThat(runOutput.getMergeReqID(), is(notNullValue()));
        assertThat(runOutput.getWebUrl(), is(notNullValue()));
    }

    @Test
    void testCreateMergeRequestNotFound() {
        // Mock the GitLab API endpoint for a non-existent project
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/54321/merge_requests"))
            .willReturn(notFound()));

        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("54321"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test merge request"))
            .mergeRequestDescription(Property.ofValue("This is a test merge request"))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateMergeRequest_missingProjectId() throws Exception {
        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test merge request"))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateMergeRequest_missingToken() throws Exception {
        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("12345"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test merge request"))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateMergeRequestWithEmptyRequiredFields() throws Exception {
        // Mock endpoint that should not be called
        wireMock.stubFor(post(urlMatching("/api/v4/projects/.*/merge_requests"))
            .willReturn(aResponse().withStatus(400)));

        RunContext runContext = runContextFactory.of();

        // Test with null title - should create MR but GitLab API will likely fail
        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        // This should complete without throwing since title is optional in our implementation
        // The actual validation happens at GitLab API level
        assertThrows(Exception.class, () -> task.run(runContext));
    }

    @Test
    void testCreateMergeRequestWithMinimalData() throws Exception {
        // Mock the GitLab API endpoint for creating a merge request with minimal data
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/12345/merge_requests"))
            .withRequestBody(equalToJson("{\"title\":\"Minimal MR\",\"source_branch\":\"feature\",\"target_branch\":\"main\"}"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":2,\"web_url\":\"https://gitlab.example.com/test/merge_requests/2\"}")));

        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Minimal MR"))
            .sourceBranch(Property.ofValue("feature"))
            .targetBranch(Property.ofValue("main"))
            .build();

        Create.Output runOutput = task.run(runContext);

        assertThat(runOutput.getMergeReqID(), is("2"));
        assertThat(runOutput.getWebUrl(), is("https://gitlab.example.com/test/merge_requests/2"));
        assertThat(runOutput.getStatusCode(), is(201));
    }

    @Test
    void testCreateMergeRequestBadRequest() {
        // Mock the GitLab API endpoint returning bad request
        wireMock.stubFor(post(urlEqualTo("/api/v4/projects/12345/merge_requests"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Bad request\"}")));

        RunContext runContext = runContextFactory.of();

        Create task = Create.builder()
            .id("create-merge-request")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .title(Property.ofValue("Test merge request"))
            .sourceBranch(Property.ofValue("feature/test-branch"))
            .targetBranch(Property.ofValue("main"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
