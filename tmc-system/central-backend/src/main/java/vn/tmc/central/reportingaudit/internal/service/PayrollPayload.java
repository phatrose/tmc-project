package vn.tmc.central.reportingaudit.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;
import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendanceSnapshot;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import java.security.MessageDigest;
import java.util.*;

public final class PayrollPayload {
    public record Prepared(Request request,byte[] bytes) {
        public Prepared {bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    private final ObjectMapper json;
    public PayrollPayload(ObjectMapper json){this.json=json;}

    public Prepared prepare(UUID batchId,PayrollAttendanceSnapshot snapshot) {
        var records=new ArrayList<Attendance>();var keys=new HashSet<String>();
        for(var row:snapshot.records()) {
            if(row.factoryId()==null||row.workDate()==null||!row.factoryId().equals(snapshot.factoryId())||!java.time.YearMonth.from(row.workDate()).equals(snapshot.period()))
                throw ReportingException.unavailable("snapshot sai factory/kỳ");
            if(row.workedMinutes()==null)continue;
            if(row.employeeId()==null||row.shiftId()==null||row.shiftId().version()!=4||row.shiftId().variant()!=2
                    ||row.employeeId().version()!=4||row.employeeId().variant()!=2||row.workedMinutes()<0
                    ||row.lateMinutes()<0||row.earlyMinutes()<0)
                throw ReportingException.unavailable("dòng Payroll có dữ liệu không hợp lệ");
            if(!keys.add(row.employeeId()+"/"+row.workDate()+"/"+row.shiftId()))
                throw ReportingException.unavailable("dòng Payroll trùng khóa");
            records.add(new Attendance(row.employeeId(),row.workDate(),row.shiftId(),row.workedMinutes(),row.lateMinutes(),row.earlyMinutes()));
        }
        records.sort(Comparator.comparing((Attendance row)->row.employeeId().toString()).thenComparing(Attendance::workDate)
                .thenComparing(row->row.shiftId().toString()));
        var request=new Request(batchId,snapshot.factoryId(),snapshot.period().toString(),snapshot.periodVersion(),checksum(records),records);
        try{return new Prepared(request,json.writeValueAsBytes(request));}
        catch(java.io.IOException failure){throw new ReportingException("INTERNAL_ERROR","Không tạo được payload Payroll",500);}
    }

    public String checksum(List<Attendance> records) {
        try {
            byte[] canonical=new JsonCanonicalizer(json.writeValueAsString(records)).getEncodedUTF8();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException failure){
            throw new ReportingException("INTERNAL_ERROR","Không tính được checksum Payroll",500);
        }
    }
    public Request read(byte[] bytes) {
        try{return json.readValue(bytes,Request.class);}
        catch(java.io.IOException failure){throw new ReportingException("STORAGE_UNAVAILABLE","Payload Payroll đã lưu không đọc được",503);}
    }
}
