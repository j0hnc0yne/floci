package io.github.hectorvent.floci.core.common.docker;

import java.util.Optional;

/**
 * Resolved Docker registry credentials from any source (env vars, docker config.json, or YAML config).
 */
public record RegistryCredentials(
    String registryUrl,
    Optional<String> username,
    Optional<String> password,
    Optional<String> email
) {

    /**
     * Check if credentials are actually provided (username and password present).
     */
    public boolean isAuthenticated() {
        return username.isPresent() && password.isPresent();
    }

    /**
     * Get username or empty Optional if not provided.
     */
    @Override
    public Optional<String> username() {
        return username;
    }

    /**
     * Get password or empty Optional if not provided.
     */
    @Override
    public Optional<String> password() {
        return password;
    }

    /**
     * Get email or empty Optional if not provided.
     */
    @Override
    public Optional<String> email() {
        return email;
    }
}
