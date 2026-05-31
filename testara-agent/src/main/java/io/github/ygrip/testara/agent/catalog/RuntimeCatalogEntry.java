package io.github.ygrip.testara.agent.catalog;

import java.util.List;

/**
 * One entry in the runtime config catalog, discovered by scanning @LoadProperties annotations.
 */
public record RuntimeCatalogEntry(
    String slice,      // api | ui-selenium | ui-playwright | sql | mongo | kafka | command | validation
    String prefix,     // e.g. "api.service", "selenium.driver", "sql.service"
    String module,     // e.g. "testara-api", "testara-ui-selenium"
    String className,  // e.g. "ApiProperties"
    List<String> exampleKeys  // representative config key examples for this prefix
) {}
