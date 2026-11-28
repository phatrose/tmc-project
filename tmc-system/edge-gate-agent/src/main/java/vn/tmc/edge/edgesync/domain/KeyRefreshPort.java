package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.BootstrapResponse.KeySet;

/** Dedicated HTTPS signing-key refresh boundary (GET /api/v1/qr/keys/refresh). */
@FunctionalInterface
public interface KeyRefreshPort {
    KeySet fetch();
}
