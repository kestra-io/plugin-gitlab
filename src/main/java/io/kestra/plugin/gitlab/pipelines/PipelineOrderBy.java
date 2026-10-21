package io.kestra.plugin.gitlab.pipelines;

import java.util.Locale;

/**
 * Sort fields accepted by the {@code order_by} parameter of the GitLab pipelines API.
 */
public enum PipelineOrderBy {
    ID,
    STATUS,
    REF,
    UPDATED_AT,
    USER_ID;

    /**
     * @return the value sent to the GitLab API, for example {@code updated_at}.
     */
    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
