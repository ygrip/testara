package io.github.ygrip.testara.ui.playwright;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Playwright UI Module Tests")
@SelectPackages({"io.github.ygrip.testara.ui.playwright"})
@ExcludeTags({"disabled", "ignored"})
public class UITestSuites {

}
