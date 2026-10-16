package com.acme.testing;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Synthetic, in-memory signing keys. Never stores private key material on disk. */
public final class OidcFixture implements AutoCloseable {
    private final RSAKey key;
    private final HttpServer server;
    public final String issuer = "https://issuer.example/realms/billing";
    public OidcFixture() {
        try {
            key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } finally { exchange.close(); }
            });
            server.start();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    public String jwks() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks"; }
    public String token(String scopes, String audience) { return token(scopes, issuer, audience, Instant.now().plusSeconds(300), false); }
    public String token(String scopes, String tokenIssuer, String audience, Instant expiry, boolean wrongSignature) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer(tokenIssuer).subject("operator-1").audience(audience)
                    .issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(expiry)).claim("scope", scopes).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(wrongSignature ? new RSAKeyGenerator(2048).generate() : key));
            return jwt.serialize();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    @Override public void close() { server.stop(0); }
}
