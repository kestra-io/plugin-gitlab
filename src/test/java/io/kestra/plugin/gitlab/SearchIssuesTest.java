
package io.kestra.plugin.gitlab.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.core.core.issues.Search;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchIssuesTest extends WireMockTest {
    @Inject
    private RunContextFactory runContextFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testSearchIssues() throws Exception {
        // Mock the GitLab API endpoint for searching issues
        wireMock.stubFor(get(urlEqualTo("/api/v4/projects/12345/issues?search=Test+issue&state=opened"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":1,\"iid\":1,\"project_id\":12345,\"title\":\"Test issue\",\"web_url\":\"https://gitlab.example.com/test-group/test-project/issues/1\"}]")
            ));

        Search task = Search.builder()
            .id("search-issues")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .search(Property.ofValue("Test issue"))
            .build();

        RunContext runContext = runContextFactory.of();

        Search.Output runOutput = task.run(runContext);

        assertThat(runOutput.getCount(), is(1));
        assertThat(runOutput.getIssues(), is(notNullValue()));
    }

    @Test
    void testSearchIssuesWithState() throws Exception {
        // Mock the GitLab API endpoint for searching issues
        wireMock.stubFor(get(urlEqualTo("/api/v4/projects/12345/issues?search=Test+issue&state=closed"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":1,\"iid\":1,\"project_id\":12345,\"title\":\"Test issue\",\"web_url\":\"https://gitlab.example.com/test-group/test-project/issues/1\"}]")
            ));

        Search task = Search.builder()
            .id("search-issues")
            .projectId(Property.ofValue("12345"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .search(Property.ofValue("Test issue"))
            .state(Property.ofValue("closed"))
            .build();

        RunContext runContext = runContextFactory.of();

        Search.Output runOutput = task.run(runContext);

        assertThat(runOutput.getCount(), is(1));
        assertThat(runOutput.getIssues(), is(notNullValue()));
    }

    @Test
    void testSearchIssuesNotFound() {
        // Mock the GitLab API endpoint for a non-existent project
        wireMock.stubFor(get(urlEqualTo("/api/v4/projects/54321/issues?search=Test+issue&state=opened"))
            .willReturn(notFound()));

        Search task = Search.builder()
            .id("search-issues")
            .projectId(Property.ofValue("54321"))
            .token(Property.ofValue("test-token"))
            .url(Property.ofValue(wireMock.baseUrl()))
            .search(Property.ofValue("Test issue"))
            .build();

        RunContext runContext = runContextFactory.of();

        assertThrows(Exception.class, () -> task.run(runContext));
    }


}
