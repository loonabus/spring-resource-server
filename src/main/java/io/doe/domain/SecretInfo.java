package io.doe.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.LocalDateTime;

/**
 * @author <loonabus@gmail.com>
 * @version 1.0.0
 * @see SecretInfo
 * @since 2024-07-08
 */

@Entity @Table(name="SECRET_INFO")
@Getter @ToString @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SecretInfo {

	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer resourceId;
	private String contents;
	@CreationTimestamp(source=SourceType.DB) private LocalDateTime createDt;
}
