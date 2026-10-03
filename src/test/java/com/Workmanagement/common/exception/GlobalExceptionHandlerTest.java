package com.Workmanagement.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test-endpoint");
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException and return 404 NOT_FOUND")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Task not found with id: 42");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("NOT_FOUND", response.getBody().error());
        assertEquals("Task not found with id: 42", response.getBody().message());
        assertEquals("/api/test-endpoint", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    @DisplayName("Should handle BadRequestException and return 400 BAD_REQUEST")
    void testHandleBadRequestException() {
        BadRequestException ex = new BadRequestException("Invalid payload");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleBadRequestException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("BAD_REQUEST", response.getBody().error());
        assertEquals("Invalid payload", response.getBody().message());
        assertEquals("/api/test-endpoint", response.getBody().path());
    }

    @Test
    @DisplayName("Should handle UnauthorizedException and return 401 UNAUTHORIZED")
    void testHandleUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("User is not authenticated");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleUnauthorizedException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("UNAUTHORIZED", response.getBody().error());
        assertEquals("User is not authenticated", response.getBody().message());
        assertEquals("/api/test-endpoint", response.getBody().path());
    }

    @Test
    @DisplayName("Should handle AuthenticationException and return 401 UNAUTHORIZED")
    void testHandleAuthenticationException() {
        BadCredentialsException ex = new BadCredentialsException("Invalid username or password");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().status());
        assertEquals("UNAUTHORIZED", response.getBody().error());
        assertEquals("Invalid username or password", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle ForbiddenException and return 403 FORBIDDEN")
    void testHandleForbiddenException() {
        ForbiddenException ex = new ForbiddenException("Access is forbidden");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleForbiddenException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("FORBIDDEN", response.getBody().error());
        assertEquals("Access is forbidden", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle AccessDeniedException and return 403 FORBIDDEN")
    void testHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("You are not authorized to view this project");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("FORBIDDEN", response.getBody().error());
        assertEquals("You are not authorized to view this project", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle ConflictException and return 409 CONFLICT")
    void testHandleConflictException() {
        ConflictException ex = new ConflictException("Project with name already exists");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleConflictException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("CONFLICT", response.getBody().error());
        assertEquals("Project with name already exists", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException and return 409 CONFLICT with safe message")
    void testHandleDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint violation in SQL table user_tbl");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("CONFLICT", response.getBody().error());
        assertEquals("Database constraint violation or conflicting data record", response.getBody().message());
        assertFalse(response.getBody().message().contains("SQL"));
        assertFalse(response.getBody().message().contains("user_tbl"));
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with field errors")
    void testHandleMethodArgumentNotValidException() throws Exception {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("userDto", "email", "must be a valid email");
        FieldError fieldError2 = new FieldError("userDto", "password", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        Method method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationExceptions(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("VALIDATION_ERROR", response.getBody().error());
        assertNotNull(response.getBody().validationErrors());
        assertEquals("must be a valid email", response.getBody().validationErrors().get("email"));
        assertEquals("must not be blank", response.getBody().validationErrors().get("password"));
    }

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException and return 400 BAD_REQUEST")
    void testHandleHttpMessageNotReadableException() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleHttpMessageNotReadable(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("MALFORMED_REQUEST", response.getBody().error());
    }

    @Test
    @DisplayName("Should handle MethodArgumentTypeMismatchException and return 400 BAD_REQUEST")
    void testHandleMethodArgumentTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc", Long.class, "taskId", null, null);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMethodArgumentTypeMismatch(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("INVALID_PARAMETER", response.getBody().error());
        assertTrue(response.getBody().message().contains("taskId"));
        assertTrue(response.getBody().message().contains("Long"));
    }

    @Test
    @DisplayName("Should handle MissingServletRequestParameterException and return 400 BAD_REQUEST")
    void testHandleMissingServletRequestParameter() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("status", "String");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMissingServletRequestParameter(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("MISSING_PARAMETER", response.getBody().error());
        assertTrue(response.getBody().message().contains("status"));
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500 without leaking stack traces or internal details")
    void testHandleGenericException() {
        NullPointerException ex = new NullPointerException("Secret connection string or internal null reference");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().error());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertFalse(response.getBody().message().contains("Secret"));
        assertFalse(response.getBody().message().contains("NullPointerException"));
    }
}
