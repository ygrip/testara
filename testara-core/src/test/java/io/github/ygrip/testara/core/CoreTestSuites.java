package io.github.ygrip.testara.core;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Core Automation Tests")
@SelectPackages({"io.github.ygrip.testara.core"})
@ExcludeTags({"disabled", "ignored"})
public class CoreTestSuites {
}
