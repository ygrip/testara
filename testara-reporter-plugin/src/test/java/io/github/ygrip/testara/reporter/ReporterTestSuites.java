package io.github.ygrip.testara.reporter;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Reporter Plugin Tests")
@SelectPackages({"io.github.ygrip.testara.reporter"})
@ExcludeTags({"disabled", "ignored"})
public class ReporterTestSuites {
}
