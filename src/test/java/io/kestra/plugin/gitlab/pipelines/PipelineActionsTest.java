package io.kestra.plugin.gitlab.pipelines;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PipelineActionsTest extends WireMockTest {
    private static final String PIPELINE_42 = "/api/v4/projects/group%2Fproject/pipelines/42";

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldReadEveryPipelineField() throws Exception {
        wireMock.stubFor(get(urlEqualTo(PIPELINE_42)).willReturn(okJson("""
            {"id":42,"iid":7,"project_id":12345,"status":"success","ref":"main","sha":"a1b2c3","source":"push",
             "web_url":"https://gitlab.example.com/group/project/-/pipelines/42",
             "created_at":"2026-10-19T10:00:00.000Z","updated_at":"2026-10-19T10:05:00.000Z",
             "started_at":"2026-10-19T10:00:05.000Z","finished_at":"2026-10-19T10:04:59.000Z","duration":294}
            """)));

        Pipeline pipeline = Get.builder()
            .id("get")
            .type(Get.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("group%2Fproject"))
            .pipelineId(Property.ofValue(42L))
            .build()
            .run(runContextFactory.of());

        assertThat(pipeline.getId(), is(42L));
        assertThat(pipeline.getProjectId(), is(12345L));
        assertThat(pipeline.getSource(), is("push"));
        assertThat(pipeline.getSha(), is("a1b2c3"));
        assertThat(pipeline.getFinishedAt(), is(Instant.parse("2026-10-19T10:04:59Z")));
        assertThat(pipeline.getDuration(), is(294));
    }

    @Test
    void shouldCancelAndRetryThroughTheirEndpoints() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PIPELINE_42 + "/cancel")).willReturn(okJson("{\"id\":42,\"status\":\"canceled\"}")));
        wireMock.stubFor(post(urlEqualTo(PIPELINE_42 + "/retry")).willReturn(okJson("{\"id\":42,\"status\":\"pending\"}")));

        Pipeline canceled = Cancel.builder()
            .id("cancel")
            .type(Cancel.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("group%2Fproject"))
            .pipelineId(Property.ofExpression("{{ pipeline }}"))
            .build()
            .run(runContextFactory.of(java.util.Map.of("pipeline", 42)));
        Pipeline retried = Retry.builder()
            .id("retry")
            .type(Retry.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("group%2Fproject"))
            .pipelineId(Property.ofValue(42L))
            .build()
            .run(runContextFactory.of());

        assertThat(canceled.getStatus(), is("canceled"));
        assertThat(retried.getStatus(), is("pending"));
    }
}
