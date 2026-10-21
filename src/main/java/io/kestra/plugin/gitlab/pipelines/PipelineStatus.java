package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

/**
 * Pipeline statuses accepted by the GitLab pipelines API.
 */
public enum PipelineStatus {
    CREATED,
    WAITING_FOR_RESOURCE,
    PREPARING,
    PENDING,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILED,
    CANCELED,
    SKIPPED,
    MANUAL,
    SCHEDULED;

    /**
     * @return the value sent to and returned by the GitLab API, for example {@code waiting_for_resource}.
     */
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
