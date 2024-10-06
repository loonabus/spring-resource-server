package io.doe.web;

import io.doe.domain.BaseRes;
import io.doe.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see ResourceServerController
 * @since 2024-07-08
 */

@Validated
@RestController
@RequestMapping("/rest/v1/resource")
public class ResourceServerController {

	private final ResourceService service;

	@Autowired
	public ResourceServerController(final ResourceService service) {
		this.service = service;
	}

	@GetMapping("/public")
	public BaseRes<List<String>> retrievePublicInfo() {
		return BaseRes.success(service.retrievePublicInfo());
	}

	@GetMapping("/secret")
	public BaseRes<List<String>> retrieveSecretInfo() {
		return BaseRes.success(service.retrieveSecretInfo());
	}
}
