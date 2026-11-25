package com.jobtrack.config;

import com.jobtrack.common.api.Result;
import com.jobtrack.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

/**
 * 全局异常处理：所有错误都转换为统一的 {@link Result} 响应体，
 * HTTP 状态码与 Result.code 保持一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException exception, HttpServletRequest request,
                                       HttpServletResponse response) {
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
        response.setStatus(exception.getStatus());
        return Result.error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException exception,
                                         HttpServletRequest request, HttpServletResponse response) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage();
        log.warn("参数校验失败 {} {}: field={}, message={}",
                request.getMethod(), request.getRequestURI(),
                fieldError == null ? "-" : fieldError.getField(), message);
        return badRequest(response, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException exception,
                                         HttpServletRequest request, HttpServletResponse response) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数不正确");
        log.warn("参数校验失败 {} {}: message={}",
                request.getMethod(), request.getRequestURI(), message);
        return badRequest(response, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException exception,
                                         HttpServletRequest request, HttpServletResponse response) {
        log.warn("请求体解析失败 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        return badRequest(response, "请求体格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException exception,
                                               HttpServletRequest request, HttpServletResponse response) {
        log.warn("缺少请求参数 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getParameterName());
        return badRequest(response, "缺少参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                           HttpServletRequest request, HttpServletResponse response) {
        log.warn("参数类型不匹配 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getName());
        return badRequest(response, "参数格式不正确: " + exception.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException exception,
                                         HttpServletRequest request, HttpServletResponse response) {
        log.warn("上传文件过大 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        return Result.error(HttpStatus.PAYLOAD_TOO_LARGE.value(), "上传文件过大");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException exception,
                                     HttpServletRequest request, HttpServletResponse response) {
        log.warn("请求方法不支持 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持的请求方法");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException exception,
                                       HttpServletRequest request, HttpServletResponse response) {
        log.warn("资源不存在 {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getMessage());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return Result.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在");
    }

    /**
     * 客户端在响应写出阶段断开（取消下载、关闭 PDF 预览、网络中断等）。
     * 此时响应已提交，不能再写 JSON 错误体，否则会触发二次异常。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.debug("客户端已断开，忽略响应写入失败: {}", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception exception, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (response.isCommitted()) {
            // 响应已提交（如文件下载中途失败），无法再返回错误响应体。
            log.warn("响应已提交，无法返回错误响应 {} {}: {}", request.getMethod(),
                    request.getRequestURI(), exception.getMessage());
            return null;
        }
        log.error("未处理的异常 {} {}", request.getMethod(), request.getRequestURI(), exception);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器开小差了，请稍后再试");
    }

    private Result<Void> badRequest(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return Result.error(HttpStatus.BAD_REQUEST.value(), message);
    }
}
