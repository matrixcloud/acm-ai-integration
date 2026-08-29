package org.acm.os.infra;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for {@code created_at} / {@code updated_at} columns.
 *
 * <p>No {@code AuditorAware} bean is registered because the current schema has no
 * {@code created_by}/{@code updated_by} columns.
 */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {}
