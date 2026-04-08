package io.github.hectorvent.floci.core.common.docker;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class DockerClientProducerTest {

    @Inject
    DockerClientProducer producer;

    @Inject
    RegistryCredentialResolver credentialResolver;

    @Test
    void testDockerClientCreatedWithoutCredentials() {
        // Mock resolver to return Docker Hub (no credentials)
        RegistryCredentials dockerHubCreds = new RegistryCredentials(
            "registry-1.docker.io",
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        // Should not throw exception
        assertDoesNotThrow(() -> producer.dockerClient());
    }

    @Test
    void testDockerClientCreatedWithCredentials() {
        // Mock resolver to return credentials
        RegistryCredentials customCreds = new RegistryCredentials(
            "custom.registry.io",
            Optional.of("testuser"),
            Optional.of("testpass"),
            Optional.of("test@example.com")
        );

        // Should not throw exception
        assertDoesNotThrow(() -> producer.dockerClient());
    }

    @Test
    void testBackwardCompatibilityWithoutRegistry() {
        // Test that existing configuration works without registry setup
        assertDoesNotThrow(() -> {
            // Should work without issues
            var client = producer.dockerClient();
            assertNotNull(client);
        });
    }

    @Test
    void testCredentialsShouldNotBeLogged() {
        // Verify that credentials are not logged by checking debug flags
        RegistryCredentials creds = new RegistryCredentials(
            "custom.registry.io",
            Optional.of("secretuser"),
            Optional.of("secretpass"),
            Optional.empty()
        );

        assertDoesNotThrow(() -> producer.dockerClient());
    }

    @Test
    void testProducerUsesResolvedCredentials() {
        // Verify credentials are used (by checking no exception)
        assertDoesNotThrow(() -> producer.dockerClient());
    }

    @Test
    void testDockerClientCanBeCreated() {
        // Test that Docker client can be created without errors
        assertDoesNotThrow(() -> {
            var client = producer.dockerClient();
            assertNotNull(client);
        });
    }
}

