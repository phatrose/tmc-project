package vn.tmc.edge.gatestation.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.net.ssl.SSLPeerUnverifiedException;
import vn.tmc.edge.gatestation.domain.KioskContext;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 2 (DEVICE-02).
 * Ánh xạ danh tính Kiosk từ SAN URI của chứng chỉ client X.509 mTLS đã xác thực trust chain:
 * - urn:tmc:kiosk:<kioskId>
 * - urn:tmc:edge:<edgeId>
 * - urn:tmc:factory:<factoryId>
 * - urn:tmc:gate:<gateId> (một hoặc nhiều cổng)
 * KHÔNG fallback sang Subject CN/OU hoặc HTTP header tự khai.
 * Đối chiếu với bảng provisioning đã cấp (byKioskId); sai binding trả Optional.empty() → 403 DEVICE_BINDING_MISMATCH.
 */
public final class SanUriKioskResolver implements KioskResolver {

    private static final int SAN_TYPE_URI = 6;
    private static final String PREFIX_KIOSK = "urn:tmc:kiosk:";
    private static final String PREFIX_EDGE = "urn:tmc:edge:";
    private static final String PREFIX_FACTORY = "urn:tmc:factory:";
    private static final String PREFIX_GATE = "urn:tmc:gate:";

    private final Map<String, KioskContext> provisionedByKioskId;

    public SanUriKioskResolver(Map<String, KioskContext> provisionedByKioskId) {
        this.provisionedByKioskId = Map.copyOf(Objects.requireNonNull(provisionedByKioskId, "provisionedByKioskId"));
    }

    @Override
    public Optional<KioskContext> resolve(HttpExchange exchange) {
        if (!(exchange instanceof HttpsExchange https)) {
            return Optional.empty();
        }
        try {
            Certificate[] chain = https.getSSLSession().getPeerCertificates();
            if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate cert)) {
                return Optional.empty();
            }
            return resolveFromCertificate(cert);
        } catch (SSLPeerUnverifiedException e) {
            return Optional.empty();
        }
    }

    public Optional<KioskContext> resolveFromCertificate(X509Certificate cert) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null || sans.isEmpty()) {
                return Optional.empty();
            }
            String kioskId = null;
            UUID edgeId = null;
            UUID factoryId = null;
            Set<UUID> gateIds = new LinkedHashSet<>();

            for (List<?> entry : sans) {
                if (entry.size() < 2 || !(entry.get(0) instanceof Integer type) || type != SAN_TYPE_URI) {
                    continue;
                }
                if (!(entry.get(1) instanceof String uri)) {
                    continue;
                }
                if (uri.startsWith(PREFIX_KIOSK)) {
                    kioskId = uri.substring(PREFIX_KIOSK.length()).trim();
                } else if (uri.startsWith(PREFIX_EDGE)) {
                    edgeId = UUID.fromString(uri.substring(PREFIX_EDGE.length()).trim());
                } else if (uri.startsWith(PREFIX_FACTORY)) {
                    factoryId = UUID.fromString(uri.substring(PREFIX_FACTORY.length()).trim());
                } else if (uri.startsWith(PREFIX_GATE)) {
                    gateIds.add(UUID.fromString(uri.substring(PREFIX_GATE.length()).trim()));
                }
            }

            if (kioskId == null || kioskId.isBlank() || edgeId == null || factoryId == null || gateIds.isEmpty()) {
                return Optional.empty();
            }
            KioskContext provisioned = provisionedByKioskId.get(kioskId);
            if (provisioned == null) {
                return Optional.empty();
            }
            if (!provisioned.edgeId().equals(edgeId) || !provisioned.factoryId().equals(factoryId)) {
                return Optional.empty();
            }
            if (!provisioned.gateIds().containsAll(gateIds)) {
                return Optional.empty();
            }
            return Optional.of(provisioned);
        } catch (CertificateParsingException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
