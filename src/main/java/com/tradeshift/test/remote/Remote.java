package com.tradeshift.test.remote;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.runner.Runner;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Remote {

    String endpoint() default "http://localhost:4578/";
    Class<? extends Runner> runnerClass();
    
}
