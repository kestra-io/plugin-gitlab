package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

/**
 * Pipeline sources accepted by the {@code source} filter of the GitLab pipelines API.
 */
public enum PipelineSource {
    PUSH,
    WEB,
    TRIGGER,
    SCHEDULE,
    API,
    EXTERNAL,
    PIPELINE,
    CHAT,
    WEBIDE,
    MERGE_REQUEST_EVENT,
    EXTERNAL_PULL_REQUEST_EVENT,
    PARENT_PIPELINE,
    ONDEMAND_DAST_SCAN,
    ONDEMAND_DAST_VALIDATION,
    SECURITY_ORCHESTRATION_POLICY;

    /**
     * @return the value sent to the GitLab API, for example {@code merge_request_event}.
     */
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
