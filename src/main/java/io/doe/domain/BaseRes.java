package io.doe.domain;

import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

import java.beans.ConstructorProperties;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see BaseRes
 * @since 2024-07-08
 */

@Getter @ToString
public final class BaseRes<T> {

	private final String code;
	private final String message;
	@Nullable private final T data;

	@ConstructorProperties({"code","message","data"})
	private BaseRes(final String code, final String message, @Nullable final T data) {
		this.code = code; this.message = message; this.data = data;
	}

	public static <T> BaseRes<T> from(final String message) {
		return new BaseRes<>("", message, null);
	}

	public static <T> BaseRes<T> success(@Nullable final T data) {
		return new BaseRes<>("", HttpStatus.OK.getReasonPhrase(), data);
	}
}
