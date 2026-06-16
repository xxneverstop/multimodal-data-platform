package com.honortech.dataplatform.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.honortech.dataplatform.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException exception) {
        log.warn("Unauthorized exception", exception);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(exception.getMessage(), null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException exception) {
        log.warn("Forbidden exception", exception);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(exception.getMessage(), null));
    }

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException exception) {
        log.warn("Business exception", exception);
        return ApiResponse.failure(exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        log.warn("Request body validation exception", exception);
        return ApiResponse.failure("Invalid request: " + extractFieldValidationMessage(exception.getBindingResult().getFieldErrors()), null);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException exception) {
        log.warn("Binding validation exception", exception);
        return ApiResponse.failure("Invalid request: " + extractFieldValidationMessage(exception.getBindingResult().getFieldErrors()), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        log.warn("Constraint violation exception", exception);
        return ApiResponse.failure("Invalid request: " + extractConstraintViolationMessage(exception), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("Request body parse exception", exception);
        return ApiResponse.failure("Invalid request: " + extractReadableMessage(exception), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        log.warn("Method argument type mismatch exception", exception);
        String expectedType = exception.getRequiredType() == null ? "required type" : exception.getRequiredType().getSimpleName();
        return ApiResponse.failure(
                "Invalid request: parameter '" + exception.getName() + "' has invalid format, expected " + expectedType,
                null
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException exception) {
        log.warn("Missing request parameter exception", exception);
        return ApiResponse.failure(
                "Invalid request: missing request parameter '" + exception.getParameterName() + "'",
                null
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ApiResponse<Void> handleMissingServletRequestPartException(MissingServletRequestPartException exception) {
        log.warn("Missing request part exception", exception);
        return ApiResponse.failure(
                "Invalid request: missing file part '" + exception.getRequestPartName() + "'",
                null
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        log.warn("Max upload size exceeded exception", exception);
        return ApiResponse.failure("Upload failed: file size exceeds limit", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        log.warn("Request method not supported exception", exception);
        return ApiResponse.failure(
                "Request failed: HTTP method '" + exception.getMethod() + "' is not supported for this endpoint",
                null
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResponse<Void> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException exception) {
        log.warn("Media type not supported exception", exception);
        String mediaType = exception.getContentType() == null ? "unknown" : exception.getContentType().toString();
        return ApiResponse.failure(
                "Request failed: content type '" + mediaType + "' is not supported",
                null
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccessException(DataAccessException exception) {
        log.error("Database access exception", exception);
        return ApiResponse.failure("Database error: " + rootCauseMessage(exception), null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ApiResponse.failure("Internal server error: " + rootCauseMessage(exception), null);
    }

    private String extractFieldValidationMessage(Iterable<FieldError> fieldErrors) {
        Iterator<FieldError> iterator = fieldErrors.iterator();
        if (!iterator.hasNext()) {
            return "request validation failed";
        }
        FieldError firstError = iterator.next();
        String field = firstError.getField();
        String message = firstError.getDefaultMessage();
        if (!StringUtils.hasText(message)) {
            return field + " is invalid";
        }
        return field + " " + message;
    }

    private String extractConstraintViolationMessage(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> extractLeafPathName(violation.getPropertyPath()) + " " + violation.getMessage())
                .orElse("request validation failed");
    }

    private String extractLeafPathName(Path path) {
        String leaf = "parameter";
        for (Path.Node node : path) {
            if (StringUtils.hasText(node.getName())) {
                leaf = node.getName();
            }
        }
        return leaf;
    }

    private String extractReadableMessage(HttpMessageNotReadableException exception) {
        Throwable rootCause = findRootCause(exception);
        if (rootCause instanceof InvalidFormatException invalidFormatException) {
            String fieldPath = invalidFormatException.getPath().stream()
                    .map(reference -> reference.getFieldName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            String targetType = invalidFormatException.getTargetType() == null
                    ? "required type"
                    : invalidFormatException.getTargetType().getSimpleName();
            if (!StringUtils.hasText(fieldPath)) {
                return "request body contains invalid value, expected " + targetType;
            }
            if (invalidFormatException.getTargetType() == LocalDate.class) {
                return "field '" + fieldPath + "' must be a valid date in format yyyy-MM-dd";
            }
            return "field '" + fieldPath + "' has invalid value, expected " + targetType;
        }
        if (rootCause instanceof MismatchedInputException mismatchedInputException) {
            String fieldPath = mismatchedInputException.getPath().stream()
                    .map(reference -> reference.getFieldName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (StringUtils.hasText(fieldPath)) {
                return "field '" + fieldPath + "' is missing or has invalid structure";
            }
            return "request body has invalid structure";
        }
        String message = rootCauseMessage(exception);
        return StringUtils.hasText(message) ? "JSON parse error: " + message : "request body has invalid JSON format";
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = findRootCause(throwable);
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message;
    }

    private Throwable findRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
