package io.github.ygrip.testara.core.data;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.ResponseData;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>DefaultResponseData class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@ResponseData(order = 0)
@EqualsAndHashCode(callSuper = true)
@TestComponent(scope = RegistryScope.TEST)
public class DefaultResponseData extends DefaultData {
}
