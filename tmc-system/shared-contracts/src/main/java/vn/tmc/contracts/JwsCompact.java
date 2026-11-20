package vn.tmc.contracts;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * PHỤ TRÁCH: Task 6 (mới). Tiện ích JWS Compact ES256 thuần JDK, dùng chung
 * giữa Central (ký) và Edge (xác minh). shared-contracts không thêm dependency
 * mới: ES256 = ECDSA P-256 + SHA-256, có sẵn trong JCA (SHA256withECDSA);
 * chữ ký JWS dạng r||s 64 byte còn JDK trả/đợi DER nên đổi định dạng tại đây.
 * Quyết định thư viện: giữ JCA chuẩn Java 21, không bổ sung nimbus-jose-jwt
 * hay BouncyCastle cho đến khi nhóm chốt (README.md mục "Công nghệ").
 *
 * Bảo mật: class không giữ trạng thái, không log signing input hay chữ ký.
 * kid/trust window/revocation thuộc QrKeySet và bộ xác minh, không thuộc class này.
 */
public final class JwsCompact {

    /** Số byte tọa độ P-256 (r/s và x/y đều 32 byte big-endian trong JWS). */
    public static final int P256_FIELD_BYTES = 32;
    /** Độ dài chữ ký JWS ES256: r||s, 64 byte. */
    public static final int ES256_RAW_SIGNATURE_BYTES = 2 * P256_FIELD_BYTES;

    private static final String EC_ALGORITHM = "EC";
    private static final String EC_CURVE_NAME = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private JwsCompact() {
    }

    // ---------- base64url (RFC 7515: không padding) ----------

    public static String encodeBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Giải mã base64url nghiêm ngặt: từ chối padding '=' và input rỗng. */
    public static byte[] decodeBase64Url(String input) {
        if (input == null || input.isEmpty() || input.indexOf('=') >= 0) {
            throw new IllegalArgumentException("base64url không hợp lệ (rỗng hoặc có padding)");
        }
        return Base64.getUrlDecoder().decode(input);
    }

