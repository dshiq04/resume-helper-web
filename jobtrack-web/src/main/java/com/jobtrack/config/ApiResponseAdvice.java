package com.jobtrack.config;

import com.jobtrack.common.api.Result;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Locale;

/**
 * 自动把所有 JSON 接口的成功返回值包装为 {@link Result}。
 *
 * <p>文件下载、头像、模板等二进制响应不属于 JSON 接口，保持原样返回；
 * 异常响应由 {@code GlobalExceptionHandler} 直接构造 Result，不会重复包装。</p>
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null || body instanceof Result) {
            return body;
        }
        if (!isJson(selectedContentType)) {
            return body;
        }
        if (body instanceof byte[] || body instanceof Resource || body instanceof StreamingResponseBody) {
            return body;
        }
        Integer status = response.getStatusCode() == null ? null : response.getStatusCode().value();
        if (status == null || status < 200 || status >= 300) {
            return body;
        }
        return Result.success(body);
    }

    private boolean isJson(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        String subtype = mediaType.getSubtype();
        if (subtype != null && subtype.toLowerCase(Locale.ROOT).contains("json")) {
            return true;
        }
        return MediaType.APPLICATION_JSON.includes(mediaType);
    }
}
