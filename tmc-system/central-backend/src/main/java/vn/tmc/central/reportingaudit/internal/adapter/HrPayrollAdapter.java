package vn.tmc.central.reportingaudit.internal.adapter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.Receipt;
import vn.tmc.central.reportingaudit.internal.service.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public final class HrPayrollAdapter implements HrPayrollPort {
    private final HttpClient http;
    private final URI endpoint;
    private final String credential;
    private final Duration timeout;
    private final ObjectMapper json;

    public HrPayrollAdapter(HttpClient http,URI origin,String credential,Duration timeout,ObjectMapper json) {
        if(http.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("HR client must not follow redirects");
        if(origin!=null&&(!"https".equalsIgnoreCase(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null
                ||origin.getQuery()!=null||origin.getFragment()!=null||!(origin.getPath().isEmpty()||origin.getPath().equals("/"))))
            throw new IllegalArgumentException("HR origin must be HTTPS without credentials, path, query or fragment");
        if(timeout==null||timeout.isNegative()||timeout.isZero())throw new IllegalArgumentException("Positive HR timeout required");
        this.http=http;this.endpoint=origin==null?null:origin.resolve("/hr/v1/payroll/batches");this.credential=credential;this.timeout=timeout;
        this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.json.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String,CoercionAction.Fail);
    }

    @Override public Receipt send(PayrollPayload.Prepared payload) {
        if(endpoint==null||credential==null||credential.isBlank())throw new HrDeliveryException("DEPENDENCY_UNAVAILABLE",true,"HR service chưa được cấu hình");
        var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json; charset=UTF-8")
                .header("Accept","application/json").header("Authorization","Bearer "+credential)
                .header("X-Request-Id",UUID.randomUUID().toString()).header("Idempotency-Key",payload.request().batchId().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.bytes())).build();
        try {
            var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());int status=response.statusCode();
            if(status==429||status>=500)throw new HrDeliveryException("DEPENDENCY_UNAVAILABLE",true,"HR tạm thời không nhận được batch");
            if(status==409)throw new HrDeliveryException("IDEMPOTENCY_CONFLICT",false,"HR báo xung đột nội dung batch");
            if(status==401||status==403)throw new HrDeliveryException("UNAUTHENTICATED",false,"HR từ chối service credential");
            if(status!=200)throw new HrDeliveryException("VALIDATION_ERROR",false,"HR trả trạng thái không hợp lệ");
            try{return json.readValue(response.body(),Receipt.class);}
            catch(java.io.IOException invalid){throw new HrDeliveryException("VALIDATION_ERROR",false,"ACK HR không đúng định dạng");}
        }catch(java.io.IOException unavailable){throw new HrDeliveryException("DEPENDENCY_UNAVAILABLE",true,"Mất kết nối hoặc quá hạn chờ HR");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new HrDeliveryException("DEPENDENCY_UNAVAILABLE",true,"Lần gửi HR bị gián đoạn");}
    }
}
