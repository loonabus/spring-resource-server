package io.doe.config;

import com.github.gavlyukovskiy.boot.jdbc.decorator.DataSourceDecoratorAutoConfiguration;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.AbstractRequestLoggingFilter;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import java.io.*;
import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see LoggingConfig
 * @since 2024-07-08
 */

@Configuration
@ConditionalOnClass(Aspect.class)
@ConditionalOnProperty(prefix="spring.aop", name="auto", havingValue="true", matchIfMissing=true)
public class LoggingConfig {

	protected static class LoggingPointcut {

		@Pointcut("within(io.doe..*)")
		void w() { /* Pointcut Designator Method */ }

		@Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
		void c() { /* Pointcut Designator Method */ }

		@Pointcut("execution(public * io.doe..*Controller.*(..))")
		void e() { /* Pointcut Designator Method */ }

		@Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping) || "
				+ "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
				+ "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
				+ "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
		void m() { /* Pointcut Designator Method */ }
	}

	@Slf4j
	protected static class ParameterExtractor {

		private static final String ERROR_FORMAT = "Param Extraction From Method Failure ({} Exception) -> {}";

		public Map<String, Object> extract(final JoinPoint point) {

			try {
				final String[] ns = ((MethodSignature)point.getSignature()).getParameterNames();
				final Object[] vs = Optional.ofNullable(point.getArgs()).orElseGet(() -> new Object[]{});

				return IntStream.range(0, vs.length).boxed().collect(Collectors.toMap(n -> ns[n], n -> convertIfMatched(vs[n])));
			} catch (final RuntimeException re) {
				log.warn(ERROR_FORMAT, "Runtime", point.getSignature().getName(), re);
			} catch (final Exception ee) {
				log.warn(ERROR_FORMAT, "Checked", point.getSignature().getName(), ee);
			}

			return Map.of();
		}

		private boolean skipIfMatched(final Object source) {

			return
					source instanceof Model || source instanceof ModelMap ||
					source instanceof File || source instanceof MultipartFile ||
					source instanceof Errors || source instanceof Principal ||
					source instanceof Locale || source instanceof HttpMethod ||
					source instanceof Reader || source instanceof Writer ||
					source instanceof InputStream || source instanceof OutputStream ||
					source instanceof SessionStatus || source instanceof WebRequest ||
					source instanceof ServletRequest || source instanceof MultipartRequest
					;
		}

		private Object convertIfMatched(final @Nullable Object source) {
			return Optional.ofNullable(source).filter(s -> !skipIfMatched(s)).orElseGet(Optional::empty);
		}
	}

	@Slf4j
	@Aspect
	@Order(101)
	public static class HandlerArgumentsLoggingAspect {

		private final ParameterExtractor extractor;

		protected HandlerArgumentsLoggingAspect(final ParameterExtractor extractor) {
			this.extractor = extractor;
		}

		@Before("io.doe.config.LoggingConfig.LoggingPointcut.w() && " +
				"io.doe.config.LoggingConfig.LoggingPointcut.c() && " +
				"io.doe.config.LoggingConfig.LoggingPointcut.e() && " +
				"io.doe.config.LoggingConfig.LoggingPointcut.m() && " + "args(..)")
		public void printHandlerArguments(final JoinPoint point) {
			log.debug("@@@ {}.{} @@@ -> {}", point.getTarget().getClass().getSimpleName(), point.getSignature().getName(), extractor.extract(point));
		}
	}

	@Bean
	public HandlerArgumentsLoggingAspect paramLoggingAspect() {
		return new HandlerArgumentsLoggingAspect(new ParameterExtractor());
	}

	@Configuration
	public static class RequestLoggingFilterConfig {

		@Bean
		public FilterRegistrationBean<AbstractRequestLoggingFilter> requestLoggingFilterRegisterer() {

			final FilterRegistrationBean<AbstractRequestLoggingFilter> bean = new FilterRegistrationBean<>();

			final CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
			filter.setIncludeQueryString(true); filter.setIncludePayload(true);
			filter.setMaxPayloadLength(10000);  filter.setIncludeHeaders(true);

			bean.setFilter(filter);
			bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

			return bean;
		}
	}

	@Configuration
	@AutoConfigureAfter(DataSourceDecoratorAutoConfiguration.class)
	public static class P6SqlLogMessageFormatConfig {

		@PostConstruct
		public void setLogMessageFormat() {
			P6SpyOptions.getActiveInstance().setLogMessageFormat(CustomP6SqlLogFormat.class.getName());
		}
	}

	public static class CustomP6SqlLogFormat implements MessageFormattingStrategy {

		private static final Set<String> DDL_PREFIX = Set.of("create", "alter", "drop", "comment");

		@Override
		public String formatMessage(final int cid, final String now, final long took, final String category, final String prepared, final String q, final String addr) {
			if (!StringUtils.hasText(q)) { return q; }
			return "#" + now + "# | " + took + "ms | " + category + " | connection" + cid + " | " + addr + (Category.STATEMENT.getName().equals(category) ? formatMore(q.strip()) : q.strip());
		}

		private String formatMore(final String source) {
			return (DDL_PREFIX.stream().anyMatch(v -> source.toLowerCase(LocaleContextHolder.getLocale()).startsWith(v)) ? FormatStyle.DDL : FormatStyle.BASIC).getFormatter().format(source);
		}
	}
}
