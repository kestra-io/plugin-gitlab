package io.kestra.plugin.gitlab.pipelines;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List GitLab CI/CD pipelines",
    description = """
        Lists the pipelines of a project, newest first, filtered by status, ref, commit, source or update time. \
        Returns the first pipeline (`FETCH_ONE`), every pipeline (`FETCH`), an internal storage file written page by page (`STORE`), \
        or only the number of pipelines found (`NONE`)."""
)
@Plugin(
    examples = {
        @Example(
            title = "List the failed pipelines of the last day.",
            full = true,
            code = """
                id: gitlab_failed_pipelines
                namespace: company.team

                tasks:
                  - id: list_failed
                    type: io.kestra.plugin.gitlab.pipelines.List
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    status: FAILED
                    updatedAfter: "{{ now() | dateAdd(-1, 'DAYS') }}"

                  - id: log_results
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_failed.size }} failed pipelines"
                """
        ),
        @Example(
            title = "Store the last 500 pipelines of `main` in an internal storage file.",
            full = true,
            code = """
                id: gitlab_pipeline_history
                namespace: company.team

                tasks:
                  - id: list_main
                    type: io.kestra.plugin.gitlab.pipelines.List
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    ref: main
                    limit: 500
                    fetchType: STORE
                """
        ),
        @Example(
            title = "Read the latest scheduled pipeline of `main`.",
            full = true,
            code = """
                id: gitlab_latest_scheduled_pipeline
                namespace: company.team

                tasks:
                  - id: latest_scheduled
                    type: io.kestra.plugin.gitlab.pipelines.List
                    token: "{{ secret('GITLAB_TOKEN') }}"
                    projectId: "12345"
                    ref: main
                    source: SCHEDULE
                    orderBy: UPDATED_AT
                    fetchType: FETCH_ONE

                  - id: log_latest
                    type: io.kestra.plugin.core.log.Log
                    message: "Latest scheduled pipeline: {{ outputs.latest_scheduled.row.webUrl }}"
                """
        )
    }
)
public class List extends AbstractPipelineTask implements RunnableTask<List.Output> {
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_LIMIT = 10_000;
    private static final int DEFAULT_LIMIT = 20;

    @Schema(
        title = "Status filter",
        description = """
            Only pipelines with this status, for example `RUNNING`, `SUCCESS`, `FAILED` or `CANCELED`."""
    )
    @PluginProperty(group = "main")
    private Property<PipelineStatus> status;

    @Schema(
        title = "Ref filter",
        description = """
            Only pipelines of this branch or tag."""
    )
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(
        title = "Commit SHA filter",
        description = """
            Only pipelines of this commit."""
    )
    @PluginProperty(group = "main")
    private Property<String> sha;

    @Schema(
        title = "Source filter",
        description = """
            Only pipelines started by this source, for example `PUSH`, `WEB`, `API`, `SCHEDULE` or `MERGE_REQUEST_EVENT`."""
    )
    @PluginProperty(group = "main")
    private Property<PipelineSource> source;

    @Schema(
        title = "Updated after",
        description = """
            Only pipelines updated after this ISO 8601 date-time."""
    )
    @PluginProperty(group = "main")
    private Property<String> updatedAfter;

