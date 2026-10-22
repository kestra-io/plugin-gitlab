package io.kestra.plugin.gitlab.pipelines;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.StatefulTriggerService;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.gitlab.WireMockTest;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriggerTest extends WireMockTest {
    private static final String PIPELINES = "/api/v4/projects/12345/pipelines";

    @Inject
    private RunContextFactory runContextFactory;

    private Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .id(IdUtils.create())
            .type(Trigger.class.getName())
            .url(Property.ofValue(wireMock.baseUrl()))
            .token(Property.ofValue("test-token"))
            .projectId(Property.ofValue("12345"))
            .ref(Property.ofValue("main"))
            .stateKey(Property.ofValue("gitlab-pipelines-" + IdUtils.create()))
            .interval(Duration.ofSeconds(60));
    }

    @Test
    void shouldSeedOnTheFirstPollThenFireOnceForNewlyFinishedPipelines() throws Exception {
        wireMock.stubFor(
            get(urlPathEqualTo(PIPELINES))
                .withQueryParam("updated_after", absent())
                .willReturn(okJson("""
                    [
                      {"id":3,"status":"running","ref":"main","updated_at":"2026-10-19T10:03:00.000Z"},
                      {"id":2,"status":"failed","ref":"main","updated_at":"2026-10-19T10:02:00.000Z"},
                      {"id":1,"status":"success","ref":"main","updated_at":"2026-10-19T10:01:00.000Z"}
                    ]
                    """))
        );
        wireMock.stubFor(
            get(urlPathEqualTo(PIPELINES))
                .withQueryParam("updated_after", matching(".+"))
                .willReturn(okJson("""
                    [
                      {"id":5,"status":"running","ref":"main","updated_at":"2026-10-19T10:07:00.000Z"},
                      {"id":4,"status":"success","ref":"main","updated_at":"2026-10-19T10:06:00.000Z"},
                      {"id":3,"status":"failed","ref":"main","updated_at":"2026-10-19T10:05:00.000Z"},
                      {"id":2,"status":"failed","ref":"main","updated_at":"2026-10-19T10:02:00.000Z"}
                    ]
                    """))
        );
        var trigger = trigger().build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        var first = trigger.evaluate(context.getKey(), context.getValue());
        var second = trigger.evaluate(context.getKey(), context.getValue());
        var third = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(first.isEmpty(), is(true));
        assertThat(second.isPresent(), is(true));
        var variables = second.get().getTrigger().getVariables();
        assertThat(variables.get("total"), is(2));
        assertThat(((Number) ((Map<?, ?>) variables.get("pipeline")).get("id")).intValue(), is(4));
        assertThat(third.isEmpty(), is(true));

        wireMock.verify(
            1, getRequestedFor(urlPathEqualTo(PIPELINES))
                .withQueryParam("updated_after", absent())
                .withQueryParam("order_by", equalTo("updated_at"))
                .withQueryParam("ref", equalTo("main"))
                .withQueryParam("per_page", equalTo("20"))
        );
        // Newest update recorded at the first poll (the running pipeline 3 at 10:03) minus the one-minute overlap.
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("updated_after", equalTo("2026-10-19T10:02:00Z")));
        // Newest finished pipeline fired at the second poll (pipeline 4 at 10:06) minus the overlap.
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PIPELINES)).withQueryParam("updated_after", equalTo("2026-10-19T10:05:00Z")));
    }

    @Test
    void shouldFireOnTheSecondPollWhenTheProjectHadNoPipelines() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo(PIPELINES)).withQueryParam("updated_after", absent()).willReturn(okJson("[]")));
        wireMock.stubFor(
            get(urlPathEqualTo(PIPELINES))
                .withQueryParam("updated_after", matching(".+"))
                .willReturn(okJson("""
                    [{"id":9,"status":"canceled","ref":"main","updated_at":"2999-01-01T00:00:00.000Z"}]
                    """))
        );
        var trigger = trigger().statuses(Property.ofValue(java.util.List.of(PipelineStatus.CANCELED))).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        var first = trigger.evaluate(context.getKey(), context.getValue());
        var second = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(first.isEmpty(), is(true));
        assertThat(second.isPresent(), is(true));
        assertThat(second.get().getTrigger().getVariables().get("total"), is(1));
    }

    @Test
    void shouldRejectMaxPipelinesOutOfBounds() throws Exception {
        for (var maxPipelines : java.util.List.of(0, Trigger.MAX_PIPELINES + 1)) {
            var trigger = trigger().maxPipelines(Property.ofValue(maxPipelines)).build();
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);

            var e = assertThrows(ConstraintViolationException.class, () -> trigger.evaluate(context.getKey(), context.getValue()));

            assertThat(e.getMessage(), containsString("maxPipelines"));
        }
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)));
    }

    @Test
    void shouldRejectAStateTtlShorterThanTwoPollingIntervals() throws Exception {
        var trigger = trigger().interval(Duration.ofSeconds(60)).stateTtl(Property.ofValue(Duration.ofMinutes(1))).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        var e = assertThrows(IllegalArgumentException.class, () -> trigger.evaluate(context.getKey(), context.getValue()));

        assertThat(e.getMessage(), containsString("`stateTtl` must be at least twice the polling `interval` (PT1M), so at least PT2M, got PT1M."));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(PIPELINES)));
    }

    @Test
    void shouldKeepAPipelineThatIsStillListedFromExpiringAndFiringAgain() throws Exception {
        wireMock.stubFor(
            get(urlPathEqualTo(PIPELINES)).willReturn(okJson("""
                [{"id":2,"status":"failed","ref":"main","updated_at":"2026-10-19T10:02:00.000Z"}]
                """))
        );
        var ttl = Duration.ofMinutes(2);
        var stateKey = "gitlab-pipelines-" + IdUtils.create();
        var trigger = trigger().stateKey(Property.ofValue(stateKey)).stateTtl(Property.ofValue(ttl)).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var runContext = context.getKey().getRunContext();

        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isEmpty(), is(true));

        // Age pipeline 2 to just under the TTL, as if it had been seen two polls ago.
        var state = StatefulTriggerService.readState(runContext, stateKey, Optional.of(ttl));
        var seen = state.get("2");
        var aged = Instant.now().minus(ttl).plusSeconds(5);
        state.put("2", new StatefulTriggerService.Entry(seen.uri(), seen.version(), seen.modifiedAt(), aged));
        StatefulTriggerService.writeState(runContext, stateKey, state, Optional.of(ttl));

        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isEmpty(), is(true));

        var refreshed = StatefulTriggerService.readState(runContext, stateKey, Optional.of(ttl)).get("2");
        assertThat(refreshed.lastSeenAt().isAfter(aged), is(true));
    }
}
