package io.kestra.plugin.gitlab;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGitLabTask extends Task {

    @Schema(title = "GitLab URL", description = "GitLab URL")
    @Builder.Default
    private String url = "https://gitlab.com";

    @Schema(title = "Personal Access Token", description = "GitLab Personal Access Token")
    @PluginProperty(dynamic = true)
    private  String token;

    @Schema(title = "Project ID", description = "GitLab project ID")
    @PluginProperty(dynamic = true)
    private String projectId;

    protected HttpClient httpClient(RunContext runContext) throws IllegalVariableEvaluationException {

        HttpConfiguration config = null;

        return  new HttpClient(runContext, config);
    }

    protected HttpRequest.HttpRequestBuilder authenticatedRequestBuilder(String endpoint, RunContext runContext) throws IllegalVariableEvaluationException{
        String baseUrl = runContext.render(this.url);
        String renderedToken = runContext.render(this.token);

        String fullUrl = baseUrl + endpoint;

        return HttpRequest.builder()
            .uri(URI.create(fullUrl))
            .addHeader("PRIVATE-TOKEN", renderedToken)
            .addHeader("Content-Type", "application/json");
    }

}
