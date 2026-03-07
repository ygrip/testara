package io.github.ygrip.testara.ui.selenium.page;

import org.openqa.selenium.By;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Page;

@Page(name = "pokemon",
  url = "https://pokemondb.net/pokedex/national",
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class PokemonPage extends SeleniumPage {
  private final By generations = By.cssSelector("div.infocard-list-pkmn-lg");
  private final By generationNumber = By.xpath("preceding-sibling::h2");
  private final By infoCard = By.cssSelector("div.infocard");
  private final By pokemonNumber = By.cssSelector("* > span.infocard-lg-data");
  private final By pokemonName = By.cssSelector("a.ent-name");
  private final By pokemonLink = By.cssSelector("* > span.infocard-lg-img > a");
  private final By imageLink = By.cssSelector("* > span.infocard-lg-img > a > *  > *.img-fixed");
  private final By pokemonTypes = By.cssSelector("* > span > small > a.itype");
}
