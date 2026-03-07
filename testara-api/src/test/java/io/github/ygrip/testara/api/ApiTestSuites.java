package io.github.ygrip.testara.api;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Api Module Tests")
@SelectPackages({"io.github.ygrip.testara.api"})
@ExcludeTags({"disabled", "ignored"})
public class ApiTestSuites {
}
