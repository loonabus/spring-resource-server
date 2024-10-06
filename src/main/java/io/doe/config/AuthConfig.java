package io.doe.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.doe.domain.BaseRes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see AuthConfig
 * @since 2024-07-08
 */

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BaseProperties.Auth.class)
public class AuthConfig {

	private final BaseProperties.Auth props;

	@Autowired
	public AuthConfig(final BaseProperties.Auth props) { this.props = props; }

	@Bean
	public SecurityFilterChain resourceServerFilterChain(
			final HttpSecurity http, final Jackson2ObjectMapperBuilder builder) throws Exception {

		http.cors(Customizer.withDefaults()).formLogin(AbstractHttpConfigurer::disable);
		http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
		http.sessionManagement(smc -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.headers(hc -> hc.frameOptions(foc -> foc.sameOrigin().xssProtection(xc -> xc.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED))));

		http.authorizeHttpRequests(rc -> rc.requestMatchers("/error").permitAll());
		http.securityMatcher("/rest/**").authorizeHttpRequests(rc -> {
			rc.requestMatchers(HttpMethod.GET, "/rest/v1/resource/public").hasAuthority("SCOPE_resource:read");
			rc.requestMatchers("/rest/v1/resource/secret").access(AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasAuthority("SCOPE_ADMIN"))).anyRequest().authenticated();
		});

		http.oauth2ResourceServer(rsc -> {
			rsc.accessDeniedHandler(new ForbiddenAccessDeniedHandler(builder.build()));
			rsc.authenticationEntryPoint(new BearerTokenUnauthorizedAuthenticationEntryPoint(builder.build()));
			rsc.jwt(new Customizer<>() { @Override @SneakyThrows
				public void customize(final OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer c) { c.decoder(jwtDecoder()); }
			});
		});

		http.exceptionHandling(createExceptionConfigurerCustomizer(builder.build()));

		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
		return NimbusJwtDecoder.withPublicKey((RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decodeBase64(Files.readAllBytes(ResourceUtils.getFile(props.getRsaPath()).toPath()))))).build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	interface ForbiddenResponseSender {

		default void sendJsonErrorResponse(final HttpServletResponse response) throws IOException {
			sendJsonErrorResponse(response, HttpStatus.FORBIDDEN, "Access Denied");
		}

		default void sendJsonErrorResponse(final HttpServletResponse response, final HttpStatus status, final String message) throws IOException {

			response.setStatus(status.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());

			try {
				response.getWriter().write(retrieveObjectMapper().writeValueAsString(BaseRes.from(message)));
			} catch (final JsonProcessingException e) {
				retrieveLogger().trace("", e); response.getWriter().write(message);
			}
		}

		Logger retrieveLogger();
		ObjectMapper retrieveObjectMapper();
	}

	@Slf4j
	static class BearerTokenUnauthorizedAuthenticationEntryPoint implements AuthenticationEntryPoint, ForbiddenResponseSender {

		private final ObjectMapper mapper;

		BearerTokenUnauthorizedAuthenticationEntryPoint(final ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public void commence(final HttpServletRequest request, final HttpServletResponse response, final AuthenticationException e) throws IOException {
			log.debug("", e);
			sendJsonErrorResponse(response, HttpStatus.UNAUTHORIZED, findJwtErrorMessageFrom(e.getCause()));
		}

		@Override public Logger retrieveLogger() { return log; }
		@Override public ObjectMapper retrieveObjectMapper() { return mapper; }

		private String findJwtErrorMessageFrom(@Nullable final Throwable e) {

			if (Objects.nonNull(e) && e instanceof JwtValidationException ve) {
				return ve.getErrors().stream().findFirst().map(r -> r.getErrorCode() + " : " + (Objects.nonNull(r.getDescription()) ? r.getDescription() : "")).orElse("Invalid Jwt");
			}

			return "Invalid Bearer Token";
		}
	}

	@Slf4j
	static class ForbiddenAccessDeniedHandler implements AccessDeniedHandler, ForbiddenResponseSender {

		private final ObjectMapper mapper;

		ForbiddenAccessDeniedHandler(final ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public void handle(final HttpServletRequest request, final HttpServletResponse response, final AccessDeniedException e) throws IOException {

			log.debug("access denied", e);
			if (response.isCommitted()) { log.trace("Did not write to response since already committed"); return; }
			sendJsonErrorResponse(response);
		}

		@Override public Logger retrieveLogger() { return log; }
		@Override public ObjectMapper retrieveObjectMapper() { return mapper; }
	}

	@Slf4j
	static class ForbiddenAuthenticationEntryPoint implements AuthenticationEntryPoint, ForbiddenResponseSender {

		private final ObjectMapper mapper;

		ForbiddenAuthenticationEntryPoint(final ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public void commence(final HttpServletRequest request, final HttpServletResponse response, final AuthenticationException e) throws IOException {
			log.debug("pre-authenticated entry point called. rejecting access", e);
			sendJsonErrorResponse(response);
		}

		@Override public Logger retrieveLogger() { return log; }
		@Override public ObjectMapper retrieveObjectMapper() { return mapper; }
	}

	private Customizer<ExceptionHandlingConfigurer<HttpSecurity>> createExceptionConfigurerCustomizer(final ObjectMapper mapper) {

		return c -> {
			final RequestMatcher rm = new MediaTypeRequestMatcher(MediaType.ALL, MediaType.APPLICATION_JSON, MediaType.TEXT_HTML);

			c.defaultAccessDeniedHandlerFor(new ForbiddenAccessDeniedHandler(mapper), rm);
			c.defaultAuthenticationEntryPointFor(new ForbiddenAuthenticationEntryPoint(mapper), rm);
		};
	}
}

