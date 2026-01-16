package com.ngcin.ems.mapper.annotations;

import com.ngcin.ems.mapper.core.IdType;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Id {
    IdType type() default IdType.AUTO;
}
