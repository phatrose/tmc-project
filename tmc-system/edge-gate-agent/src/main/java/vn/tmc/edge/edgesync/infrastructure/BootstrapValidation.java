package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.*;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

final class BootstrapValidation {
    private static final long MAX_REVISION = 9_007_199_254_740_991L;
    private static final ECParameterSpec P256 = p256();

    private BootstrapValidation() {}

    static void validate(BootstrapCache.Identity identity, BootstrapResponse bundle, Instant now) {
        check(bundle != null && identity.edgeId().equals(bundle.edgeId()) && identity.factoryId().equals(bundle.factoryId()),
                "Bootstrap identity differs from the provisioned identity");
        check(bundle.mode() != null && version(bundle.revision()) && bundle.revision() > 0, "Invalid bootstrap revision");
        if (bundle.mode() == Mode.FULL) {
            check(bundle.baseRevision() == 0, "FULL requires baseRevision zero");
        } else {
            check(bundle.baseRevision() > 0 && bundle.baseRevision() < bundle.revision(), "Invalid DELTA revision range");
        }
        check(validTime(bundle.generatedAt()) && validTime(bundle.trustedUntil()) && !bundle.generatedAt().isAfter(now)
                && bundle.trustedUntil().isAfter(now), "Bootstrap trust is unavailable");
        var limit = bundle.generatedAt().plus(BootstrapCache.FULL_TRUST);
        check(!bundle.trustedUntil().isAfter(limit) && (bundle.mode() != Mode.FULL || bundle.trustedUntil().equals(limit)),
                "Invalid bootstrap trust horizon");

        validateKeys(bundle.keySet(), now);
        var gates = new HashMap<UUID, Gate>();
        for (var gate : bundle.gates()) {
            check(gate.id() != null && version(gate.version()) && text(gate.name()) && text(gate.turnstileApiId())
                    && identity.edgeId().equals(gate.edgeId()) && identity.factoryId().equals(gate.factoryId())
                    && gate.direction() != null && gates.putIfAbsent(gate.id(), gate) == null, "Invalid gate snapshot");
        }
        var policy = bundle.policy();
        check(policy != null && version(policy.version()), "Policy snapshot is missing or invalid");
        var employees = new HashSet<UUID>();
        for (var employee : policy.employees()) {
            check(employee.employeeId() != null && employees.add(employee.employeeId()), "Invalid policy employee");
            for (var window : employee.windows()) {
                var gate = gates.get(window.gateId());
                check(gate != null && gate.direction() == window.direction() && validTime(window.startsAt())
                        && validTime(window.endsAt()) && window.startsAt().isBefore(window.endsAt()), "Invalid policy window");
            }
        }

        var revocations = new HashSet<UUID>();
        for (var change : bundle.revocations()) {
            validateRevocation(change);
            if (bundle.mode() == Mode.FULL) {
                check(change.op() == Operation.UPSERT && revocations.add(change.id()), "FULL revocations must be unique active entries");
            }
        }
    }

    static void validateKeys(KeySet keys, Instant now) {
        check(keys != null && version(keys.version()) && validTime(keys.generatedAt()) && validTime(keys.trustedUntil())
                && !keys.generatedAt().isAfter(now) && keys.generatedAt().isBefore(keys.trustedUntil())
                && keys.trustedUntil().isAfter(now), "Key snapshot is missing or invalid");
        var kids = new HashSet<String>();
        for (var key : keys.keys()) {
            check(text(key.kid()) && kids.add(key.kid()) && "EC".equals(key.kty()) && "P-256".equals(key.crv())
                    && "ES256".equals(key.alg()) && "sig".equals(key.use()) && key.state() != null
                    && validTime(key.notBefore()) && validTime(key.notAfter()) && key.notBefore().isBefore(key.notAfter())
                    && isCurvePoint(key.x(), key.y()), "Invalid signing key");
        }
    }

    private static void validateRevocation(RevocationChange change) {
        check(change.id() != null && change.op() != null && change.kind() != null && text(change.value())
                && validTime(change.effectiveAt()), "Invalid revocation entry");
        check(change.kind() == RevocationKind.SUB ? validTime(change.revokedBefore()) : change.revokedBefore() == null,
                "Invalid revocation cutoff");
        if (change.kind() != RevocationKind.KID) {
            try {
                check(UUID.fromString(change.value()).toString().equals(change.value()), "Invalid revocation UUID");
            } catch (IllegalArgumentException malformed) {
                throw new BootstrapProtocolException("Invalid revocation UUID", malformed);
            }
        }
    }

    private static boolean version(long value) {
        return value >= 0 && value <= MAX_REVISION;
    }

    private static boolean validTime(Instant value) {
        if (value == null) {
            return false;
        }
        try {
            return value.equals(Instant.ofEpochMilli(value.toEpochMilli()));
        } catch (ArithmeticException invalid) {
            return false;
        }
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new BootstrapProtocolException(message);
        }
    }

    private static ECParameterSpec p256() {
        try {
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("JDK P-256 parameters are unavailable", unavailable);
        }
    }

    private static boolean isCurvePoint(String encodedX, String encodedY) {
        if (encodedX == null || encodedY == null || !encodedX.matches("[A-Za-z0-9_-]{43}")
                || !encodedY.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        var x = new BigInteger(1, Base64.getUrlDecoder().decode(encodedX));
        var y = new BigInteger(1, Base64.getUrlDecoder().decode(encodedY));
        var curve = P256.getCurve();
        var prime = ((ECFieldFp) curve.getField()).getP();
        return x.compareTo(prime) < 0 && y.compareTo(prime) < 0
                && y.pow(2).mod(prime).equals(x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(prime));
    }
}
