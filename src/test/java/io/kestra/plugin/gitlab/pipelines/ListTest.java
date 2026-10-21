package io.kestra.plugin.gitlab.pipelines;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            .id(IdUtils.create())
            .type(List.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("12345"));
    }

    @Test
    void shouldPageUntilTheLimitWithTheFilters() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("1")).willReturn(okJson(page(300, 100))));
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("2")).willReturn(okJson(page(200, 100))));

        var output = task()
            .status(Property.ofValue(PipelineStatus.WAITING_FOR_RESOURCE))
            .source(Property.ofValue(PipelineSource.MERGE_REQUEST_EVENT))
            .orderBy(Property.ofValue(PipelineOrderBy.UPDATED_AT))
            .ref(Property.ofValue("release/1.0"))
            .updatedAfter(Property.ofValue("2026-10-18T10:00:00Z"))
            .limit(Property.ofValue(150))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getSize(), is(150));
        assertThat(output.getPipelines().getFirst().getId(), is(300L));
        assertThat(output.getPipelines().getLast().getId(), is(151L));
        assertThat(output.getUri(), is(nullValue()));
        assertThat(output.getRow(), is(nullValue()));
        wireMock.verify(
            2, getRequestedFor(urlPathEqualTo(PIPELINES))
                .withQueryParam("status", equalTo("waiting_for_resource"))
                .withQueryParam("source", equalTo("merge_request_event"))
                .withQueryParam("ref", equalTo("release/1.0"))
                .withQueryParam("updated_after", equalTo("2026-10-18T10:00:00Z"))
                .withQueryParam("order_by", equalTo("updated_at"))
                .withQueryParam("sort", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100"))
        );
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("3")));
    }

    @Test
    void shouldStoreEveryPageInTheIonFile() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("1")).willReturn(okJson(page(300, 100))));
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("2")).willReturn(okJson(page(200, 3))));
        var runContext = runContextFactory.of();

        var output = task().limit(Property.ofValue(500)).fetchType(Property.ofValue(FetchType.STORE)).build().run(runContext);

        assertThat(output.getSize(), is(103));
        assertThat(output.getPipelines(), is(nullValue()));
        try (var input = runContext.storage().getFile(output.getUri())) {
            var rows = FileSerde.readAll(input, Map.class).collectList().block();
            assertThat(rows, hasSize(103));
            assertThat(((Number) rows.getFirst().get("id")).longValue(), is(300L));
            assertThat(rows.getFirst().get("status"), is("failed"));
            assertThat(((Number) rows.getLast().get("id")).longValue(), is(198L));
        }
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("3")));
    }

    @Test
    void shouldReturnOnlyTheFirstPipelineWithFetchOne() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).willReturn(okJson(page(77, 1))));

        var output = task().limit(Property.ofValue(500)).fetchType(Property.ofValue(FetchType.FETCH_ONE)).build().run(runContextFactory.of());

        assertThat(output.getSize(), is(1));
        assertThat(output.getRow().getId(), is(77L));
        assertThat(output.getPipelines(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("per_page", equalTo("1")).withQueryParam("page", equalTo("1")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PIPELINES)));
    }

    @Test
    void shouldReturnAnEmptyRowWhenNothingMatchesWithFetchOne() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).willReturn(okJson("[]")));

        var output = task().fetchType(Property.ofValue(FetchType.FETCH_ONE)).build().run(runContextFactory.of());

        assertThat(output.getSize(), is(0));
        assertThat(output.getRow(), is(nullValue()));
    }

    @Test
    void shouldReturnOnlyTheSizeWithNone() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("page", equalTo("1")).willReturn(okJson(page(5, 5))));

        var output = task().fetchType(Property.ofValue(FetchType.NONE)).build().run(runContextFactory.of());

        assertThat(output.getSize(), is(5));
        assertThat(output.getPipelines(), is(nullValue()));
        assertThat(output.getRow(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
    }

    @Test
    void shouldRejectALimitOutOfBounds() {
        for (var limit : java.util.List.of(0, -5, List.MAX_LIMIT + 1)) {
            var e = assertThrows(IllegalArgumentException.class, () -> task().limit(Property.ofValue(limit)).build().run(runContextFactory.of()));
            assertThat(e.getMessage(), containsString("`limit` must be between 1 and 10000"));
        }
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)));
    }

    @Test
    void shouldFailWithAnActionableMessageOnAnEmptyBody() {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).willReturn(aResponse().withStatus(200)));

        var e = assertThrows(IllegalStateException.class, () -> task().build().run(runContextFactory.of()));

        assertThat(e.getMessage(), containsString("Empty response from the GitLab API for GET /api/v4/projects/12345/pipelines"));
        assertThat(e.getMessage(), containsString("`url` and `apiPath`"));
    }
}
