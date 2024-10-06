package io.doe.config;

import com.google.common.base.CaseFormat;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardServletPartUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExtendedServletRequestDataBinder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see WebMvcConfig
 * @since 2024-07-08
 */

@Configuration
public class WebMvcConfig {

	@Bean
	public WebMvcConfigurer webMvcConfigurer() {

		return new WebMvcConfigurer() {

			@Override
			public void addFormatters(final FormatterRegistry fr) { fr.addConverter(String.class, String.class, String::strip); }

			@Override
			public void addCorsMappings(final CorsRegistry cr) {
				cr.addMapping("/**").allowedOrigins("*").allowedMethods(Arrays.stream(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new));
			}
		};
	}

	@Bean
	public HttpMessageConverters customRefinedHttpMessageConverters(final Jackson2ObjectMapperBuilder builder) {
		return new CustomHttpMessageConverters(new MappingJackson2HttpMessageConverter(builder.build()));
	}

	static class CustomHttpMessageConverters extends HttpMessageConverters {

		CustomHttpMessageConverters(final HttpMessageConverter<?>... converters) { super(converters); }

		@Override
		protected List<HttpMessageConverter<?>> postProcessConverters(final List<HttpMessageConverter<?>> converters) {
			return converters.stream().filter(c -> !(c instanceof MappingJackson2HttpMessageConverter cc)
					|| cc.getObjectMapper().getRegisteredModuleIds().stream().anyMatch(m -> Objects.equals(JacksonConfig.CustomModule.CUSTOM_DATE_AND_MISC_MODULE.name(), String.valueOf(m)))).toList();
		}
	}

	public static class RemoteAddressFilter implements Filter {

		@Override
		public void init(final FilterConfig filterConfig) { /* no operation here */ }

		@Override
		public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
			chain.doFilter((request instanceof HttpServletRequest r ? new RemoteAddressWrapper(r) : request), response);
		}

