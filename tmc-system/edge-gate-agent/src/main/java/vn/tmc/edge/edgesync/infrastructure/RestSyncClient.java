package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.domain.SyncPort;
import vn.tmc.edge.edgesync.domain.SyncProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Objects;

/** HTTPS sync using the provisioned device certificate and Central trust roots. */
public final class RestSyncClient implements SyncPort {
    private final URI endpoint;
    private final EdgeHttp http;

    public RestSyncClient(URI endpoint, SSLContext deviceTls) {
        this.endpoint = Objects.requireNonNull(endpoint);
        EdgeHttp.request(endpoint);
        http = new EdgeHttp(Objects.requireNonNull(deviceTls));
    }

    @Override
    public SyncBatchAck send(SyncBatchRequest batch) {
        byte[] body;
        try {
            body = WireJson.write(batch);
        } catch (IOException invalid) {
            throw new SyncProtocolException("Could not encode the sync request", invalid);
        }
        if (body.length > SyncBatchRequest.MAX_BODY_BYTES) {
            throw new SyncProtocolException("Sync request exceeds 1 MiB");
        }

        var response = http.send(EdgeHttp.request(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
        int status = response.statusCode();
        if (status != 200) {
            long retryAfter = 0;
            if (status == 429) {
                try {
                    retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElse("-1"));
                } catch (NumberFormatException invalid) {
                    retryAfter = -1;
                }
            }
            throw new SyncTransportException(status, retryAfter);
        }

        try {
            return WireJson.read(response.body(), SyncBatchAck.class);
        } catch (IOException invalid) {
            throw new SyncProtocolException("Malformed sync ACK", invalid);
        }
    }
}
