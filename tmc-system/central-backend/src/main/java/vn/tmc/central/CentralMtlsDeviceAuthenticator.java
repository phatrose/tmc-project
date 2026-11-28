package vn.tmc.central;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.tmc.contracts.DeviceContext;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 3, 5, 7 (DEVICE-01, 03, 04, 10).
 * Trích xuất và xác thực danh tính Edge từ chứng thư số X.509 client mTLS trực tiếp tại Central:
 * - Kiểm tra hiệu lực thời gian chứng thư (checkValidity).
 * - Chỉ đọc SAN URI (type=6) theo định dạng chuẩn:
 *     urn:tmc:edge:<edgeId>
 *     urn:tmc:factory:<factoryId>
 * - KHÔNG fallback sang Subject CN/OU hoặc HTTP header X-Edge-Id/X-Factory-Id tự khai.
 * - Đối chiếu mapping provisioning (edgeId → factoryId); chứng thư chưa provisioning hoặc sai factoryId bị từ chối.
 */
public final class CentralMtlsDeviceAuthenticator {

    private static final int SAN_TYPE_URI = 6;
    private static final String PREFIX_EDGE = "urn:tmc:edge:";
    private static final String PREFIX_FACTORY = "urn:tmc:factory:";

    private final Map<UUID, UUID> provisionedEdgeToFactory = new ConcurrentHashMap<>();

    public CentralMtlsDeviceAuthenticator() {}

    public CentralMtlsDeviceAuthenticator(Map<UUID, UUID> initialProvisioning) {
        if (initialProvisioning != null) {
            provisionedEdgeToFactory.putAll(initialProvisioning);
        }
    }

    public void registerProvisionedEdge(UUID edgeId, UUID factoryId) {
        provisionedEdgeToFactory.put(
                Objects.requireNonNull(edgeId, "edgeId"),
                Objects.requireNonNull(factoryId, "factoryId"));
    }

    public Optional<DeviceContext> authenticate(X509Certificate[] chain) {
        if (chain == null || chain.length == 0 || chain[0] == null) {
            return Optional.empty();
        }
        X509Certificate leaf = chain[0];
        try {
            leaf.checkValidity();
            Collection<List<?>> sans = leaf.getSubjectAlternativeNames();
            if (sans == null || sans.isEmpty()) {
                return Optional.empty();
            }
            UUID edgeId = null;
            UUID factoryId = null;
            for (List<?> entry : sans) {
                if (entry.size() < 2 || !(entry.get(0) instanceof Integer type) || type != SAN_TYPE_URI) {
                    continue;
                }
                if (!(entry.get(1) instanceof String uri)) {
                    continue;
                }
                if (uri.startsWith(PREFIX_EDGE)) {
                    edgeId = UUID.fromString(uri.substring(PREFIX_EDGE.length()).trim());
                } else if (uri.startsWith(PREFIX_FACTORY)) {
                    factoryId = UUID.fromString(uri.substring(PREFIX_FACTORY.length()).trim());
                }
            }
            if (edgeId == null || factoryId == null) {
                return Optional.empty();
            }
            UUID expectedFactory = provisionedEdgeToFactory.get(edgeId);
            if (expectedFactory == null || !expectedFactory.equals(factoryId)) {
                return Optional.empty();
            }
            return Optional.of(new DeviceContext(edgeId, factoryId));
        } catch (CertificateExpiredException | CertificateNotYetValidException
                 | CertificateParsingException | IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }
}
