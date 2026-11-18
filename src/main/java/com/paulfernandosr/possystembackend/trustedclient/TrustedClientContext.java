package com.paulfernandosr.possystembackend.trustedclient;

import java.security.cert.X509Certificate;
import java.util.UUID;

/** Transport evidence, never data asserted by Angular or a forwarded-header wrapper. */
public record TrustedClientContext(boolean proxyAuthenticated, String clientIp, UUID requestId,
                                   String certificateState, X509Certificate certificate) {}
