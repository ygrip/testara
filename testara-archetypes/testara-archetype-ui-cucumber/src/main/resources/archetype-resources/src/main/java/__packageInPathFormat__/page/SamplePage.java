package ${package}.page;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Page;
#if($uiEngine == "playwright")
import io.github.ygrip.testara.ui.playwright.page.PlaywrightPage;
#elseif($uiEngine == "appium")
import io.github.ygrip.testara.ui.appium.page.AppiumPage;
#else
import io.github.ygrip.testara.ui.selenium.page.SeleniumPage;
#end

/** Replace this generic page with page objects for the application under test. */
@Page(name = "sample", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
public class SamplePage extends #if($uiEngine == "playwright")PlaywrightPage#elseif($uiEngine == "appium")AppiumPage#elseSeleniumPage#end {
}
