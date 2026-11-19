package vn.tmc.edge.edgesync.application;

import vn.tmc.contracts.BootstrapResponse.Gate;
import vn.tmc.contracts.BootstrapResponse.KeyState;
import vn.tmc.contracts.BootstrapResponse.RevocationKind;
import vn.tmc.contracts.BootstrapResponse.SigningKey;
import vn.tmc.edge.edgesync.domain.BootstrapCache;
import vn.tmc.edge.edgesync.domain.BootstrapCache.Snapshot;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Local policy and key lookup for Task 2/6. The refresh signal queues background work. */
public final class CachedAccess {
    public enum Decision { ALLOW, UNKNOWN_KID, REVOKED, CACHE_UNAVAILABLE, GATE_DISABLED, OUTSIDE_POLICY }
    public record KeyLookup(Decision decision, SigningKey key) {}
    public record Access(Decision decision, Gate gate) {}

    private final BootstrapCache cache;
    private final Runnable signalKeyRefresh;

    public CachedAccess(BootstrapCache cache, Runnable signalKeyRefresh) {
        this.cache = Objects.requireNonNull(cache);
        this.signalKeyRefresh = Objects.requireNonNull(signalKeyRefresh);
    }

    public KeyLookup lookupKey(String kid, Instant now) {
        return cache.snapshot().filter(snapshot -> snapshot.trustedAt(now))
                .map(snapshot -> lookupKey(snapshot, kid, now))
                .orElseGet(() -> new KeyLookup(Decision.CACHE_UNAVAILABLE, null));
    }

    private KeyLookup lookupKey(Snapshot snapshot, String kid, Instant now) {
        var revokedKid = snapshot.revocations().stream().anyMatch(entry -> switch (entry.kind()) {
            case KID -> entry.value().equals(kid) && !now.isBefore(entry.effectiveAt());
            default -> false;
        });
        if (revokedKid) {
            return new KeyLookup(Decision.REVOKED, null);
        }
        for (var key : snapshot.keySet().keys()) {
            if (key.kid().equals(kid)) {
                if (key.state() == KeyState.REVOKED) {
                    return new KeyLookup(Decision.REVOKED, null);
                }
                if (now.isBefore(key.notBefore()) || !now.isBefore(key.notAfter())) {
                    return new KeyLookup(Decision.CACHE_UNAVAILABLE, null);
                }
                return new KeyLookup(Decision.ALLOW, key);
            }
        }
        signalKeyRefresh.run();
        return new KeyLookup(Decision.UNKNOWN_KID, null);
    }

    /** Call after Task 6 verifies the token with its cached public key. */
    public Access evaluate(UUID employeeId, UUID jti, String kid, Instant tokenIssuedAt, UUID gateId, Instant now) {
        Objects.requireNonNull(employeeId);
        Objects.requireNonNull(jti);
        Objects.requireNonNull(tokenIssuedAt);
        var current = cache.snapshot().filter(snapshot -> snapshot.trustedAt(now));
        if (current.isEmpty()) {
            return new Access(Decision.CACHE_UNAVAILABLE, null);
        }
        var snapshot = current.orElseThrow();
        var key = lookupKey(snapshot, kid, now);
        if (key.decision() != Decision.ALLOW) {
            return new Access(key.decision(), null);
        }
        boolean revoked = snapshot.revocations().stream().filter(entry -> !now.isBefore(entry.effectiveAt()))
                .anyMatch(entry -> switch (entry.kind()) {
                    case SUB -> entry.value().equals(employeeId.toString()) && !tokenIssuedAt.isAfter(entry.revokedBefore());
                    case JTI -> entry.value().equals(jti.toString());
                    case KID -> false; // Evaluated in lookupKey.
                });
        if (revoked) {
            return new Access(Decision.REVOKED, null);
        }

        var gate = snapshot.gates().stream().filter(candidate -> candidate.id().equals(gateId) && candidate.enabled()).findFirst();
        if (gate.isEmpty()) {
            return new Access(Decision.GATE_DISABLED, null);
        }
        boolean allowed = snapshot.policy().employees().stream()
                .filter(employee -> employee.active() && employee.employeeId().equals(employeeId))
                .flatMap(employee -> employee.windows().stream())
                .anyMatch(window -> window.gateId().equals(gateId) && window.direction() == gate.orElseThrow().direction()
                        && !now.isBefore(window.startsAt()) && now.isBefore(window.endsAt()));
        return allowed ? new Access(Decision.ALLOW, gate.orElseThrow()) : new Access(Decision.OUTSIDE_POLICY, null);
    }

    public boolean readyForOffline24Hours(Instant now) {
        return cache.snapshot().filter(snapshot -> readyForOffline24Hours(snapshot, now)).isPresent();
    }

    public static boolean readyForOffline24Hours(Snapshot snapshot, Instant now) {
        var horizon = now.plus(BootstrapCache.OFFLINE_TARGET);
        if (!snapshot.trustedAt(now) || snapshot.trustDeadline().isBefore(horizon)) {
            return false;
        }
        boolean keyReady = snapshot.keySet().keys().stream().anyMatch(key -> key.state() == KeyState.ACTIVE
                && !now.isBefore(key.notBefore()) && !key.notAfter().isBefore(horizon)
                && snapshot.revocations().stream().noneMatch(entry -> entry.kind() == RevocationKind.KID
                        && entry.value().equals(key.kid()) && entry.effectiveAt().isBefore(horizon)));
        return keyReady && snapshot.policy().employees().stream().filter(employee -> employee.active())
                .flatMap(employee -> employee.windows().stream())
                .anyMatch(window -> window.startsAt().isBefore(horizon) && now.isBefore(window.endsAt())
                        && snapshot.gates().stream().anyMatch(gate -> gate.enabled() && gate.id().equals(window.gateId())));
    }
}
