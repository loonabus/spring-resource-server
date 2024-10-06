package io.doe.service;

import io.doe.domain.PublicInfo;
import io.doe.domain.SecretInfo;
import io.doe.persistence.PublicInfoRepo;
import io.doe.persistence.SecretInfoRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see ResourceServiceImpl
 * @since 2024-07-08
 */

@Slf4j
@Service
public class ResourceServiceImpl implements ResourceService, BaseService {

	private final PublicInfoRepo pr;
	private final SecretInfoRepo sr;
	private final MessageSourceAccessor accessor;

	@Autowired
	public ResourceServiceImpl(final PublicInfoRepo pr, final SecretInfoRepo sr, final MessageSourceAccessor accessor) {
		this.pr = pr; this.sr = sr; this.accessor = accessor;
	}

	@Override
	public List<String> retrievePublicInfo() {
		return pr.findAll().stream().map(PublicInfo::getContents).filter(StringUtils::hasText).toList();
	}

	@Override
	public List<String> retrieveSecretInfo() {
		return sr.findAll().stream().map(SecretInfo::getContents).filter(StringUtils::hasText).toList();
	}

	@Override public MessageSourceAccessor retrieveAccessor() { return accessor; }
}
