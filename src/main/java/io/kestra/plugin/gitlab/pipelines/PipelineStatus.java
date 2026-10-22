package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

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

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
