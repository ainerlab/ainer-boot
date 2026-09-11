package dev.ainer.web.error;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.UUID;

/**
 * 全局异常处理器：把异常映射为携带真实 HTTP 状态码的 {@link ApiResponse} 错误信封。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler}：Spring MVC 判定的整族框架异常
 * （405 方法不支持、406 不可接受、415 媒体类型不支持、非法请求体、参数绑定失败、
 * 上传超限等）都由框架给出权威状态码，本类只在
 * {@link #handleExceptionInternal} 这一收口点把状态码折算成稳定错误码并包成 Ainer 信封，
 * 因此这些 4xx 不再落进 {@link #handleUnexpected} 的 500 兜底，也不会打 error 级日志。
 *
 * <p>{@link BusinessException} 使用其错误码声明的状态码；参数校验类异常映射为 400；
 * 其余异常保留 Spring 判定的真实状态码（响应状态码原样透传，错误码是稳定契约的投影）。
 * 未知异常统一按 500 处理，只记录日志并返回稳定消息，不向客户端泄露堆栈或内部信息；
 * 所有响应都携带 requestId。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        return response(exception.errorCode(), exception.getMessage(), request);
    }

    /** 方法级 jakarta 约束校验；Spring 7 的父类只覆盖 {@code HandlerMethodValidationException}。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        return response(StandardErrorCode.INVALID_REQUEST, exception.getMessage(), request);
    }

    /** 数据绑定失败；Spring 7 的父类不再覆盖裸 {@link BindException}（只覆盖其子类）。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(
            BindException exception, HttpServletRequest request) {
        return response(StandardErrorCode.INVALID_REQUEST, fieldMessage(exception), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIds.currentOrCreate(request);
        LOGGER.error("Unhandled request failure, requestId={}", requestId, exception);
        return response(StandardErrorCode.INTERNAL_ERROR, StandardErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    /** 请求体校验失败：保留“字段: 原因”的可诊断消息，状态码与错误码仍由本类统一决定。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return envelope(StandardErrorCode.INVALID_REQUEST, fieldMessage(exception),
                headers, statusCode, request);
    }

    /**
     * 框架异常的单一收口点。
     *
     * <p>传输层状态码原样使用 Spring 判定的结果（例如 405/406/415/429/503），只用
     * {@link #standardCode} 折算稳定错误码，保证「HTTP status 始终真实」；父类传入的
     * 响应头（405 的 {@code Allow}、415 的 {@code Accept}）一并保留。父类的 ProblemDetail
     * body 不直接透出，只作为 4xx 的消息来源，最终响应一律是 {@link ApiResponse} 信封。
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        if (isResponseCommitted(request)) {
            LOGGER.warn("Response already committed, dropping error envelope for {}", exception.toString());
            return null;
        }

        ErrorCode errorCode = standardCode(statusCode);
        if (statusCode.is5xxServerError() && !(exception instanceof ErrorResponseException)) {
            // 真正的服务端失败（框架 5xx / 未知异常）记 error；携带显式状态码的
            // ErrorResponseException 是应用有意为之，保持与既有实现一致的静默处理。
            LOGGER.error("Framework request failure, requestId={}", requestId(request), exception);
        }
        return envelope(errorCode, messageOf(exception, body, errorCode, statusCode),
                headers, statusCode, request);
    }

    private ResponseEntity<ApiResponse<Void>> response(
            ErrorCode errorCode, @Nullable String message, HttpServletRequest request) {
        String requestId = RequestIds.currentOrCreate(request);
        ApiResponse<Void> body = ApiResponse.failure(errorCode, message, requestId);
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }

    private ResponseEntity<Object> envelope(
            ErrorCode errorCode, @Nullable String message,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        if (!acceptsJson(request)) {
            // 客户端 Accept 排除 JSON 时，内容协商会让错误信封写不出去（406 响应退化成空 body），
            // 显式声明响应类型，保证“真实状态码 + 稳定错误码”仍能一起到达客户端。
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        ApiResponse<Void> body = ApiResponse.failure(errorCode, message, requestId(request));
        return new ResponseEntity<>(body, responseHeaders, statusCode);
    }

    /** 4xx 使用框架给出的安全明细；5xx 一律使用稳定文案，不向客户端泄露内部信息。 */
    private @Nullable String messageOf(
            Exception exception, @Nullable Object body, ErrorCode errorCode, HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return errorCode.defaultMessage();
        }
        if (body instanceof ProblemDetail problemDetail && hasText(problemDetail.getDetail())) {
            return problemDetail.getDetail();
        }
        if (exception instanceof ErrorResponse errorResponse && hasText(errorResponse.getBody().getDetail())) {
            return errorResponse.getBody().getDetail();
        }
        return exception.getMessage();
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static String fieldMessage(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        return fieldError == null
                ? StandardErrorCode.INVALID_REQUEST.defaultMessage()
                : "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ErrorCode standardCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> StandardErrorCode.INVALID_REQUEST;
            case 401 -> StandardErrorCode.UNAUTHENTICATED;
            case 403 -> StandardErrorCode.FORBIDDEN;
            case 404 -> StandardErrorCode.NOT_FOUND;
            case 405 -> StandardErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> StandardErrorCode.NOT_ACCEPTABLE;
            case 409 -> StandardErrorCode.CONFLICT;
            case 415 -> StandardErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> StandardErrorCode.BUSINESS_RULE_VIOLATION;
            case 429 -> StandardErrorCode.RATE_LIMITED;
            case 503 -> StandardErrorCode.SERVICE_UNAVAILABLE;
            default -> statusCode.is4xxClientError()
                    ? StandardErrorCode.INVALID_REQUEST
                    : StandardErrorCode.INTERNAL_ERROR;
        };
    }

    private static boolean isResponseCommitted(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            return response != null && response.isCommitted();
        }
        return false;
    }

    private static boolean acceptsJson(WebRequest request) {
        if (!(request instanceof ServletWebRequest servletWebRequest)) {
            return true;
        }
        String accept = servletWebRequest.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            List<MediaType> accepted = MediaType.parseMediaTypes(accept);
            return accepted.isEmpty() || accepted.stream()
                    .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.APPLICATION_JSON));
        }
        catch (InvalidMediaTypeException invalidAccept) {
            // 畸形 Accept 交由 Spring 自身的内容协商判定，错误信封保持默认行为
            return true;
        }
    }

    private static String requestId(WebRequest request) {
        HttpServletRequest servletRequest = request instanceof ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest()
                : null;
        return servletRequest == null
                ? UUID.randomUUID().toString().replace("-", "")
                : RequestIds.currentOrCreate(servletRequest);
    }
}
