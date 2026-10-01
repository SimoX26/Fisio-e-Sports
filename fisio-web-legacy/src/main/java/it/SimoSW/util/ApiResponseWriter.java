package it.SimoSW.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.SimoSW.model.dto.ApiResponse;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class ApiResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApiResponseWriter() {
    }

    public static <T> void writeSuccess(HttpServletResponse response, int status, String message, T data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.ok(message, data));
    }

    public static void writeError(
            ServletContext servletContext,
            HttpServletResponse response,
            int status,
            String message,
            String errorCode,
            Throwable throwable
    ) throws IOException {
        if (servletContext != null && throwable != null) {
            servletContext.log(buildLogPrefix(errorCode, status) + " " + safeLogMessage(throwable.getMessage()), throwable);
        }

        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.error(
                message == null || message.isBlank() ? "Operazione non riuscita" : message,
                errorCode
        ));
    }

    private static String buildLogPrefix(String errorCode, int status) {
        String code = (errorCode == null || errorCode.isBlank()) ? "UNSPECIFIED_ERROR" : errorCode;
        return "[API_ERROR] code=" + code + " status=" + status;
    }

    private static String safeLogMessage(String message) {
        if (message == null || message.isBlank()) {
            return "N/A";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
