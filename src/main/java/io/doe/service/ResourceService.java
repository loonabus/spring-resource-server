package io.doe.service;

import java.util.List;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see ResourceService
 * @since 2024-07-08
 */

public interface ResourceService {
	List<String> retrievePublicInfo();
	List<String> retrieveSecretInfo();
}