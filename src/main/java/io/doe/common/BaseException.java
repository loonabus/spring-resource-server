package io.doe.common;

import java.io.Serial;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see BaseException
 * @since 2024-07-08
 */

public class BaseException extends RuntimeException {

	@Serial private static final long serialVersionUID = 1L;

	public BaseException(final String message) {
		super(message);
	}
	public BaseException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
