package io.github.hectorvent.floci.core.common.docker;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves Docker registry credentials from multiple sources with priority:
 * 1. Environment variables (FLOCI_SERVICES_LAMBDA_REGISTRY_*)
 * 2. Docker config.json (~/.docker/config.json)
 * 3. EmulatorConfig YAML
 */
@ApplicationScoped
public class RegistryCredentialResolver {

    private static final Logger LOG = Logger.getLogger(RegistryCredentialResolver.class);

    private static final String ENV_REGISTRY_URL = "FLOCI_SERVICES_LAMBDA_REGISTRY_URL";
    private static final String ENV_REGISTRY_USERNAME = "FLOCI_SERVICES_LAMBDA_REGISTRY_USERNAME";
    private static final String ENV_REGISTRY_PASSWORD = "FLOCI_SERVICES_LAMBDA_REGISTRY_PASSWORD";
    private static final String ENV_REGISTRY_EMAIL = "FLOCI_SERVICES_LAMBDA_REGISTRY_EMAIL";

    private final EmulatorConfig config;

    @Inject
    public RegistryCredentialResolver(EmulatorConfig config) {
        this.config = config;
    }

    /**
     * Resolve registry credentials from all available sources.
     * Priority: Environment Variables > Docker config.json > EmulatorConfig YAML
     */
    public RegistryCredentials resolve() {
        // Try environment variables first
        Optional<RegistryCredentials> envCreds = resolveFromEnvironment();
        if (envCreds.isPresent()) {
            LOG.debug("Registry credentials resolved from environment variables");
            return envCreds.get();
        }

        // Try docker config.json
        Optional<RegistryCredentials> dockerConfigCreds = resolveFromDockerConfig();
        if (dockerConfigCreds.isPresent()) {
            LOG.debug("Registry credentials resolved from Docker config.json");
            return dockerConfigCreds.get();
        }

        // Try YAML config
        Optional<RegistryCredentials> yamlCreds = resolveFromConfig();
        if (yamlCreds.isPresent()) {
            LOG.debug("Registry credentials resolved from EmulatorConfig YAML");
            return yamlCreds.get();
        }

        // No credentials found - return Docker Hub as default
        LOG.debug("No registry credentials configured, using Docker Hub as default");
        return new RegistryCredentials(
            "registry-1.docker.io",
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    /**
     * Resolve credentials from environment variables.
     */
    private Optional<RegistryCredentials> resolveFromEnvironment() {
        String url = System.getenv(ENV_REGISTRY_URL);
        String username = System.getenv(ENV_REGISTRY_USERNAME);
        String password = System.getenv(ENV_REGISTRY_PASSWORD);
        String email = System.getenv(ENV_REGISTRY_EMAIL);

        if (url != null || username != null || password != null) {
            return Optional.of(new RegistryCredentials(
                url != null ? url : "registry-1.docker.io",
                Optional.ofNullable(username),
                Optional.ofNullable(password),
                Optional.ofNullable(email)
            ));
        }

        return Optional.empty();
    }

    /**
     * Resolve credentials from Docker config.json file.
     * Looks for ~/.docker/config.json and parses auth entries.
     */
    private Optional<RegistryCredentials> resolveFromDockerConfig() {
        try {
            Path dockerConfigPath = Paths.get(System.getProperty("user.home"), ".docker", "config.json");
            if (!Files.exists(dockerConfigPath)) {
                return Optional.empty();
            }

            String configContent = Files.readString(dockerConfigPath, StandardCharsets.UTF_8);
            return parseDockerConfig(configContent);
        } catch (IOException e) {
            LOG.debugv("Failed to read Docker config.json: {0}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            LOG.debugv("Failed to parse Docker config.json: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parse Docker config.json and extract the first registry with auth.
     * Note: This is a simplified parser using String operations.
     * For a custom registry, look for "registries" or "auths" section.
     */
    private Optional<RegistryCredentials> parseDockerConfig(String configContent) {
        // Simple JSON parsing to find auth entries
        // Look for "auths" section which contains registry URLs and base64-encoded credentials
        int authsIndex = configContent.indexOf("\"auths\"");
        if (authsIndex == -1) {
            return Optional.empty();
        }

        // Find the first auth entry with credentials
        int searchStart = authsIndex + 7; // Skip past "auths"
        int authIndex = configContent.indexOf("\"auth\"", searchStart);

        if (authIndex == -1) {
            return Optional.empty();
        }

        // Extract registry URL from the auth entry
        String registryUrl = extractRegistryUrl(configContent, authsIndex, authIndex);
        if (registryUrl == null || registryUrl.isEmpty()) {
            return Optional.empty();
        }

        // Extract base64-encoded credentials
        int credStart = configContent.indexOf("\"", authIndex + 6) + 1;
        int credEnd = configContent.indexOf("\"", credStart);

        if (credEnd == -1) {
            return Optional.empty();
        }

        String encodedCreds = configContent.substring(credStart, credEnd);
        return decodeDockerCredentials(registryUrl, encodedCreds);
    }

    /**
     * Extract registry URL from auths section.
     * Looks backwards from auth position to find the registry URL key.
     */
    private String extractRegistryUrl(String configContent, int authsStart, int authPos) {
        // Search backwards for the registry URL (enclosed in quotes)
        int urlEnd = authPos - 1;
        while (urlEnd > authsStart && Character.isWhitespace(configContent.charAt(urlEnd))) {
            urlEnd--;
        }

        if (urlEnd <= authsStart || configContent.charAt(urlEnd) != '"') {
            return null;
        }

        int urlStart = urlEnd - 1;
        while (urlStart > authsStart && configContent.charAt(urlStart) != '"') {
            urlStart--;
        }

        if (urlStart <= authsStart) {
            return null;
        }

        return configContent.substring(urlStart + 1, urlEnd);
    }

    /**
     * Decode base64-encoded credentials from Docker config.json.
     * Format is typically "username:password" base64-encoded.
     */
    private Optional<RegistryCredentials> decodeDockerCredentials(String registryUrl, String encodedCreds) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCreds);
            String decodedCreds = new String(decodedBytes, StandardCharsets.UTF_8);

            int colonIndex = decodedCreds.indexOf(':');
            if (colonIndex == -1) {
                // No colon means we couldn't parse it properly
                return Optional.empty();
            }

            String username = decodedCreds.substring(0, colonIndex);
            String password = decodedCreds.substring(colonIndex + 1);

            return Optional.of(new RegistryCredentials(
                registryUrl,
                Optional.of(username),
                Optional.of(password),
                Optional.empty()
            ));
        } catch (IllegalArgumentException e) {
            LOG.debugv("Failed to decode Docker credentials: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve credentials from EmulatorConfig YAML.
     */
    private Optional<RegistryCredentials> resolveFromConfig() {
        var registryConfig = config.services().lambda().registry();

        Optional<String> url = registryConfig.url();
        Optional<String> username = registryConfig.username();
        Optional<String> password = registryConfig.password();
        Optional<String> email = registryConfig.email();

        if (url.isPresent() || username.isPresent() || password.isPresent()) {
            return Optional.of(new RegistryCredentials(
                url.orElse("registry-1.docker.io"),
                username,
                password,
                email
            ));
        }

        return Optional.empty();
    }
}
