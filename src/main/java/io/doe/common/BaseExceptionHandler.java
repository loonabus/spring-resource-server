package io.doe.common;

import io.doe.domain.BaseRes;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.transaction.TransactionException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.WebUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
 * @since 2024-07-08
 */

@Slf4j
@RestControllerAdvice
public class BaseExceptionHandler {

	private static final String TYPE_MISMATCH_CODE = "TypeMismatch";
	private static final String BAD_REQUEST_MESSAGE = "Invalid Parameters Found";

	private final MessageSourceAccessor accessor;

	@Autowired
	public BaseExceptionHandler(final MessageSourceAccessor accessor) {
		this.accessor = accessor;
	}

	@Nullable
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler({BindException.class,
			ConstraintViolationException.class, HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
			ServletRequestBindingException.class, TypeMismatchException.class})
	public BaseRes<Void> processBadRequestError(final Exception e, final WebRequest wr) {

		switch (e) {
			case BindException ee -> { return processBindException(ee, wr); }
			case ConstraintViolationException ee -> { return processConstraintViolationException(ee, wr); }
			case HttpMessageNotReadableException ee -> { return processHttpMessageNotReadableException(ee, wr); }
			case MissingServletRequestParameterException ee -> { return processMissingServletRequestParameterException(ee, wr); }
			case MissingServletRequestPartException ee -> { return processMissingServletRequestPartException(ee, wr); }
			case ServletRequestBindingException ee -> { return processServletRequestBindingException(ee, wr); }
			case TypeMismatchException ee -> { return processTypeMismatchException(ee, wr); }
			default -> { return response(e, wr, BaseRes.from(HttpStatus.BAD_REQUEST.getReasonPhrase()), HttpStatus.BAD_REQUEST); }
		}
	}

	@Nullable
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler({HandlerMethodValidationException.class, MissingPathVariableException.class})
	public BaseRes<Void> processOtherBadRequestError(final Exception e, final WebRequest wr) {

		if (e instanceof MissingPathVariableException ee) {
			return response(e, wr, BaseRes.from("URI path variable " + ee.getVariableName() + " is not present"), HttpStatus.BAD_REQUEST);
		}

		return response(e, wr, BaseRes.from(HttpStatus.BAD_REQUEST.getReasonPhrase()), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ExceptionHandler(BadJwtException.class)
	public BaseRes<Void> processUnauthorizedError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.UNAUTHORIZED);
	}

