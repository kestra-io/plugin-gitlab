@PluginSubGroup(
    title = "Pipelines",
    description = "This sub-group of plugins contains tasks for GitLab CI/CD pipelines.\nSet the GitLab host name, project ID, and access token to start a pipeline and wait for its result, read, list, cancel or retry pipelines, and trigger flows when a pipeline finishes.",
    categories = {
        PluginSubGroup.PluginCategory.INFRASTRUCTURE,
        PluginSubGroup.PluginCategory.BUSINESS
    }
)
package io.kestra.plugin.gitlab.pipelines;

import io.kestra.core.models.annotations.PluginSubGroup;
