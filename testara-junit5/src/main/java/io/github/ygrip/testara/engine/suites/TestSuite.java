package io.github.ygrip.testara.engine.suites;

import io.github.ygrip.testara.engine.support.CustomTestNameGenerator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.platform.commons.annotation.Testable;
import org.junit.platform.suite.api.ExcludeEngines;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Testable
@Suite
@DisplayNameGeneration(CustomTestNameGenerator.class)
@IncludeEngines(value = {"testara-cucumber"})
@ExcludeEngines(value = {"junit-jupiter", "junit-platform-suite", "cucumber", "cucumber-junit"})
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestSuite {

}