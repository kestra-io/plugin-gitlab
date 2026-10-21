package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

public enum PipelineOrderBy {
    ID,
    STATUS,
    REF,
    UPDATED_AT,
    USER_ID;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
