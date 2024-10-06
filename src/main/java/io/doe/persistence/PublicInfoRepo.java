package io.doe.persistence;

import io.doe.domain.PublicInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see PublicInfoRepo
 * @since 2024-07-08
 */

@Repository
public interface PublicInfoRepo extends JpaRepository<PublicInfo, Integer> { /* no additional operation for now */ }
