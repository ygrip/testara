package ${package};

import org.junit.runner.RunWith;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;

//@formatter:off
@RunWith(Cucumber.class)
@CucumberOptions(
    features = "classpath:features",
    glue = {"io.github.ygrip.testara", "${package}"}
)
public class Junit4RunnerTests {
}
//@formatter:on