	@Nullable
	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	public BaseRes<Void> processNotFoundError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.NOT_FOUND);
	}

	@Nullable
	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public BaseRes<Void> processMethodNotAllowedError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.METHOD_NOT_ALLOWED);
	}

	@Nullable
	@ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public BaseRes<Void> processNotAcceptableError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.NOT_ACCEPTABLE);
	}

	@Nullable
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public BaseRes<Void> processPayloadTooLargeError(final Exception e, final WebRequest wr) {
		final String message = "Maximum upload size exceeded (" + ((MaxUploadSizeExceededException)e).getMaxUploadSize() + ")";
		return response(e, wr, BaseRes.from(message), HttpStatus.PAYLOAD_TOO_LARGE);
	}

	@Nullable
	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public BaseRes<Void> processUnsupportedMediaTypeError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Nullable
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler({ConversionNotSupportedException.class, HttpMessageNotWritableException.class, IllegalArgumentException.class, MethodValidationException.class})
	public BaseRes<Void> processInternalServerError(final Exception e, final WebRequest wr) {

		switch (e) {
			case ConversionNotSupportedException ee -> { return processConversionNotSupportedException(ee, wr); }
			case HttpMessageNotWritableException ee -> { return processHttpMessageNotWritableException(ee, wr); }
			case IllegalArgumentException ee -> { return processIllegalArgumentException(ee, wr); }
			case MethodValidationException ee -> { return processMethodValidationException(ee, wr); }
			default -> { return response(e, wr, BaseRes.from(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()), HttpStatus.INTERNAL_SERVER_ERROR); }
		}
	}

	@Nullable
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(BaseException.class)
	public BaseRes<Void> processUserDefinedError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler({DataAccessException.class, TransactionException.class})
	public BaseRes<Void> processDatabaseError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	@ExceptionHandler(AsyncRequestTimeoutException.class)
	public BaseRes<Void> processServiceUnavailableError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from("Asynchronous request time out"), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	public BaseRes<Void> processRemainderError(final Exception e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	private BaseRes<Void> processBindException(final BindException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(createBindExceptionMessage(e)), HttpStatus.BAD_REQUEST);
	}

	private String createBindExceptionMessage(final BindException e) {

		if (e.getBindingResult().hasErrors()) {
			final ObjectError oe = e.getBindingResult().getAllErrors().getFirst();
			final Optional<String> dm = Optional.ofNullable(oe.getDefaultMessage());
			final String[] codes = Optional.ofNullable(oe.getCodes()).orElse(new String[]{});

			return Arrays.stream(codes).map(c -> accessor.getMessage(c, oe.getArguments(), "")).filter(StringUtils::hasText).findFirst()
					.orElseGet(() -> (oe instanceof FieldError fe) ? retrieveMessageFromFieldError(fe, dm) : dm.orElse(BAD_REQUEST_MESSAGE));
		}

		return BAD_REQUEST_MESSAGE;
	}

	private String retrieveMessageFromFieldError(final FieldError error, final Optional<String> message) {

		if (TYPE_MISMATCH_CODE.equalsIgnoreCase(error.getCode())) {
			return retrieveValidationErrorMessage(TYPE_MISMATCH_CODE, error.getField(), String.valueOf(error.getRejectedValue()));
		}

		return message.map(m -> error.getField() + ": " + m).orElse(BAD_REQUEST_MESSAGE);
	}

	@Nullable
	private BaseRes<Void> processConstraintViolationException(final ConstraintViolationException e, final WebRequest wr) {

		final String message = CollectionUtils.isEmpty(e.getConstraintViolations()) ?
				BAD_REQUEST_MESSAGE : e.getConstraintViolations().stream().findFirst().map(v -> v.getPropertyPath() + " " + v.getMessage()).orElse(BAD_REQUEST_MESSAGE);
		return response(e, wr, BaseRes.from(message), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processHttpMessageNotReadableException(final HttpMessageNotReadableException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from("Failed to read request"), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processMissingServletRequestParameterException(final MissingServletRequestParameterException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(retrieveValidationErrorMessage(e.getClass().getSimpleName(), e.getParameterType(), e.getParameterName())), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processMissingServletRequestPartException(final MissingServletRequestPartException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(retrieveValidationErrorMessage(e.getClass().getSimpleName(), e.getRequestPartName())), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processServletRequestBindingException(final ServletRequestBindingException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from("Unrecoverable fatal binding exception occurred"), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processTypeMismatchException(final TypeMismatchException e, final WebRequest wr) {

		final String message = retrieveValidationErrorMessage(e.getClass().getSimpleName(),
				e.getRequiredType(), (e instanceof MethodArgumentTypeMismatchException ee) ? ee.getName() : e.getPropertyName(), e.getValue());
		return response(e, wr, BaseRes.from(message), HttpStatus.BAD_REQUEST);
	}

	@Nullable
	private BaseRes<Void> processConversionNotSupportedException(final TypeMismatchException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(retrieveValidationErrorMessage(e.getClass().getSimpleName(), e.getRequiredType(), e.getPropertyName(), e.getValue())), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	private BaseRes<Void> processHttpMessageNotWritableException(final HttpMessageNotWritableException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from("Failed to write request"), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	private BaseRes<Void> processIllegalArgumentException(final IllegalArgumentException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Nullable
	private BaseRes<Void> processMethodValidationException(final MethodValidationException e, final WebRequest wr) {
		return response(e, wr, BaseRes.from("Method validation failed"), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private void printError(final Exception e, final String message, final HttpStatus status) {

		log.debug("status code : {} | message : {}", status.value(), message);

		if (HttpStatus.BAD_REQUEST.value() == status.value()) {
			log.debug("", e); return;
		}
		if (HttpStatus.Series.CLIENT_ERROR == HttpStatus.Series.resolve(status.value())) {
			log.info("", e); return;
		}
		if (e instanceof BaseException && Objects.nonNull(e.getCause())) {
			log.warn("", e.getCause()); return;
		}

		log.warn("", e);
	}

	private boolean responseCommitted(final Exception e, final WebRequest wr) {

		if (wr instanceof ServletWebRequest swr && Objects.nonNull(swr.getResponse()) && swr.getResponse().isCommitted()) {
			log.warn("Response already committed. Ignoring : {}", e.getClass().getName()); return true;
		}

		return false;
	}

	private void putAttributeIfMatched(final Exception e, WebRequest wr, final HttpStatus status) {
		if (HttpStatus.INTERNAL_SERVER_ERROR.value() == status.value()) { wr.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, e, SCOPE_REQUEST); }
	}

	@Nullable
	private BaseRes<Void> response(final Exception e, final WebRequest wr, final BaseRes<Void> res, final HttpStatus status) {

		printError(e, res.getMessage(), status);

		if (responseCommitted(e, wr)) { return null; }

		putAttributeIfMatched(e, wr, status);

		return res;
	}

	private String retrieveValidationErrorMessage(final String prop, @Nullable final Object... arr) {
		return accessor.getMessage(Constants.BASE_PACKAGE + ".validation.exceptions." + prop + ".message", arr, Constants.DEFAULT_EXCEPTION_MESSAGE);
	}
}
