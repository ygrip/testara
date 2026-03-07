package io.github.ygrip.testara.core.data;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>DefaultRequestData class.</p>
 *
 * @author yunaz.ramadhan
 * @version $Id: $Id
 */
@Data
@RequestData(order = 0)
@EqualsAndHashCode(callSuper = true)
@TestComponent(scope = RegistryScope.TEST)
public class DefaultRequestData extends DefaultData {
}
