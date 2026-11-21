package com.jobtrack.config;

import com.jobtrack.common.api.ErrorResponse;
import com.jobtrack.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        if (exception.getStatus() >= 500) {
            log.error("业务异常 {} {}: status={}, code={}, message={}",
                    request.getMethod(), request.getRequestURI(), exception.getStatus(),
                    exception.getCode(), exception.getMessage(), exception);
        } else {
            // 参数、登录等正常业务错误按 WARN 记录，便于排查但不刷错误堆栈。
            log.warn("请求失败 {} {}: status={}, code={}, message={}",
                    request.getMethod(), request.getRequestURI(), exception.getStatus(),
                    exception.getCode(), exception.getMessage());
        }
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                          HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        log.warn("参数校验失败 {} {}: field={}, message={}",
                request.getMethod(), request.getRequestURI(),
                fieldError == null ? "-" : fieldError.getField(), message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(message, "validation_failed"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception,
                                                          HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数不正确");
        log.warn("参数校验失败 {} {}: message={}",
                request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(message, "validation_failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception,
                                                          HttpServletRequest request) {
        log.warn("请求体解析失败 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("请求体格式不正确", "invalid_body"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                HttpServletRequest request) {
        log.warn("缺少请求参数 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("缺少参数: " + exception.getParameterName(), "missing_parameter"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        log.warn("参数类型不匹配 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("参数格式不正确: " + exception.getName(), "invalid_parameter"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException exception,
                                                          HttpServletRequest request) {
        log.warn("上传文件过大 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("上传文件过大", "file_too_large"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception,
                                                      HttpServletRequest request) {
        log.warn("请求方法不支持 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("不支持的请求方法", "method_not_allowed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception,
                                                        HttpServletRequest request) {
        log.warn("资源不存在 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("请求的资源不存在", "not_found"));
    }

    /**
     * 客户端在响应写出阶段断开（取消下载、关闭 PDF 预览、网络中断等）。
     * 此时响应已提交，不能再写 JSON 错误体，否则会触发二次异常。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.debug("客户端已断开，忽略响应写入失败: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception exception, HttpServletRequest request,
                                              HttpServletResponse response) {
        if (response.isCommitted()) {
            // 响应已提交（如文件下载中途失败），无法再返回错误响应体。
            log.warn("响应已提交，无法返回错误响应 {} {}: {}", request.getMethod(),
                    request.getRequestURI(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.error("未处理的异常 {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("服务器开小差了，请稍后再试", "internal_error"));
    }
}