    /**
     * Tách token compact thành đúng 3 phần header/payload/signature.
     * Ném IllegalArgumentException khi sai cấu trúc (số dấu chấm, phần rỗng,
     * chứa padding). Không giải mã; giới hạn độ dài do caller kiểm tra.
     */
    public static String[] splitCompact(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token rỗng");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new IllegalArgumentException("JWS compact cần đúng 3 phần khác rỗng");
        }
        for (String part : parts) {
            if (part.indexOf('=') >= 0) {
                throw new IllegalArgumentException("JWS compact không dùng base64 padding");
            }
        }
        return parts;
    }

    /** Signing input là ASCII của "headerB64.payloadB64". */
    public static byte[] signingInput(String headerB64, String payloadB64) {
        return (headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII);
    }

    // ---------- ECDSA DER <-> raw (r||s) ----------

    /**
     * Chuyển chữ ký DER SEQUENCE{INTEGER r, INTEGER s} thành r||s 64 byte.
     * Ném IllegalArgumentException khi DER sai cấu trúc.
     */
    public static byte[] rawFromDer(byte[] der) {
        if (der == null || der.length < 8) {
            throw new IllegalArgumentException("DER chữ ký quá ngắn");
        }
        if (der[0] != 0x30) {
            throw new IllegalArgumentException("DER phải bắt đầu bằng SEQUENCE");
        }
        int[] sequence = parseDerHeader(der, 0);
        int contentStart = sequence[0];
        int contentEnd = contentStart + sequence[1];
        if (contentEnd != der.length) {
            throw new IllegalArgumentException("DER có dữ liệu thừa hoặc thiếu");
        }
        if (der[contentStart] != 0x02) {
            throw new IllegalArgumentException("DER thành phần r phải là INTEGER");
        }
        int[] rHeader = parseDerHeader(der, contentStart);
        byte[] rBytes = Arrays.copyOfRange(der, rHeader[0], rHeader[0] + rHeader[1]);
        int sStart = rHeader[0] + rHeader[1];
        if (sStart >= contentEnd || der[sStart] != 0x02) {
            throw new IllegalArgumentException("DER thành phần s phải là INTEGER");
        }
        int[] sHeader = parseDerHeader(der, sStart);
        int sEnd = sHeader[0] + sHeader[1];
        if (sEnd != contentEnd) {
            throw new IllegalArgumentException("DER có dữ liệu thừa trong SEQUENCE");
        }
        byte[] sBytes = Arrays.copyOfRange(der, sHeader[0], sEnd);
        return joinFixed32(rBytes, sBytes);
    }

    /** Chuyển r||s 64 byte thành DER SEQUENCE(INTEGER r, INTEGER s). */
    public static byte[] derFromRaw(byte[] raw) {
        if (raw == null || raw.length != ES256_RAW_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("Chữ ký raw ES256 phải 64 byte");
        }
        byte[] rBytes = new BigInteger(1, Arrays.copyOfRange(raw, 0, P256_FIELD_BYTES)).toByteArray();
        byte[] sBytes = new BigInteger(1, Arrays.copyOfRange(raw, P256_FIELD_BYTES, ES256_RAW_SIGNATURE_BYTES)).toByteArray();
        int contentLength = 2 + rBytes.length + 2 + sBytes.length;
        byte[] out = new byte[2 + contentLength];
        out[0] = 0x30;
        out[1] = (byte) contentLength;
        out[2] = 0x02;
        out[3] = (byte) rBytes.length;
        System.arraycopy(rBytes, 0, out, 4, rBytes.length);
        int sHeader = 4 + rBytes.length;
        out[sHeader] = 0x02;
        out[sHeader + 1] = (byte) sBytes.length;
        System.arraycopy(sBytes, 0, out, sHeader + 2, sBytes.length);
        return out;
    }

    /**
     * Đọc header DER tại offset, trả {contentStart, contentLength}.
     * Chỉ nhận short form và long form tối đa 2 byte length (đủ cho P-256).
     */
    private static int[] parseDerHeader(byte[] der, int offset) {
        if (offset < 0 || offset + 2 > der.length) {
            throw new IllegalArgumentException("DER thiếu dữ liệu header");
        }
        int lengthByte = der[offset + 1] & 0xff;
        if ((lengthByte & 0x80) == 0) {
            int length = lengthByte;
            if (offset + 2 + length > der.length) {
                throw new IllegalArgumentException("DER length vượt dữ liệu");
            }
            return new int[] {offset + 2, length};
        }
        int count = lengthByte & 0x7f;
        if (count == 0 || count > 2) {
            throw new IllegalArgumentException("DER length form không hỗ trợ");
        }
        if (offset + 2 + count > der.length) {
            throw new IllegalArgumentException("DER thiếu dữ liệu length");
        }
        int length = 0;
        for (int i = 0; i < count; i++) {
            length = (length << 8) | (der[offset + 2 + i] & 0xff);
        }
        int contentStart = offset + 2 + count;
        if (contentStart + length > der.length) {
            throw new IllegalArgumentException("DER length vượt dữ liệu");
        }
        return new int[] {contentStart, length};
    }

    private static byte[] joinFixed32(byte[] left, byte[] right) {
        byte[] out = new byte[ES256_RAW_SIGNATURE_BYTES];
        byte[] l = fixed32(left);
        byte[] r = fixed32(right);
        System.arraycopy(l, 0, out, 0, P256_FIELD_BYTES);
        System.arraycopy(r, 0, out, P256_FIELD_BYTES, P256_FIELD_BYTES);
        return out;
    }

    /**
     * Chuẩn hóa số không âm thành đúng 32 byte big-endian.
     * Input là giá trị KHÔNG DẤU (tọa độ x/y, r/s): mảng 32 byte với bit cao
     * đặt 1 vẫn hợp lệ và giữ nguyên. Mảng 33 byte chỉ chấp nhận khi là biểu
     * diễn BigInteger có byte dấu 0x00 ở đầu (từ derFromRaw/extract).
     */
    private static byte[] fixed32(byte[] value) {
        if (value == null || value.length == 0 || value.length > P256_FIELD_BYTES + 1) {
            throw new IllegalArgumentException("Thành phần ECDSA/EC nằm ngoài P-256");
        }
        int start = 0;
        int length = value.length;
        if (length == P256_FIELD_BYTES + 1) {
            // 33 byte chỉ là biểu diễn BigInteger thêm byte dấu 0x00 ở đầu
            if (value[0] != 0) {
                throw new IllegalArgumentException("Thành phần ECDSA/EC không hợp lệ");
            }
            start = 1;
            length = P256_FIELD_BYTES;
        }
        byte[] out = new byte[P256_FIELD_BYTES];
        System.arraycopy(value, start, out, P256_FIELD_BYTES - length, length);
        return out;
    }

    // ---------- ký và xác minh ----------

    /** Ký ES256: trả chữ ký JWS dạng r||s 64 byte. */
    public static byte[] signEs256(byte[] signingInput, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signingInput);
            return rawFromDer(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ký ES256 thất bại: " + e.getMessage(), e);
        }
    }

    /** Xác minh ES256 với chữ ký r||s 64 byte; false khi chữ ký sai cấu trúc. */
    public static boolean verifyEs256(byte[] signingInput, byte[] rawSignature, PublicKey publicKey) {
        if (rawSignature == null || rawSignature.length != ES256_RAW_SIGNATURE_BYTES) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            return verifier.verify(derFromRaw(rawSignature));
        } catch (GeneralSecurityException e) {
            // Sai DER/khóa coi như chữ ký không hợp lệ, không ném ra ngoài.
            return false;
        }
    }

    // ---------- khóa công khai P-256 ----------

    /** Dựng ECPublicKey P-256 từ tọa độ x/y (x/y trong QrKeySetEntry là base64url). */
    public static ECPublicKey p256PublicKeyFrom(byte[] x, byte[] y) {
        byte[] xFixed = fixed32(x);
        byte[] yFixed = fixed32(y);
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(EC_ALGORITHM);
            parameters.init(new ECGenParameterSpec(EC_CURVE_NAME));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPoint point = new ECPoint(new BigInteger(1, xFixed), new BigInteger(1, yFixed));
            return (ECPublicKey) KeyFactory.getInstance(EC_ALGORITHM)
                    .generatePublic(new ECPublicKeySpec(point, spec));
        } catch (GeneralSecurityException e) {
            // bao gồm điểm không nằm trên đường cong P-256
            throw new IllegalArgumentException("Khóa công khai P-256 không hợp lệ", e);
        }
    }

    /** Tọa độ x 32 byte từ khóa công khai. */
    public static byte[] extractX(ECPublicKey publicKey) {
        if (publicKey == null || publicKey.getW() == null) {
            throw new IllegalArgumentException("Thiếu điểm công khai EC");
        }
        return fixed32(toPositiveBytes(publicKey.getW().getAffineX()));
    }

    /** Tọa độ y 32 byte từ khóa công khai. */
    public static byte[] extractY(ECPublicKey publicKey) {
        if (publicKey == null || publicKey.getW() == null) {
            throw new IllegalArgumentException("Thiếu điểm công khai EC");
        }
        return fixed32(toPositiveBytes(publicKey.getW().getAffineY()));
    }

    private static byte[] toPositiveBytes(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Tọa độ EC phải không âm");
        }
        return value.toByteArray();
    }
}
