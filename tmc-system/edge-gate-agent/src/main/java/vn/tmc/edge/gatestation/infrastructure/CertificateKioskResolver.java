package vn.tmc.edge.gatestation.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import vn.tmc.edge.gatestation.domain.KioskContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 2. Tra danh tính kiosk theo dấu vân tay SHA-256 của chứng chỉ client mTLS trong kết nối HTTPS.
 * Bảng vân tay → KioskContext được cấp qua provisioning (CONTRACT-V1.md §3: danh tính thiết bị không cấp qua API CRUD).
 * Việc bắt tay TLS và kiểm tra chuỗi chứng chỉ do HttpsServer (needClientAuth) đảm nhiệm; lớp này chỉ ánh xạ.
 * CHƯA kiểm thử với chứng chỉ thật (cần provisioning của Task 7/8); chỉ ghi nhận trong bàn giao.
 */
public final class CertificateKioskResolver implements KioskResolver {
    private final Map<String, KioskContext> byFingerprint;

    /** Khóa là SHA-256 hex chữ thường của chứng chỉ DER. */
    public CertificateKioskResolver(Map<String, KioskContext> byFingerprint) {
        this.byFingerprint = Map.copyOf(Objects.requireNonNull(byFingerprint));
    }

    @Override
    public Optional<KioskContext> resolve(HttpExchange exchange) {
        if (!(exchange instanceof HttpsExchange https)) {
            return Optional.empty();
        }
        try {
            Certificate[] chain = https.getSSLSession().getPeerCertificates();
            if (chain.length == 0) {
                return Optional.empty();
            }
            var digest = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
            return Optional.ofNullable(byFingerprint.get(HexFormat.of().formatHex(digest)));
        } catch (SSLPeerUnverifiedException | CertificateEncodingException | NoSuchAlgorithmException unknown) {
            return Optional.empty();
        }
    }
}
