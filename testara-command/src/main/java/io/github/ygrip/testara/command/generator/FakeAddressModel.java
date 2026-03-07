package io.github.ygrip.testara.command.generator;

/**
 * Model representing a fake address with its components.
 *
 * @param fullAddress  the complete formatted address
 * @param streetAddress the street portion of the address
 * @param city         the city name
 * @param state        the state or province
 * @param country      the country name
 * @param zipCode      the postal/zip code
 */
public record FakeAddressModel(
    String fullAddress,
    String streetAddress,
    String city,
    String state,
    String country,
    String zipCode
) {
}
