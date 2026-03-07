package io.github.ygrip.testara.command;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Command Module Tests")
@SelectPackages({"io.github.ygrip.testara.command"})
@ExcludeTags({"disabled", "ignored"})
public class CommandTestSuites {
}
