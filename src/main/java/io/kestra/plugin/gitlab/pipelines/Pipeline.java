package io.kestra.plugin.gitlab.pipelines;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.tasks.Output;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Pipeline implements Output {
    static final Set<String> TERMINAL_STATUSES = Set.of("success", "failed", "canceled", "skipped", "manual");
    static final Set<String> FAILED_STATUSES = Set.of("failed", "canceled");

    @Schema(
        title = "Pipeline ID",
        description = """
            Global ID of the pipeline, used in the pipeline API endpoints."""
    )
    private final Long id;

    @Schema(
        title = "Pipeline IID",
        description = """
            ID of the pipeline within its project, as shown in the GitLab UI (`#12`)."""
    )
    private final Long iid;

    @Schema(
        title = "Project ID",
        description = """
            Numeric ID of the project the pipeline belongs to."""
    )
    private final Long projectId;

    @Schema(
        title = "Pipeline status",
        description = """
            One of `created`, `waiting_for_resource`, `preparing`, `pending`, `running`, `canceling`, `success`, `failed`, `canceled`, `skipped`, `manual` or `scheduled`."""
    )
    private final String status;

    @Schema(
        title = "Git ref",
        description = """
            Branch or tag the pipeline runs for."""
    )
    private final String ref;

    @Schema(
        title = "Commit SHA",
        description = """
            SHA of the commit the pipeline runs for."""
    )
    private final String sha;

    @Schema(
        title = "Pipeline source",
        description = """
            What started the pipeline, for example `push`, `web`, `api`, `schedule` or `merge_request_event`."""
    )
    private final String source;

    @Schema(
        title = "Pipeline URL",
        description = """
            Link to the pipeline in the GitLab UI."""
    )
    private final String webUrl;

    @Schema(title = "Creation time")
    private final Instant createdAt;

    @Schema(title = "Last update time")
    private final Instant updatedAt;

    @Schema(
        title = "Start time",
        description = """
            Empty while the pipeline has not started."""
    )
    private final Instant startedAt;

    @Schema(
        title = "Finish time",
        description = """
            Empty while the pipeline is still running."""
    )
    private final Instant finishedAt;

    @Schema(
        title = "Duration in seconds",
        description = """
            Time the pipeline spent running; empty while it is still running."""
    )
    private final Integer duration;

    static Pipeline of(Map<String, Object> body) {
        return Pipeline.builder()
            .id(asLong(body.get("id")))
            .iid(asLong(body.get("iid")))
            .projectId(asLong(body.get("project_id")))
            .status((String) body.get("status"))
            .ref((String) body.get("ref"))
            .sha((String) body.get("sha"))
            .source((String) body.get("source"))
            .webUrl((String) body.get("web_url"))
            .createdAt(asInstant(body.get("created_at")))
            .updatedAt(asInstant(body.get("updated_at")))
            .startedAt(asInstant(body.get("started_at")))
            .finishedAt(asInstant(body.get("finished_at")))
            .duration(body.get("duration") instanceof Number number ? number.intValue() : null)
            .build();
    }

    boolean isTerminal() {
        return TERMINAL_STATUSES.contains(status);
    }

    boolean isFailed() {
        return FAILED_STATUSES.contains(status);
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Instant asInstant(Object value) {
        return value instanceof String text && !text.isBlank() ? Instant.parse(text) : null;
    }
}
