package io.kestra.plugin.gitlab.pipelines;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
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
        var endpoint = buildApiEndpoint(resource, runContext);
        var request = authenticatedRequestBuilder(endpoint, runContext).method(method);
        if (jsonBody != null) {
            request.body(new HttpRequest.StringRequestBody("application/json", StandardCharsets.UTF_8, jsonBody));
        }

        var response = client.request(request.build(), String.class);
        return Pipeline.of(readBody(response, new TypeReference<Map<String, Object>>() {
        }, method, endpoint));
    }

    // Read as text first: the HTTP client fails with an opaque Jackson error on an empty 2xx body.
    static <T> T readBody(HttpResponse<String> response, TypeReference<T> type, String method, String endpoint) throws IOException {
        var body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(
                "Empty response from the GitLab API for %s %s; check that `url` and `apiPath` point to the GitLab REST API.".formatted(method, endpoint)
            );
        }
        return JacksonMapper.ofJson().readValue(body, type);
    }

    static int boundedInt(Integer value, String property, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException("`%s` must be between %d and %d, got %s.".formatted(property, min, max, value));
        }
        return value;
    }
}
