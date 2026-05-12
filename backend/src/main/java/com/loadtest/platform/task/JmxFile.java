package com.loadtest.platform.task;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JmxFileValidator.class)
public @interface JmxFile {

    String message() default "jmxFile must end with .jmx";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
