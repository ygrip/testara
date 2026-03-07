package io.github.ygrip.testara.properties;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Properties Module Tests")
@SelectPackages({"io.github.ygrip.testara.properties"})
@ExcludeTags({"disabled", "ignored"})
public class PropertiesTestSuites {
}
