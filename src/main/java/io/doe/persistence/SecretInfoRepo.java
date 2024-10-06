package io.doe.persistence;

import io.doe.domain.SecretInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see SecretInfoRepo
 * @since 2024-07-08
 */

@Repository
public interface SecretInfoRepo extends JpaRepository<SecretInfo, Integer> { /* no additional operation for now */ }
