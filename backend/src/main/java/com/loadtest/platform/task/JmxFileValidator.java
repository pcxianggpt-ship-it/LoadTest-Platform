package com.loadtest.platform.task;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JmxFileValidator implements ConstraintValidator<JmxFile, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.endsWith(".jmx");
    }
}
