package io.kestra.plugin.gitlab.core;

import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.context.annotation.Value;


@KestraTest
public abstract class AbstractGitLabTest {

    @Value("${kestra.gitlab.url}")
    private String url;


    @Value("${kestra.gitlab.token}")
    private String token;

    @Value("${kestra.gitlab.projectId")
    private String projectId;
}
