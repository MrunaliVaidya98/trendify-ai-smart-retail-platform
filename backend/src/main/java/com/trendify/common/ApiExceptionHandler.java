package com.trendify.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  public record ErrorBody(Instant timestamp,int status,String error,String message,String path,String correlationId,Map<String,String> fields) {}

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex,HttpServletRequest request) {
    Map<String,String> fields = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST,"Validation failed","Request is invalid",fields,request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorBody> constraint(ConstraintViolationException ex,HttpServletRequest request) {
    Map<String,String> fields=new LinkedHashMap<>();
    ex.getConstraintViolations().forEach(v->fields.put(v.getPropertyPath().toString(),v.getMessage()));
    return response(HttpStatus.BAD_REQUEST,"Validation failed","Request is invalid",fields,request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorBody> bad(IllegalArgumentException ex,HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST,"Bad request",safeMessage(ex,"Request could not be processed"),Map.of(),request);
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ErrorBody> missing(NoSuchElementException ex,HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND,"Not found",safeMessage(ex,"Resource not found"),Map.of(),request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorBody> denied(AccessDeniedException ex,HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN,"Forbidden","Access is denied",Map.of(),request);
  }

  private ResponseEntity<ErrorBody> response(HttpStatus status,String error,String message,Map<String,String> fields,HttpServletRequest request) {
    Object correlation=request.getAttribute(CorrelationFilter.REQUEST_ATTRIBUTE);
    String correlationId=correlation==null?request.getHeader(CorrelationFilter.HEADER):correlation.toString();
    return ResponseEntity.status(status).body(new ErrorBody(Instant.now(),status.value(),error,message,request.getRequestURI(),correlationId,fields));
  }

  private String safeMessage(RuntimeException ex,String fallback) {
    return ex.getMessage()==null||ex.getMessage().isBlank()?fallback:ex.getMessage();
  }
}
