package vn.tmc.central.reportingaudit.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReportingRequestFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    public ReportingRequestFilter(ObjectMapper json){this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return !path.matches("/api/v1/(reports|audit|payroll)(/.*)?")&&!path.equals("/hr/v1/payroll/batches");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        String supplied=request.getHeader("X-Request-Id");
        if(supplied!=null&&!supplied.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            ReportingHttp.write(json,request,response,400,"VALIDATION_ERROR","X-Request-Id phải là UUIDv4 chữ thường",false);return;
        }
        String id=supplied==null?response.getHeader("X-Request-Id"):supplied;
        if(id==null)id=UUID.randomUUID().toString();
        request.setAttribute(ReportingHttp.REQUEST_ID,id);response.setHeader("X-Request-Id",id);
        chain.doFilter(request,response);
    }
}
