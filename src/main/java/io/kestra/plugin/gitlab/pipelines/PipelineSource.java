package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

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

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
