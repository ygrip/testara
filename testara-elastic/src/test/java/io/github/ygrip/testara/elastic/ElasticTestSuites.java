package io.github.ygrip.testara.elastic;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Database Module Tests")
@SelectPackages({"io.github.ygrip.testara.elastic"})
@ExcludeTags({"disabled", "ignored"})
public class ElasticTestSuites {
}
