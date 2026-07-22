package io.github.chrisshi.mom.iam.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 从显式 PEM 资源加载 IAM RSA 密钥；生产环境绝不启动时随机生成。 */
final class IamRsaKeyMaterial {
    private IamRsaKeyMaterial() {
    }

    static JWKSource<SecurityContext> load(
            IamAuthorizationProperties properties, Environment environment) {
        properties.validate();
        IamAuthorizationProperties.SigningKey key = properties.getKey();
        rejectTestKeyInProduction(key, environment);
        RSAPrivateKey privateKey = readPrivateKey(key.getPrivateKeyLocation());
        RSAPublicKey publicKey = readPublicKey(key.getPublicKeyLocation());
        if (!privateKey.getModulus().equals(publicKey.getModulus())
                || publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("IAM RSA 公私钥不匹配或长度小于 2048 位");
        }
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(key.getKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    static void requireProductionIssuer(URI issuer, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !"https".equalsIgnoreCase(issuer.getScheme())) {
            throw new IllegalStateException("生产环境 IAM issuer 必须使用 HTTPS");
        }
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            String pem = resource.getContentAsString(StandardCharsets.US_ASCII);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem, "PRIVATE KEY")));
        }
        catch (Exception exception) {
            throw new IllegalStateException("IAM RSA 私钥无法读取", exception);
        }
    }

    private static RSAPublicKey readPublicKey(Resource resource) {
        try {
            String pem = resource.getContentAsString(StandardCharsets.US_ASCII);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePem(pem, "PUBLIC KEY")));
        }
        catch (Exception exception) {
            throw new IllegalStateException("IAM RSA 公钥无法读取", exception);
        }
    }

    private static byte[] decodePem(String pem, String type) {
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static void rejectTestKeyInProduction(
            IamAuthorizationProperties.SigningKey key, Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        String privateDescription = key.getPrivateKeyLocation().getDescription();
        String publicDescription = key.getPublicKeyLocation().getDescription();
        if (key.isAllowTestKey()
                || privateDescription.contains("/test/")
                || publicDescription.contains("/test/")) {
            throw new IllegalStateException("生产环境禁止使用 IAM 本地测试签名密钥");
        }
    }
}
