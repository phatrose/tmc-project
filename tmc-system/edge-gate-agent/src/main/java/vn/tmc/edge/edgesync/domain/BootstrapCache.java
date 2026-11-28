package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.Gate;
import vn.tmc.contracts.BootstrapResponse.KeySet;
import vn.tmc.contracts.BootstrapResponse.PolicySnapshot;
import vn.tmc.contracts.BootstrapResponse.RevocationChange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BootstrapCache {
    Duration FULL_TRUST = Duration.ofHours(48);
    Duration RENEW_BELOW = Duration.ofHours(36);
    Duration OFFLINE_TARGET = Duration.ofHours(24);

    record Identity(UUID edgeId, UUID factoryId) {
        public Identity {
            java.util.Objects.requireNonNull(edgeId, "edgeId");
            java.util.Objects.requireNonNull(factoryId, "factoryId");
        }
    }

    record Snapshot(Identity identity, long revision, Instant generatedAt, Instant trustedUntil,
                    KeySet keySet, PolicySnapshot policy, List<Gate> gates, List<RevocationChange> revocations) {
        public Snapshot {
            gates = List.copyOf(gates);
            revocations = List.copyOf(revocations);
        }

        public Instant trustDeadline() {
            return trustedUntil.isBefore(keySet.trustedUntil()) ? trustedUntil : keySet.trustedUntil();
        }

        public boolean trustedAt(Instant instant) {
            return !instant.isBefore(generatedAt) && !instant.isBefore(keySet.generatedAt())
                    && instant.isBefore(trustDeadline());
        }
    }

    enum ApplyResult { APPLIED, FULL_REQUIRED }

    Optional<Snapshot> snapshot();
    ApplyResult apply(BootstrapResponse bundle);
    ApplyResult applyKeySet(KeySet keySet);
}
