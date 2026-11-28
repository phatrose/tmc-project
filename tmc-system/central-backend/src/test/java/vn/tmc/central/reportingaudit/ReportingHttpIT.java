package vn.tmc.central.reportingaudit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.tmc.central.reportingaudit.internal.controller.ReportingRequestFilter;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.controller.*;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.Receipt;
import vn.tmc.central.reportingaudit.api.ReportingSources.SessionQuery;
import vn.tmc.central.usermgmt.api.Role;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

class ReportingHttpIT extends ReportingIntegrationFixture {
    private MockMvc mvc;
    private SessionQuery sessions;
    @BeforeEach @SuppressWarnings("unchecked") void http(){
        sessions=mock(SessionQuery.class);when(sessions.getActorContext("Bearer fixture")).thenAnswer(call->hr);
        ObjectProvider<SessionQuery> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(sessions);
        var controller=new ReportAuditController(provider,reports,exports,auditQueries,access,payroll,dashboard);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ReportingErrorHandler())
                .setMessageConverters(new org.springframework.http.converter.ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(json.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)))
                .addFilters(new ReportingRequestFilter(json)).build();
    }
    @Test void attendanceAndDashboardUseV1ShapeAndTrustedContext()throws Exception{
        night();mvc.perform(get("/api/v1/reports/attendance").header("Authorization","Bearer fixture")
                .param("factoryId",FACTORY.toString()).param("from","2026-10-01").param("to","2026-11-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].workedMinutes").value(480)).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/reports/dashboard").header("Authorization","Bearer fixture")
                .param("factoryId",FACTORY.toString()).param("date","2026-10-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expectedCount").value(1)).andExpect(jsonPath("$.presentCount").value(1));
    }
    @Test void exportJobDownloadsBinaryAndExpires()throws Exception{
        night();var created=mvc.perform(post("/api/v1/reports/exports").header("Authorization","Bearer fixture")
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("factoryId",FACTORY,"period","2026-10","format","CSV")))).andExpect(status().isAccepted()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsByteArray()).get("id").asText();
        mvc.perform(get("/api/v1/reports/exports/"+id+"/file").header("Authorization","Bearer fixture"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        exportWorker.runOnce();mvc.perform(get("/api/v1/reports/exports/"+id+"/file").header("Authorization","Bearer fixture"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type","text/csv;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));
        clock.advance(Duration.ofHours(24));mvc.perform(get("/api/v1/reports/exports/"+id+"/file").header("Authorization","Bearer fixture"))
                .andExpect(status().isNotFound());
    }
    @Test void payrollCloseReplayHistoryAndReopenKeepCorrelationAndVersion()throws Exception{
        night();UUID key=UUID.randomUUID(),correlation=UUID.randomUUID();
        byte[] body=json.writeValueAsBytes(Map.of("factoryId",FACTORY,"reason","Chốt kỳ tháng 10"));
        var first=mvc.perform(post("/api/v1/payroll/periods/2026-10/close").header("Authorization","Bearer fixture")
                .header("Idempotency-Key",key).header("X-Request-Id",correlation).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(header().string("X-Request-Id",correlation.toString())).andReturn();
        mvc.perform(post("/api/v1/payroll/periods/2026-10/close").header("Authorization","Bearer fixture")
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(content().json(first.getResponse().getContentAsString()));
        String batch=json.readTree(first.getResponse().getContentAsByteArray()).get("id").asText();
        mvc.perform(get("/api/v1/payroll/batches/"+batch).header("Authorization","Bearer fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("QUEUED"));
        assertThat(db.jdbc.queryForObject("SELECT correlation_id FROM audit_logs WHERE action='PERIOD_CLOSE'",UUID.class)).isEqualTo(correlation);
        worker(p->new Receipt(p.request().batchId(),p.request().checksum(),1,1,List.of())).runOnce();
        mvc.perform(post("/api/v1/payroll/periods/2026-10/reopen").header("Authorization","Bearer fixture")
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("factoryId",FACTORY,"reason","Đối soát dữ liệu muộn","version",1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.state").value("REOPENED"));
        mvc.perform(get("/api/v1/payroll/periods/2026-10/history").header("Authorization","Bearer fixture").param("factoryId",FACTORY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void forgedHeadersMissingKeyUnknownFieldsAndWrongScopeAreRejected()throws Exception{
        mvc.perform(get("/api/v1/reports/attendance").header("X-User-Id",id(1)).header("X-Roles","HR")
                .param("factoryId",FACTORY.toString()).param("from","2026-10-01").param("to","2026-11-01"))
                .andExpect(status().isUnauthorized());
        byte[] body=json.writeValueAsBytes(Map.of("factoryId",FACTORY,"period","2026-10","format","CSV"));
        mvc.perform(post("/api/v1/reports/exports").header("Authorization","Bearer fixture").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reports/exports").header("Authorization","Bearer fixture").header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("factoryId",FACTORY,"period","2026-10","format","CSV","extra",true))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/dashboard").header("Authorization","Bearer fixture").param("factoryId",id(99).toString()).param("date","2026-10-31"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SCOPE_MISMATCH"));
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM report_exports",Long.class)).isZero();
    }
    @Test void auditorCannotCloseEvenWithPayrollPermission()throws Exception{
        hr=actor(id(1),null,List.of(Role.HR,Role.AUDITOR),List.of("payroll.export"),List.of(FACTORY),List.of(),"FACTORY");
        mvc.perform(post("/api/v1/payroll/periods/2026-10/close").header("Authorization","Bearer fixture").header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("factoryId",FACTORY,"reason","Chốt kỳ tháng 10"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
