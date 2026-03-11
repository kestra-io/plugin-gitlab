
package io.kestra.plugin.gitlab;

import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.kestra.core.junit.annotations.KestraTest;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@KestraTest
public abstract class WireMockTest {
    @RegisterExtension
    public static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();
}
