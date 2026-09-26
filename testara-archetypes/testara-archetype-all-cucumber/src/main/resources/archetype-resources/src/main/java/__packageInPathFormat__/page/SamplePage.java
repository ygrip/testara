package ${package}.page;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Page;
#if($uiEngine == "playwright")
import io.github.ygrip.testara.ui.playwright.page.PlaywrightPage;
#elseif($uiEngine == "appium")
import io.github.ygrip.testara.ui.appium.page.AppiumPage;
#elseif($uiEngine == "vibium")
import io.github.ygrip.testara.ui.vibium.page.VibiumPage;
#else
import io.github.ygrip.testara.ui.selenium.page.SeleniumPage;
#end

/** Replace this generic page with page objects for the application under test. */
@Page(name = "sample", url = "", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
#if($uiEngine == "playwright")
public class SamplePage extends PlaywrightPage {
#elseif($uiEngine == "appium")
public class SamplePage extends AppiumPage {
#elseif($uiEngine == "vibium")
public class SamplePage extends VibiumPage {
#else
public class SamplePage extends SeleniumPage {
#end
}
