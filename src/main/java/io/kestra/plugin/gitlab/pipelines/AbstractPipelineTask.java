package io.kestra.plugin.gitlab.pipelines;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.gitlab.AbstractGitLabTask;

import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPipelineTask extends AbstractGitLabTask {
    protected Pipeline pipelineRequest(RunContext runContext, HttpClient client, String method, String resource, String jsonBody) throws Exception {
        HttpRequest.HttpRequestBuilder request = authenticatedRequestBuilder(buildApiEndpoint(resource, runContext), runContext).method(method);
        if (jsonBody != null) {
            request.body(new HttpRequest.StringRequestBody("application/json", StandardCharsets.UTF_8, jsonBody));
        }

        @SuppressWarnings("unchecked")
        HttpResponse<Map<String, Object>> response = (HttpResponse<Map<String, Object>>) (HttpResponse<?>) client.request(request.build(), Map.class);
        return Pipeline.of(response.getBody());
    }
}