		@Override public void destroy() { /* no operation here */ }
	}

	protected static class RemoteAddressWrapper extends HttpServletRequestWrapper {

		private static final List<String> CANDIDATES;

		static {
			CANDIDATES = List.of("X-Forwarded-For","Proxy-Client-IP","WL-Proxy-Client-IP","HTTP_CLIENT_IP","HTTP_X_FORWARDED_FOR","X-Real-IP");
		}

		protected RemoteAddressWrapper(final HttpServletRequest request) {
			super(request);
		}

		@Override
		public String getRemoteHost() {

			try {
				return InetAddress.getByName(getRemoteAddr()).getHostName();
			} catch (final UnknownHostException e) {
				return getRemoteAddr();
			}
		}

		@Override
		public String getRemoteAddr() {
			return refine(CANDIDATES.stream().map(super::getHeader).filter(StringUtils::hasText).map(String::strip).filter(s -> !Objects.equals("unknown", s)).findFirst().orElseGet(super::getRemoteAddr));
		}

		private String refine(final String source) {
			return !source.contains(",") ? source.strip() : source.split(",")[0].strip();
		}
	}

	@Bean
	public FilterRegistrationBean<RemoteAddressFilter> remoteAddressFilterRegisterer() {

		final FilterRegistrationBean<RemoteAddressFilter> bean = new FilterRegistrationBean<>();
		bean.setFilter(new RemoteAddressFilter());
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);

		return bean;
	}

	public static class SnakeCaseParameterNameFilter implements Filter {

		@Override
		public void init(final FilterConfig filterConfig) { /* no operation here */ }

		@Override
		public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
			chain.doFilter(request instanceof HttpServletRequest sr ? new SnakeCaseParameterNameWrapper(sr) : request, response);
		}

		@Override public void destroy() { /* no operation here */ }
	}

	static class SnakeCaseParameterNameWrapper extends HttpServletRequestWrapper {

		private final Map<String, String[]> converted;

		SnakeCaseParameterNameWrapper(final HttpServletRequest request) {
			super(request);
			converted = convertParameters(request.getParameterMap());
		}

		@Nullable @Override
		public String getParameter(final String name) {
			return Optional.ofNullable(converted.get(name)).map(v -> v[0]).orElse(null);
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			return converted;
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(converted.keySet());
		}

		@Nullable @Override
		public String[] getParameterValues(final String name) {
			return converted.get(name);
		}

		@Override
		public Part getPart(final String name) throws IOException, ServletException {
			return Optional.ofNullable(super.getPart(toCamelCase(name))).orElse(super.getPart(name));
		}

		private String toCamelCase(final String source) {
			return !source.contains("_") ? source : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, source);
		}

		private Map<String, String[]> convertParameters(final Map<String, String[]> source) {
			return source.entrySet().stream().collect(Collectors.toMap(e -> toCamelCase(e.getKey()), Map.Entry::getValue));
		}
	}

	@Bean
	public FilterRegistrationBean<SnakeCaseParameterNameFilter> snakeCaseParameterNameFilterRegisterer() {

		final FilterRegistrationBean<SnakeCaseParameterNameFilter> bean = new FilterRegistrationBean<>();
		bean.setFilter(new SnakeCaseParameterNameFilter());
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 12);

		return bean;
	}

	@Bean
	public WebMvcRegistrations requestMappingHandlerAdapterProvider() {

		return new WebMvcRegistrations() {
			@Override
			public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
				return new SnakeCaseParameterRequestMappingHandlerAdapter();
			}
		};
	}

	@SuppressWarnings("squid:MaximumInheritanceDepth")
	static class SnakeCaseParameterRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {

		@Override
		protected InitBinderDataBinderFactory createDataBinderFactory(final List<InvocableHandlerMethod> methods) {

			return new ServletRequestDataBinderFactory(methods, super.getWebBindingInitializer()) {
				@Override
				protected ServletRequestDataBinder createBinderInstance(@Nullable final Object o, final String s, final NativeWebRequest nwr) {
					return new SnakeCaseParameterRequestDataBinder(o, s);
				}
			};
		}
	}

	@Slf4j
	static class SnakeCaseParameterRequestDataBinder extends ExtendedServletRequestDataBinder {

		SnakeCaseParameterRequestDataBinder(@Nullable final Object target, final String name) {
			super(target, name);
		}

		@Override
		protected void bindMultipart(final Map<String, List<MultipartFile>> mp, final MutablePropertyValues mpv) {

			mp.forEach((k, v) -> {
				final String converted = toCamelCase(k);
				if (v.size() == 1) {
					final MultipartFile value = v.getFirst();
					if (isBindEmptyMultipartFiles() || !value.isEmpty()) { mpv.add(converted, value); }
				} else { mpv.add(converted, v); }
			});
		}

		@Override
		protected void addBindValues(final MutablePropertyValues mpv, final ServletRequest request) {

			@SuppressWarnings("unchecked")
			final Map<String, String> pv = (Map<String, String>)request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

			if (Objects.nonNull(pv)) {
				pv.forEach((k, v) -> {
					if (mpv.contains(k)) {
						log.warn("PathVariable '{}' Found In RequestParameters", k);
						log.warn("Spring's default behavior is 'skip overwriting' but overwrite.");
					}
					mpv.addPropertyValue(k, v);
				});
			}

			if (StringUtils.startsWithIgnoreCase(request.getContentType(), MediaType.MULTIPART_FORM_DATA_VALUE)) {
				addServletPartsToMutablePropertyValues(mpv, WebUtils.getNativeRequest(request, HttpServletRequest.class));
			}
		}

		private String toCamelCase(final String source) {
			return !source.contains("_") ? source : CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, source);
		}

		private void addServletPartsToMutablePropertyValues(final MutablePropertyValues mpv, @Nullable final HttpServletRequest request) {

			if (Objects.nonNull(request) && HttpMethod.POST.matches(request.getMethod())) {
				StandardServletPartUtils.getParts(request).forEach((k, v) -> {
					final String converted = toCamelCase(k);
					if (v.size() == 1) {
						final Part part = v.getFirst();
						if (isBindEmptyMultipartFiles() || part.getSize() > 0) { mpv.add(converted, part); }
					} else { mpv.add(converted, v); }
				});
			}
		}
	}
}
