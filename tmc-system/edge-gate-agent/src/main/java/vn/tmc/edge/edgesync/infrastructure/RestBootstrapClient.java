package vn.tmc.edge.edgesync.infrastructure;

import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.edge.edgesync.domain.BootstrapPort;
import vn.tmc.edge.edgesync.domain.BootstrapProtocolException;
import vn.tmc.edge.edgesync.domain.SyncTransportException;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Function;

public final class RestBootstrapClient implements BootstrapPort {
    private final URI endpoint;
    private final Function<HttpRequest, HttpResponse<byte[]>> sender;

    public RestBootstrapClient(URI endpoint, SSLContext deviceTls) {
        this(endpoint, new EdgeHttp(Objects.requireNonNull(deviceTls))::send);
    }

    RestBootstrapClient(URI endpoint, Function<HttpRequest, HttpResponse<byte[]>> sender) {
        this.endpoint = Objects.requireNonNull(endpoint);
        EdgeHttp.request(endpoint);
        if (endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Bootstrap endpoint must omit query and fragment");
        }
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public FetchResult fetch(long afterRevision) {
        if (afterRevision < 0) {
            throw new IllegalArgumentException("Invalid bootstrap revision");
        }
        var url = URI.create(endpoint.toASCIIString() + "?afterRevision=" + afterRevision);
        var response = sender.apply(EdgeHttp.request(url).GET().build());
        if (response.statusCode() == 304) {
            try {
                long revision = Long.parseLong(response.headers().firstValue("X-Revision").orElse(""));
                if (response.body().length != 0) {
                    throw new BootstrapProtocolException("304 must omit the bootstrap body");
                }
                return new FetchResult(true, revision, null);
            } catch (NumberFormatException invalid) {
                throw new BootstrapProtocolException("Invalid X-Revision header", invalid);
            }
        }
        if (response.statusCode() != 200) {
            throw new SyncTransportException(response.statusCode(), 0);
        }
        try {
            var bundle = WireJson.readResponse(response.body(), BootstrapResponse.class);
            if (bundle == null) {
                throw new BootstrapProtocolException("Bootstrap body is missing");
            }
            return new FetchResult(false, bundle.revision(), bundle);
        } catch (IOException invalid) {
            throw new BootstrapProtocolException("Malformed bootstrap response", invalid);
        }
    }
}
