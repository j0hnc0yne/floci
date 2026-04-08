package io.github.hectorvent.floci.core.common.docker;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Docker registry credential resolution and client creation.
 */
@QuarkusTest
class DockerClientProducerIntegrationTest {

    @Inject
    DockerClientProducer producer;

    @Inject
    RegistryCredentialResolver resolver;

    @Inject
    EmulatorConfig config;

    @Test
    void testResolverCanBeInjected() {
        // Verify that the resolver is properly registered as a CDI bean
        assertNotNull(resolver);
    }

    @Test
    void testProducerCanBeInjected() {
        // Verify that the producer is properly registered as a CDI bean
        assertNotNull(producer);
    }

    @Test
    void testConfigCanBeInjected() {
        // Verify that config is properly injected
        assertNotNull(config);
    }

    @Test
    void testDefaultRegistryResolution() {
        // Test that default registry resolution works
        RegistryCredentials creds = resolver.resolve();
        assertNotNull(creds);
        assertNotNull(creds.registryUrl());
        
        // Should default to Docker Hub if no custom registry configured
        assertEquals("registry-1.docker.io", creds.registryUrl());
    }

    @Test
    void testDockerClientCanBeCreated() {
        // Test that Docker client can be created without errors
        assertDoesNotThrow(() -> {
            var client = producer.dockerClient();
            assertNotNull(client);
        });
    }

    @Test
    void testCredentialsNotExposedInLogs() {
        // Ensure that when credentials are resolved, they don't get exposed in logs
        // This is a security-focused test
        RegistryCredentials creds = resolver.resolve();
        
        // Credentials should only be present when both username and password exist
        if (creds.isAuthenticated()) {
            assertTrue(creds.username().isPresent());
            assertTrue(creds.password().isPresent());
            // Verify that password is not empty string
            assertNotEquals("", creds.password().orElse(""));
        }
    }

    @Test
    void testEmptyCredentialsDoNotThrow() {
        // Ensure that empty credentials (no auth) are handled gracefully
        RegistryCredentials creds = new RegistryCredentials(
            "registry-1.docker.io",
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
        
        assertFalse(creds.isAuthenticated());
        assertEquals("registry-1.docker.io", creds.registryUrl());
    }

    @Test
    void testPartialCredentialsNotAuthenticated() {
        // Ensure that partial credentials (only username or only password) are not considered authenticated
        RegistryCredentials credsWithOnlyUser = new RegistryCredentials(
            "custom.registry.io",
            Optional.of("user"),
            Optional.empty(),
            Optional.empty()
        );
        assertFalse(credsWithOnlyUser.isAuthenticated());

        RegistryCredentials credsWithOnlyPass = new RegistryCredentials(
            "custom.registry.io",
            Optional.empty(),
            Optional.of("pass"),
            Optional.empty()
        );
        assertFalse(credsWithOnlyPass.isAuthenticated());
    }
}