    @Schema(
        title = "Order by",
        description = """
            Sort field, newest first: `ID` (the default), `STATUS`, `REF`, `UPDATED_AT` or `USER_ID`."""
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<PipelineOrderBy> orderBy = Property.ofValue(PipelineOrderBy.ID);

    @Schema(
        title = "Maximum number of pipelines",
        description = """
            Pages through the API, 100 pipelines per request, until this many pipelines are read. \
            Defaults to `20`; must be between `1` and `10000`. Ignored with `fetchType: FETCH_ONE`, which reads a single pipeline."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<@Min(1) @Max(MAX_LIMIT) Integer> limit = Property.ofValue(DEFAULT_LIMIT);

    @Schema(
        title = "Fetch type",
        description = """
            `FETCH_ONE` returns the first pipeline in the `row` output, `FETCH` (the default) returns every pipeline in the `pipelines` output, \
            `STORE` writes them page by page to an internal storage file returned as `uri`, and `NONE` only returns their number in `size`."""
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        var rLimit = rFetchType == FetchType.FETCH_ONE ? 1 : renderLimit(runContext);

        Output output;
        try (var client = httpClient(runContext)) {
            var pipelines = pipelines(runContext, client, rLimit);
            output = switch (rFetchType) {
                case FETCH_ONE -> {
                    var rows = await(pipelines.collectList());
                    yield Output.builder().size(rows.size()).row(rows.isEmpty() ? null : rows.getFirst()).build();
                }
                case FETCH -> {
                    var rows = await(pipelines.collectList());
                    yield Output.builder().size(rows.size()).pipelines(rows).build();
                }
                case STORE -> store(runContext, pipelines);
                case NONE -> Output.builder().size(await(pipelines.count()).intValue()).build();
            };
        }

        runContext.logger().info("Found {} GitLab pipeline(s)", output.getSize());
        return output;
    }

    /**
     * Reads the pipelines matching this task's filters into memory, without logging, for callers such as {@link Trigger}.
     */
    java.util.List<Pipeline> fetch(RunContext runContext) throws Exception {
        var rLimit = renderLimit(runContext);
        try (var client = httpClient(runContext)) {
            return await(pipelines(runContext, client, rLimit).collectList());
        }
    }

    private int renderLimit(RunContext runContext) throws IllegalVariableEvaluationException {
        return boundedInt(runContext.render(this.limit).as(Integer.class).orElse(DEFAULT_LIMIT), "limit", 1, MAX_LIMIT);
    }

    /**
     * Lazily pages through the API: each page is requested only when the previous one has been consumed.
     */
    private Flux<Pipeline> pipelines(RunContext runContext, HttpClient client, int rLimit) throws IllegalVariableEvaluationException {
        var pageSize = Math.min(rLimit, MAX_PAGE_SIZE);
        var filters = new ArrayList<String>();
        addFilter(filters, "status", runContext.render(this.status).as(PipelineStatus.class).map(PipelineStatus::apiValue).orElse(null));
        addFilter(filters, "ref", runContext.render(this.ref).as(String.class).orElse(null));
        addFilter(filters, "sha", runContext.render(this.sha).as(String.class).orElse(null));
        addFilter(filters, "source", runContext.render(this.source).as(PipelineSource.class).map(PipelineSource::apiValue).orElse(null));
        addFilter(filters, "updated_after", runContext.render(this.updatedAfter).as(String.class).orElse(null));
        addFilter(filters, "order_by", runContext.render(this.orderBy).as(PipelineOrderBy.class).orElse(PipelineOrderBy.ID).apiValue());
        filters.add("sort=desc");
        filters.add("per_page=" + pageSize);
        var endpoint = buildApiEndpoint("pipelines", runContext) + "?" + String.join("&", filters);

        return Flux.<java.util.List<Pipeline>, PageState> generate(() -> new PageState(1, 0, false), (state, sink) ->
        {
            if (state.done() || state.read() >= rLimit) {
                sink.complete();
                return state;
            }
            try {
                var body = page(runContext, client, endpoint + "&page=" + state.page());
                var rows = body.stream().limit(rLimit - state.read()).map(Pipeline::of).toList();
                if (rows.isEmpty()) {
                    sink.complete();
                } else {
                    sink.next(rows);
                }
                return new PageState(state.page() + 1, state.read() + rows.size(), body.size() < pageSize);
            } catch (Exception e) {
                sink.error(e);
                return state;
            }
        }).flatMapIterable(rows -> rows);
    }

    private java.util.List<Map<String, Object>> page(RunContext runContext, HttpClient client, String endpoint) throws Exception {
        var request = authenticatedRequestBuilder(endpoint, runContext).method("GET").build();

        var response = client.request(request, String.class);
        return readBody(response, new TypeReference<java.util.List<Map<String, Object>>>() {
        }, "GET", endpoint);
    }

    private static void addFilter(java.util.List<String> filters, String name, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    private static Output store(RunContext runContext, Flux<Pipeline> pipelines) throws Exception {
        var file = runContext.workingDir().createTempFile(".ion");
        long count;
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            count = await(FileSerde.writeAll(writer, pipelines));
        }
        URI uri = runContext.storage().putFile(file.toFile());
        return Output.builder().size((int) count).uri(uri).build();
    }

    /**
     * Blocks on the publisher and rethrows the original checked exception instead of Reactor's wrapper.
     */
    private static <T> T await(Mono<T> mono) throws Exception {
        try {
            return mono.block();
        } catch (RuntimeException e) {
            if (Exceptions.unwrap(e) instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private record PageState(int page, int read, boolean done) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Pipelines",
            description = """
                The pipelines found, newest first, when `fetchType` is `FETCH`."""
        )
        private final java.util.List<Pipeline> pipelines;

        @Schema(
            title = "First pipeline",
            description = """
                The first pipeline found when `fetchType` is `FETCH_ONE`; empty when no pipeline matches."""
        )
        private final Pipeline row;

        @Schema(
            title = "Stored pipelines",
            description = """
                Internal storage URI of an ION file with one pipeline per row, when `fetchType` is `STORE`."""
        )
        private final URI uri;

        @Schema(
            title = "Number of pipelines",
            description = """
                Number of pipelines read, for every `fetchType`."""
        )
        private final Integer size;
    }
}
