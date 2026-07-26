package dev.ainer.web.error;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        return response(exception.errorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBinding(Exception exception, HttpServletRequest request) {
        FieldError fieldError = exception instanceof MethodArgumentNotValidException methodArgument
                ? methodArgument.getBindingResult().getFieldError()
                : ((BindException) exception).getBindingResult().getFieldError();
        String message = fieldError == null ? StandardErrorCode.INVALID_REQUEST.defaultMessage()
                : "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
        return response(StandardErrorCode.INVALID_REQUEST, message, request);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return response(StandardErrorCode.INVALID_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(StandardErrorCode.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleFrameworkError(
            ErrorResponseException exception, HttpServletRequest request) {
        ErrorCode errorCode = standardCode(exception.getStatusCode());
        String message = errorCode == StandardErrorCode.INTERNAL_ERROR
                ? StandardErrorCode.INTERNAL_ERROR.defaultMessage()
                : exception.getBody().getDetail();
        return response(errorCode, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIds.currentOrCreate(request);
        LOGGER.error("Unhandled request failure, requestId={}", requestId, exception);
        return response(StandardErrorCode.INTERNAL_ERROR, StandardErrorCode.INTERNAL_ERROR.defaultMessage(), request);
    }

    private ResponseEntity<ApiResponse<Void>> response(
            ErrorCode errorCode, String message, HttpServletRequest request) {
        String requestId = RequestIds.currentOrCreate(request);
        ApiResponse<Void> body = ApiResponse.failure(errorCode, message, requestId);
        return ResponseEntity.status(errorCode.httpStatus()).body(body);
    }

    private ErrorCode standardCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> StandardErrorCode.INVALID_REQUEST;
            case 401 -> StandardErrorCode.UNAUTHENTICATED;
            case 403 -> StandardErrorCode.FORBIDDEN;
            case 404 -> StandardErrorCode.NOT_FOUND;
            case 409 -> StandardErrorCode.CONFLICT;
            case 422 -> StandardErrorCode.BUSINESS_RULE_VIOLATION;
            default -> statusCode.is4xxClientError()
                    ? StandardErrorCode.INVALID_REQUEST
                    : StandardErrorCode.INTERNAL_ERROR;
        };
    }
}
