package vn.tmc.central;

import java.util.Map;
import java.util.UUID;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PHỤ TRÁCH: Task 8 (DEVICE-01, 04, 06, 10).
 * Cấu hình cổng HTTPS mTLS trực tiếp riêng cho thiết bị Edge trên Tomcat khi bật tmc.mtls.enabled=true:
 * - Bắt buộc client certificate (certificateVerification=required), truststore CA riêng.
 * - Không đụng cổng HTTP/HTTPS người dùng; không bật trust-all.
 */
@Configuration(proxyBeanMethods = false)
public class CentralMtlsConnectorConfiguration {

    @Bean
    CentralMtlsDeviceAuthenticator centralMtlsDeviceAuthenticator(
            @Value("${tmc.mtls.provisioned-edges:}") String provisionedPairs
    ) {
        CentralMtlsDeviceAuthenticator auth = new CentralMtlsDeviceAuthenticator();
        if (provisionedPairs != null && !provisionedPairs.isBlank()) {
            for (String pair : provisionedPairs.split(",")) {
                String[] parts = pair.trim().split("=");
                if (parts.length == 2) {
                    auth.registerProvisionedEdge(UUID.fromString(parts[0].trim()), UUID.fromString(parts[1].trim()));
                }
            }
        }
        return auth;
    }

    @Bean
    @ConditionalOnProperty(name = "tmc.mtls.enabled", havingValue = "true")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> mtlsDeviceConnectorCustomizer(
            @Value("${tmc.mtls.port:8443}") int port,
            @Value("${tmc.mtls.keystore}") String keystorePath,
            @Value("${tmc.mtls.keystore-password}") String keystorePassword,
            @Value("${tmc.mtls.truststore}") String truststorePath,
            @Value("${tmc.mtls.truststore-password}") String truststorePassword
    ) {
        return factory -> {
            Connector connector = new Connector(Http11NioProtocol.class.getName());
            connector.setScheme("https");
            connector.setSecure(true);
            connector.setPort(port);

            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            protocol.setSSLEnabled(true);

            SSLHostConfig sslHostConfig = new SSLHostConfig();
            sslHostConfig.setProtocols("TLSv1.3+TLSv1.2");
            sslHostConfig.setCertificateVerification("required");
            sslHostConfig.setTruststoreFile(truststorePath);
            sslHostConfig.setTruststorePassword(truststorePassword);
            sslHostConfig.setTruststoreType("PKCS12");

            SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                    sslHostConfig, SSLHostConfigCertificate.Type.EC);
            cert.setCertificateKeystoreFile(keystorePath);
            cert.setCertificateKeystorePassword(keystorePassword);
            cert.setCertificateKeystoreType("PKCS12");
            sslHostConfig.addCertificate(cert);

            protocol.addSslHostConfig(sslHostConfig);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
