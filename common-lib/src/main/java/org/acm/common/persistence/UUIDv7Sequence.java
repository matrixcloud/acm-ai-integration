/* (C)2025 */
package org.acm.common.persistence;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

@IdGeneratorType(UUIDv7Generator.class)
@Retention(RUNTIME)
@Target({METHOD, FIELD}) // ← 绑定实现类
public @interface UUIDv7Sequence {}
