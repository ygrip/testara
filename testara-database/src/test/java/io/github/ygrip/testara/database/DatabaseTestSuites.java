package io.github.ygrip.testara.database;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Database Module Tests")
@SelectPackages({"io.github.ygrip.testara.database"})
@ExcludeTags({"disabled", "ignored"})
public class DatabaseTestSuites {

}
