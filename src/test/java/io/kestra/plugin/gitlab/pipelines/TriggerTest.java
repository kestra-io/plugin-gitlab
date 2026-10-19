package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TriggerTest extends WireMockTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldFireOnceForFinishedPipelinesAndSkipRunningOnes() throws Exception {
        wireMock.stubFor(
            get(urlPathEqualTo("/api/v4/projects/12345/pipelines"))
                .withQueryParam("order_by", equalTo("updated_at"))
                .withQueryParam("ref", equalTo("main"))
                .willReturn(okJson("""
                    [
                      {"id":3,"status":"running","ref":"main","updated_at":"2026-10-19T10:03:00.000Z"},
                      {"id":2,"status":"failed","ref":"main","updated_at":"2026-10-19T10:02:00.000Z"},
                      {"id":1,"status":"success","ref":"main","updated_at":"2026-10-19T10:01:00.000Z"}
                    ]
                    """))
        );

        Trigger trigger = Trigger.builder()
            .id("on_pipeline_finished")
            .type(Trigger.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("12345"))
            .ref(Property.ofValue("main"))
            .stateKey(Property.ofValue("gitlab-pipelines-" + IdUtils.create()))
            .interval(Duration.ofSeconds(60))
            .build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        ConditionContext conditionContext = context.getKey();

        Optional<Execution> first = trigger.evaluate(conditionContext, context.getValue());
        Optional<Execution> second = trigger.evaluate(conditionContext, context.getValue());

        assertThat(first.isPresent(), is(true));
        Map<String, Object> variables = first.get().getTrigger().getVariables();
        assertThat(variables.get("total"), is(2));
        assertThat(((Number) ((Map<?, ?>) variables.get("pipeline")).get("id")).intValue(), is(2));
        assertThat(second.isEmpty(), is(true));
    }
}
