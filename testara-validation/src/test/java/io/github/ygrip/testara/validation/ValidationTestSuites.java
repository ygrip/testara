package io.github.ygrip.testara.validation;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Validation Module Tests")
@SelectPackages({"io.github.ygrip.testara.validation"})
@ExcludeTags({"disabled", "ignored"})
public class ValidationTestSuites {
}
