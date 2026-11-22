package vn.tmc.central.reportingaudit.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.tmc.contracts.ApiError;
import java.io.IOException;
import java.util.UUID;

public final class ReportingHttp {
    static final String REQUEST_ID=ReportingHttp.class.getName()+".requestId";
    private ReportingHttp() {}
    public static String requestId(HttpServletRequest request) {
        var value=request.getAttribute(REQUEST_ID);
        if(value!=null)return value.toString();
        String id=UUID.randomUUID().toString();request.setAttribute(REQUEST_ID,id);return id;
    }
    public static void write(ObjectMapper json,HttpServletRequest request,HttpServletResponse response,
            int status,String code,String message,boolean retryable)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id",requestId(request));response.setHeader("Cache-Control","no-store");
        json.writeValue(response.getOutputStream(),new ApiError(code,message,requestId(request),retryable));
    }
}
