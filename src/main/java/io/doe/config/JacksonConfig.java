package io.doe.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Serial;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see JacksonConfig
 * @since 2024-07-08
 */

@Configuration
@AutoConfigureBefore(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(BaseProperties.Jackson.class)
public class JacksonConfig {

	private final BaseProperties.Jackson props;

	@Autowired
	public JacksonConfig(final BaseProperties.Jackson props) {
		this.props = props;
	}

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer objectMapperBuilderCustomizer() {

		final DateTimeFormatter dFormatter = DateTimeFormatter.ofPattern(props.getDesFormat());
		final DateTimeFormatter sFormatter = DateTimeFormatter.ofPattern(props.getSerDateTimeFormat());
		final SimpleModule module = new SimpleModule(CustomModule.CUSTOM_DATE_AND_MISC_MODULE.name(), Version.unknownVersion());

		module.addSerializer(new LocalDateSerializer(DateTimeFormatter.ofPattern(props.getSerDateFormat())));
		module.addSerializer(new LocalDateTimeSerializer(sFormatter));
		module.addSerializer(new ZonedDateTimeSerializer(sFormatter.withZone(props.getZoneId())));

		module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dFormatter));
		module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dFormatter));
		module.addDeserializer(ZonedDateTime.class, new ZonedDateTimeJsonDeserializer(dFormatter.withZone(props.getZoneId())));

		module.addDeserializer(String.class, new StringSanitizingDeserializer());

		return c -> c.findModulesViaServiceLoader(true).modulesToInstall(module);
	}

	@Slf4j
	static class ZonedDateTimeJsonDeserializer extends JsonDeserializer<ZonedDateTime> {

		private final DateTimeFormatter formatter;

		ZonedDateTimeJsonDeserializer(final DateTimeFormatter formatter) {
			this.formatter = formatter;
		}

		@Override
		public Class<ZonedDateTime> handledType() {
			return ZonedDateTime.class;
		}

		@Nullable @Override
		public ZonedDateTime deserialize(final JsonParser p, final DeserializationContext c) throws IOException {

			final String source = p.getText();

			try {
				return ZonedDateTime.parse(source, formatter);
			} catch (final NullPointerException e) {
				log.debug(e.getMessage());
			} catch (final DateTimeParseException e) {
				log.debug("Invalid ZonedDateTimeFormat '{}'", source);
			}

			return null;
		}
	}

	@Slf4j
	static class StringStripDeserializer extends StringDeserializer {

		@Serial private static final long serialVersionUID = 1L;

		@Override
		public String deserialize(final JsonParser p, final DeserializationContext c) throws IOException {
			final String source = super.deserialize(p, c); return StringUtils.hasLength(source) ? source.strip() : source;
		}
	}

	@Slf4j
	static class StringSanitizingDeserializer extends StringDeserializer implements ContextualDeserializer {

		@Serial private static final long serialVersionUID = 1L;
		private static final StringDeserializer DESERIALIZER = new StringStripDeserializer();

		@Override
		public JsonDeserializer<String> createContextual(final DeserializationContext c, final BeanProperty bp) {
			return Objects.nonNull(bp.getAnnotation(XssSanitize.class)) ? this : DESERIALIZER;
		}

		@Nullable @Override
		public String deserialize(final JsonParser p, final DeserializationContext c) throws IOException {
			final String source = super.deserialize(p, c);
			return StringEscapeUtils.escapeHtml4(StringUtils.hasLength(source) ? source.strip() : source);
		}
	}

	public enum CustomModule { CUSTOM_DATE_AND_MISC_MODULE }

	@Documented
	@Target({FIELD, PARAMETER})
	@Retention(RetentionPolicy.RUNTIME)
	public @interface XssSanitize { /* Marker Annotation For xss protection */ }
}
