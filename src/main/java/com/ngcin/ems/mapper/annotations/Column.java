package com.ngcin.ems.mapper.annotations;

import org.apache.ibatis.type.JdbcType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String name() default "";
    String value() default "";
    JdbcType jdbcType() default JdbcType.UNDEFINED;
}