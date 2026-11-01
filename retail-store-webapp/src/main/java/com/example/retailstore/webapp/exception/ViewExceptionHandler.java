package com.example.retailstore.webapp.exception;

import com.example.common.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(annotations = Controller.class)
public class ViewExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ViewExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ModelAndView handleValidationExceptions(MethodArgumentNotValidException ex) {
        return renderUiError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ModelAndView handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex) {
        return renderUiError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({HttpClientErrorException.class, HttpServerErrorException.class})
    public ModelAndView handleHttpException(Exception ex) {
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof HttpClientErrorException clientError) {
            status = clientError.getStatusCode();
        } else if (ex instanceof HttpServerErrorException serverError) {
            status = serverError.getStatusCode();
        }
        return renderUiError(ex.getMessage(), status);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ModelAndView handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return renderUiError(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ModelAndView handleInvalidRequestException(InvalidRequestException ex) {
        return renderUiError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFoundException(ResourceNotFoundException ex) {
        return renderUiError(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleSpringAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access Denied in View: {}", LogSanitizer.sanitizeException(ex));
        return renderUiError(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception ex) {
        log.error("Unexpected error occurred in View: {}", LogSanitizer.sanitizeException(ex));
        return renderUiError("An unexpected error occurred. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ModelAndView renderUiError(String message, HttpStatusCode status) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("message", message);
        return mav;
    }
}
