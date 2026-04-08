package io.github.hectorvent.floci.core.common.docker;

import io.github.hectorvent.floci.config.EmulatorConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * CDI producer for the DockerClient singleton bean.
 */
@ApplicationScoped
public class DockerClientProducer {

    private static final Logger LOG = Logger.getLogger(DockerClientProducer.class);

    private final EmulatorConfig config;
    private final RegistryCredentialResolver credentialResolver;

    @Inject
    public DockerClientProducer(EmulatorConfig config, RegistryCredentialResolver credentialResolver) {
        this.config = config;
        this.credentialResolver = credentialResolver;
    }

    @Produces
    @ApplicationScoped
    public DockerClient dockerClient() {
        String dockerHost = config.services().lambda().dockerHost();
        LOG.infov("Creating DockerClient for host: {0}", dockerHost);

        var configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost);

        // Resolve and apply registry credentials
        RegistryCredentials credentials = credentialResolver.resolve();
        if (credentials.isAuthenticated()) {
            LOG.debugv("Applying registry credentials for: {0}", credentials.registryUrl());
            configBuilder.withRegistryUrl(credentials.registryUrl());
            
            credentials.username().ifPresent(configBuilder::withRegistryUsername);
            credentials.password().ifPresent(configBuilder::withRegistryPassword);
            credentials.email().ifPresent(configBuilder::withRegistryEmail);
        }

        DefaultDockerClientConfig clientConfig = configBuilder.build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(5))
                .build();

        return DockerClientImpl.getInstance(clientConfig, httpClient);
    }
}

