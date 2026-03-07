package io.github.ygrip.testara.streaming;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Streaming Module Tests")
@SelectPackages({"io.github.ygrip.testara.streaming"})
@ExcludeTags({"disabled", "ignored"})
public class StreamingTestSuites {
}
