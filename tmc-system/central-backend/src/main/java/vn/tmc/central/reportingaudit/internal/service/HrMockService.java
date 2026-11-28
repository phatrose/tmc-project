package vn.tmc.central.reportingaudit.internal.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

public final class HrMockService {
    public enum Mode { SUCCESS, PARTIAL, TIMEOUT, COUNT_MISMATCH, CHECKSUM_MISMATCH }
    private final NamedParameterJdbcTemplate jdbc;private final TransactionTemplate transactions;
    private final ObjectMapper json;private final PayrollPayload codec;private final String credential;private final Supplier<Mode> mode;
    public HrMockService(NamedParameterJdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper json,
            PayrollPayload codec,String credential,Supplier<Mode> mode) {
        this.jdbc=jdbc;this.transactions=new TransactionTemplate(transactions);this.json=json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        this.json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
                .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
        this.codec=codec;this.credential=credential;this.mode=mode;
    }
    public Mode mode(){return mode.get();}
    public Receipt receive(String authorization,String idempotencyKey,byte[] body) {
        if(credential==null||credential.isBlank())throw new ReportingException("DEPENDENCY_UNAVAILABLE","HR service credential chưa cấu hình",503);
        if(authorization==null||!MessageDigest.isEqual(("Bearer "+credential).getBytes(StandardCharsets.UTF_8),authorization.getBytes(StandardCharsets.UTF_8)))
            throw new ReportingException("UNAUTHENTICATED","HR service credential không hợp lệ",401);
        Request request;String hash;
        try {
            request=json.readValue(body,Request.class);var bytes=new JsonCanonicalizer(json.readTree(body).toString()).getEncodedUTF8();
            hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException invalid){throw new ReportingException("MALFORMED_JSON","Payroll request không hợp lệ",400);}
        if(request.batchId()==null||request.factoryId()==null||request.batchId().version()!=4||request.factoryId().version()!=4
                ||request.batchId().variant()!=2||request.factoryId().variant()!=2
                ||!request.batchId().toString().equals(idempotencyKey)||request.periodVersion()<1)
            throw invalid();
        try {ReportExportService.validatePeriod(request.period());}catch(IllegalArgumentException malformed){throw invalid();}
        var keys=new HashSet<String>();
        for(var row:request.records()) if(row.employeeId()==null||row.shiftId()==null||row.workDate()==null
                ||row.employeeId().version()!=4||row.shiftId().version()!=4||row.employeeId().variant()!=2||row.shiftId().variant()!=2
                ||row.workedMinutes()<0||row.lateMinutes()<0||row.earlyMinutes()<0
                ||!row.workDate().toString().startsWith(request.period()+"-")||!keys.add(row.employeeId()+"/"+row.workDate()+"/"+row.shiftId()))throw invalid();
        var sorted=new ArrayList<>(request.records());sorted.sort(Comparator.comparing((Attendance row)->row.employeeId().toString())
                .thenComparing(Attendance::workDate).thenComparing(row->row.shiftId().toString()));
        if(!sorted.equals(request.records())||!Objects.equals(codec.checksum(request.records()),request.checksum()))throw invalid();
        return transactions.execute(status->{
            int count=request.records().size();var rejected=new ArrayList<Rejected>();
            if(mode.get()==Mode.PARTIAL&&count>0) {var row=request.records().getLast();rejected.add(new Rejected(row.employeeId(),row.workDate(),row.shiftId(),"INVALID_RECORD"));}
            int received=mode.get()==Mode.COUNT_MISMATCH?count+1:count;
            String checksum=mode.get()==Mode.CHECKSUM_MISMATCH?("0".repeat(64).equals(request.checksum())?"1":"0").repeat(64):request.checksum();
            var receipt=new Receipt(request.batchId(),checksum,received,received-rejected.size(),rejected);
            String value=encode(receipt);var p=Map.of("id",request.batchId(),"hash",hash,"receipt",value);
            jdbc.update("INSERT INTO hr_mock_batches VALUES(:id,:hash,CAST(:receipt AS jsonb)) ON CONFLICT DO NOTHING",p);
            var stored=jdbc.queryForMap("SELECT body_hash,receipt::text FROM hr_mock_batches WHERE id=:id FOR UPDATE",p);
            if(!hash.equals(stored.get("body_hash")))throw new ReportingException("IDEMPOTENCY_CONFLICT","batchId đã có nội dung khác",409);
            try{return json.readValue(stored.get("receipt").toString(),Receipt.class);}
            catch(java.io.IOException corrupt){throw new ReportingException("STORAGE_UNAVAILABLE","HR ACK không đọc được",503);}
        });
    }
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(java.io.IOException failure){throw new ReportingException("INTERNAL_ERROR","HR ACK không ghi được",500);}
    }
    private static ReportingException invalid(){return new ReportingException("VALIDATION_ERROR","Payroll metadata, records hoặc checksum không hợp lệ",400);}
}
