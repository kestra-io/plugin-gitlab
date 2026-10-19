package io.kestra.plugin.gitlab.pipelines;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ListTest extends WireMockTest {
    private static final String PIPELINES = "/api/v4/projects/12345/pipelines";

    @Inject
    private RunContextFactory runContextFactory;

    private static String page(int fromId, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> "{\"id\":%d,\"status\":\"failed\",\"ref\":\"main\",\"updated_at\":\"2026-10-19T10:00:00.000Z\"}".formatted(fromId - i))
            .collect(Collectors.joining(",", "[", "]"));
    }

    private List.ListBuilder<?, ?> task() {
        return List.builder()
            .id("list")
            .type(List.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("12345"));
    }

    @Test
    void shouldPageUntilTheLimitWithTheFilters() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("1")).willReturn(okJson(page(300, 100))));
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("2")).willReturn(okJson(page(200, 100))));

        List.Output output = task()
            .status(Property.ofValue("failed"))
            .ref(Property.ofValue("release/1.0"))
            .updatedAfter(Property.ofValue("2026-10-18T10:00:00Z"))
            .limit(Property.ofValue(150))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize(), is(150));
        assertThat(output.getPipelines().getFirst().getId(), is(300L));
        assertThat(output.getPipelines().getLast().getId(), is(151L));
        assertThat(output.getUri(), is(nullValue()));
        wireMock.verify(
            getRequestedFor(urlPathEqualTo(PIPELINES))
                .withQueryParam("status", equalTo("failed"))
                .withQueryParam("ref", equalTo("release/1.0"))
                .withQueryParam("updated_after", equalTo("2026-10-18T10:00:00Z"))
                .withQueryParam("order_by", equalTo("id"))
                .withQueryParam("sort", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100"))
        );
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("3")));
    }

    @Test
    void shouldStopOnAShortPageAndStoreThePipelines() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("1")).willReturn(okJson(page(3, 3))));
        RunContext runContext = runContextFactory.of();

        List.Output output = task().fetchType(Property.ofValue(FetchType.STORE)).build().run(runContext);

        assertThat(output.getSize(), is(3));
        assertThat(output.getPipelines(), is(nullValue()));
        try (var reader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(output.getUri()), StandardCharsets.UTF_8))) {
            assertThat(reader.lines().count(), is(3L));
        }
    }
}
