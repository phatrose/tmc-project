package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.BootstrapResponse;

/** HTTPS bootstrap boundary. A 304 reports its revision without a bundle. */
@FunctionalInterface
public interface BootstrapPort {
    record FetchResult(boolean notModified, long revision, BootstrapResponse bundle) {
        public FetchResult {
            if (revision <= 0 || (notModified ? bundle != null : bundle == null || bundle.revision() != revision)) {
                throw new BootstrapProtocolException("Invalid bootstrap response metadata");
            }
        }
    }

    FetchResult fetch(long afterRevision);
}
