package io.doe.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;

/**
 * @author <jongsung.choi@kubeworks.net>
 * @version 1.0.0
 * @see MiscConfig
 * @since 2024-07-08
 */

@Configuration
@EnableCaching(proxyTargetClass=true)
public class MiscConfig {

	@Bean
	public MessageSourceAccessor messageSourceAccessor(final MessageSource source) {
		return new MessageSourceAccessor(source);
	}
}
