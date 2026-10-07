package org.hugopalma.pipelinenotifier.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;

/**
 * Stores each connection's access token in the IDE password safe (macOS Keychain, Windows
 * Credential Store, libsecret) rather than in the plain-text settings XML.
 *
 * <p>Every method here blocks on OS keychain I/O and must never be called on the EDT.
 */
public final class TokenStore {

    private static final String SUBSYSTEM = "Pipeline Notifier";
    private static final String USER = "token";

    private TokenStore() {
    }

    /** Keyed by provider as well as host, so two services can never be handed each other's token. */
    private static CredentialAttributes attributes(String provider, String host) {
        String serviceName = CredentialAttributesKt.generateServiceName(SUBSYSTEM + " (" + provider + ")", host);
        return new CredentialAttributes(serviceName, USER);
    }

    public static String get(String provider, String host) {
        String password = PasswordSafe.getInstance().getPassword(attributes(provider, host));
        return (password == null || password.isBlank()) ? null : password;
    }

    public static void set(String provider, String host, String token) {
        CredentialAttributes attributes = attributes(provider, host);
        if (token == null || token.isBlank()) {
            PasswordSafe.getInstance().set(attributes, null);
        } else {
            PasswordSafe.getInstance().set(attributes, new Credentials(USER, token));
        }
    }
}
