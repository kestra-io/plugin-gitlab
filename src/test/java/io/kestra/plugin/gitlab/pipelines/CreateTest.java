package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateTest extends WireMockTest {
    private static final String PIPELINES = "/api/v4/projects/12345/pipeline";
    private static final String PIPELINE_42 = "/api/v4/projects/12345/pipelines/42";

    @Inject
    private RunContextFactory runContextFactory;

    static String pipeline(String status) {
        return """
            {"id":42,"iid":7,"project_id":12345,"status":"%s","ref":"main","sha":"a1b2c3","source":"api",
             "web_url":"https://gitlab.example.com/group/project/-/pipelines/42",
             "created_at":"2026-10-19T10:00:00.000Z","updated_at":"2026-10-19T10:05:00.000Z",
             "started_at":"2026-10-19T10:00:05.000Z","finished_at":null,"duration":null}
            """.formatted(status);
    }

    private Create.CreateBuilder<?, ?> task() {
        return Create.builder()
            .id("create")
            .type(Create.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("12345"))
            .ref(Property.ofValue("main"))
            .pollInterval(Property.ofValue(Duration.ofMillis(20)));
    }

    @Test
    void shouldCreatePipelineWithVariablesWithoutWaiting() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PIPELINES)).willReturn(okJson(pipeline("created"))));

        Pipeline output = task()
            .variables(Property.ofValue(Map.of("DEPLOY_ENV", "staging")))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getId(), is(42L));
        assertThat(output.getIid(), is(7L));
        assertThat(output.getStatus(), is("created"));
        assertThat(output.getWebUrl(), is("https://gitlab.example.com/group/project/-/pipelines/42"));
        wireMock.verify(
            postRequestedFor(urlEqualTo(PIPELINES))
                .withHeader("PRIVATE-TOKEN", equalTo("test-token"))
                .withRequestBody(equalToJson("""
                    {"ref":"main","variables":[{"key":"DEPLOY_ENV","value":"staging","variable_type":"env_var"}]}
                    """))
        );
        wireMock.verify(0, getRequestedFor(urlEqualTo(PIPELINE_42)));
    }

    @Test
    void shouldWaitUntilThePipelineSucceeds() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PIPELINES)).willReturn(okJson(pipeline("pending"))));
        wireMock.stubFor(
            get(urlEqualTo(PIPELINE_42)).inScenario("progress").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(pipeline("running"))).willSetStateTo("done")
        );
        wireMock.stubFor(
            get(urlEqualTo(PIPELINE_42)).inScenario("progress").whenScenarioStateIs("done")
                .willReturn(okJson(pipeline("success")))
        );

        Pipeline output = task().wait(Property.ofValue(true)).build().run(runContextFactory.of());

        assertThat(output.getStatus(), is("success"));
        wireMock.verify(2, getRequestedFor(urlEqualTo(PIPELINE_42)));
    }

    @Test
    void shouldFailWhenThePipelineFails() {
        wireMock.stubFor(post(urlEqualTo(PIPELINES)).willReturn(okJson(pipeline("running"))));
        wireMock.stubFor(get(urlEqualTo(PIPELINE_42)).willReturn(okJson(pipeline("failed"))));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> task().wait(Property.ofValue(true)).build().run(runContextFactory.of()));

        assertThat(e.getMessage(), containsString("finished with status 'failed'"));
    }

    @Test
    void shouldCancelThePipelineWhenTheWaitTimesOut() {
        wireMock.stubFor(post(urlEqualTo(PIPELINES)).willReturn(okJson(pipeline("running"))));
        wireMock.stubFor(get(urlEqualTo(PIPELINE_42)).willReturn(okJson(pipeline("running"))));
        wireMock.stubFor(post(urlEqualTo(PIPELINE_42 + "/cancel")).willReturn(okJson(pipeline("canceling"))));

        assertThrows(
            TimeoutException.class, () -> task()
                .wait(Property.ofValue(true))
                .maxWaitDuration(Property.ofValue(Duration.ofMillis(100)))
                .build()
                .run(runContextFactory.of())
        );

        wireMock.verify(1, postRequestedFor(urlEqualTo(PIPELINE_42 + "/cancel")));
    }

    @Test
    void shouldCancelThePipelineWhenTheExecutionIsKilled() throws Exception {
        wireMock.stubFor(post(urlEqualTo(PIPELINES)).willReturn(okJson(pipeline("running"))));
        wireMock.stubFor(get(urlEqualTo(PIPELINE_42)).willReturn(okJson(pipeline("running"))));
        wireMock.stubFor(post(urlEqualTo(PIPELINE_42 + "/cancel")).willReturn(okJson(pipeline("canceling"))));
        Create task = task().wait(Property.ofValue(true)).build();

        CompletableFuture<Pipeline> running = CompletableFuture.supplyAsync(() ->
        {
            try {
                return task.run(runContextFactory.of());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        while (wireMock.findAll(getRequestedFor(urlEqualTo(PIPELINE_42))).isEmpty()) {
            Thread.sleep(10);
        }
        task.kill();

        Exception e = assertThrows(Exception.class, () -> running.get(5, TimeUnit.SECONDS));
        assertThat(e.getMessage(), containsString("killed"));
        wireMock.verify(1, postRequestedFor(urlEqualTo(PIPELINE_42 + "/cancel")));
    }
}
