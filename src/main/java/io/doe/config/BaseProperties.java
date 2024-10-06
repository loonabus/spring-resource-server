package io.doe.config;

import io.doe.common.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see BaseProperties
 * @since 2024-07-08
 */

public final class BaseProperties {

	private BaseProperties() {
		throw new UnsupportedOperationException(Constants.UNSUPPORTED_OPERATION_MESSAGE);
	}

	@Getter @Validated
	@ConfigurationProperties(prefix="base.auth")
	public static class Auth {

		@NotBlank private final String rsaPath;

		public Auth(final String rsaPath) { this.rsaPath = rsaPath; }
	}

	@Getter @Validated
	@ConfigurationProperties(prefix="base.jackson")
	public static class Jackson {

		@NotNull private final ZoneId zoneId;
		@NotBlank private final String desFormat;
		@NotBlank private final String serDateFormat;
		@NotBlank private final String serDateTimeFormat;

		public Jackson(ZoneId zoneId, String desFormat, String serDateFormat, String serDateTimeFormat) {
			this.zoneId = zoneId;
			this.desFormat = desFormat;
			this.serDateFormat = serDateFormat;
			this.serDateTimeFormat = serDateTimeFormat;
		}
	}
}
