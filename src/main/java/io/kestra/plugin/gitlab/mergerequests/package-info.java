@PluginSubGroup(
    title = "Merge Requests",
    description = "Tasks that create, update, and fetch GitLab Merge Requests.\nSet the GitLab host name, project ID, and access token to handle Merge Requests.",
    categories = {
        PluginSubGroup.PluginCategory.INFRASTRUCTURE,
        PluginSubGroup.PluginCategory.BUSINESS
    }
)
package io.kestra.plugin.gitlab.mergerequests;

import io.kestra.core.models.annotations.PluginSubGroup;