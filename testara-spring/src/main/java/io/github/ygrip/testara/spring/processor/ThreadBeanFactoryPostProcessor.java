package io.github.ygrip.testara.spring.processor;

import io.github.ygrip.testara.spring.scope.AutomationScope;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * <p>ThreadBeanFactoryPostProcessor class.</p>
 *
 * @author yunaz.ramadhan on 3/15/2020
 * @version $Id: $Id
 */
public final class ThreadBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
  /** Constant <code>THREAD_SCOPE="testara-automation"</code> */
  public static final String THREAD_SCOPE = "testara-automation";

  /** {@inheritDoc} */
  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory factory)
      throws BeansException {
    factory.registerScope(THREAD_SCOPE, new AutomationScope());
  }
}
