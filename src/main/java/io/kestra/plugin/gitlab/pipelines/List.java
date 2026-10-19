package io.kestra.plugin.gitlab.pipelines;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
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
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List GitLab CI/CD pipelines",
    description = "Lists the pipelines of a project, newest first, filtered by status, ref, commit, source or update time. Returns the pipelines in the output (`FETCH`) or stores them in an internal storage file (`STORE`)."
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
                    status: failed
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
        )
    }
)
public class List extends AbstractPipelineTask implements RunnableTask<List.Output> {
    private static final int MAX_PAGE_SIZE = 100;

    @Schema(title = "Status filter", description = "Only pipelines with this status, for example `running`, `success`, `failed` or `canceled`.")
    @PluginProperty(group = "main")
    private Property<String> status;

    @Schema(title = "Ref filter", description = "Only pipelines of this branch or tag.")
    @PluginProperty(group = "main")
    private Property<String> ref;

    @Schema(title = "Commit SHA filter", description = "Only pipelines of this commit.")
    @PluginProperty(group = "main")
    private Property<String> sha;

    @Schema(title = "Source filter", description = "Only pipelines started by this source, for example `push`, `web`, `api`, `schedule` or `merge_request_event`.")
    @PluginProperty(group = "main")
    private Property<String> source;

    @Schema(title = "Updated after", description = "Only pipelines updated after this ISO 8601 date-time.")
    @PluginProperty(group = "main")
    private Property<String> updatedAfter;

    @Schema(title = "Order by", description = "Sort field, newest first: `id`, `status`, `ref`, `updated_at` or `user_id`.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<String> orderBy = Property.ofValue("id");

    @Schema(title = "Maximum number of pipelines", description = "Pages through the API until this many pipelines are read.")
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Integer> limit = Property.ofValue(20);

    @Schema(title = "Fetch type", description = "`FETCH` returns the pipelines in the `pipelines` output, `STORE` writes them to an internal storage file returned as `uri`.")
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        int rLimit = runContext.render(this.limit).as(Integer.class).orElse(20);
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        int pageSize = Math.max(1, Math.min(rLimit, MAX_PAGE_SIZE));

        java.util.List<String> filters = new ArrayList<>();
        addFilter(filters, "status", runContext.render(this.status).as(String.class).orElse(null));
        addFilter(filters, "ref", runContext.render(this.ref).as(String.class).orElse(null));
        addFilter(filters, "sha", runContext.render(this.sha).as(String.class).orElse(null));
        addFilter(filters, "source", runContext.render(this.source).as(String.class).orElse(null));
        addFilter(filters, "updated_after", runContext.render(this.updatedAfter).as(String.class).orElse(null));
        addFilter(filters, "order_by", runContext.render(this.orderBy).as(String.class).orElse("id"));
        filters.add("sort=desc");
        filters.add("per_page=" + pageSize);

        java.util.List<Pipeline> pipelines = new ArrayList<>();
        try (HttpClient client = httpClient(runContext)) {
            for (int page = 1; pipelines.size() < rLimit; page++) {
                String endpoint = buildApiEndpoint("pipelines", runContext) + "?" + String.join("&", filters) + "&page=" + page;
                HttpRequest request = authenticatedRequestBuilder(endpoint, runContext).method("GET").build();

                @SuppressWarnings("unchecked")
                HttpResponse<java.util.List<Map<String, Object>>> response = (HttpResponse<java.util.List<Map<String, Object>>>) (HttpResponse<?>) client
                    .request(request, java.util.List.class);
                java.util.List<Map<String, Object>> body = response.getBody();

                body.stream().limit(rLimit - pipelines.size()).map(Pipeline::of).forEach(pipelines::add);
                if (body.size() < pageSize) {
                    break;
                }
            }
        }

        runContext.logger().info("Found {} GitLab pipeline(s)", pipelines.size());
        Output.OutputBuilder output = Output.builder().size(pipelines.size());
        if (rFetchType == FetchType.STORE) {
            return output.uri(store(runContext, pipelines)).build();
        }
        return output.pipelines(pipelines).build();
    }

    private static void addFilter(java.util.List<String> filters, String name, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    private static URI store(RunContext runContext, java.util.List<Pipeline> pipelines) throws Exception {
        File file = runContext.workingDir().createTempFile(".ion").toFile();
        try (var writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            FileSerde.writeAll(writer, Flux.fromIterable(pipelines)).block();
        }
        return runContext.storage().putFile(file);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Pipelines", description = "The pipelines found, newest first, when `fetchType` is `FETCH`.")
        private final java.util.List<Pipeline> pipelines;

        @Schema(title = "Stored pipelines", description = "Internal storage URI of an ION file with one pipeline per row, when `fetchType` is `STORE`.")
        private final URI uri;

        @Schema(title = "Number of pipelines")
        private final Integer size;
    }
}
