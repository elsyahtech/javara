package com.elsyah.tech.javara.middleware.feature;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FeatureModulesProperties.class)
public class FeatureModulesConfiguration {
    @Bean
    static BeanFactoryPostProcessor featureDependencyValidator(Environment environment) {
        return beanFactory -> {
            boolean authApi = environment.getProperty(
                    "app.features.auth-api", Boolean.class, true);
            boolean resourceServer = environment.getProperty(
                    "app.features.resource-server", Boolean.class, true);
            boolean serviceAuth = environment.getProperty(
                    "app.security.service-auth.enabled", Boolean.class, true);

            if (authApi && !resourceServer) {
                throw new BeanDefinitionStoreException(
                        "Feature 'auth-api' requires 'resource-server'. Enable "
                                + "app.features.resource-server or disable app.features.auth-api.");
            }
            if (serviceAuth && !resourceServer) {
                throw new BeanDefinitionStoreException(
                        "Feature 'service-auth' requires 'resource-server'. Enable "
                                + "app.features.resource-server or disable "
                                + "app.security.service-auth.enabled.");
            }
        };
    }
}
