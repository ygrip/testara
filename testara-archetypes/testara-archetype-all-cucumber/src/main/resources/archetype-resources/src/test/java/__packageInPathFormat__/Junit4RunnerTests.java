package ${package};

import org.junit.runner.RunWith;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import io.github.ygrip.testara.cucumber.factory.TestaraObjectFactory;

//@formatter:off
@RunWith(Cucumber.class)
@CucumberOptions(
    features = "classpath:features",
    glue = {"io.github.ygrip.testara", "${package}"},
    objectFactory = TestaraObjectFactory.class,
    stepNotifications = true
)
public class Junit4RunnerTests {
}
//@formatter:on
