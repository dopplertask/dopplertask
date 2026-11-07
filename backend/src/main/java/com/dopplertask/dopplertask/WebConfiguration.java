package com.dopplertask.dopplertask;

import org.eclipse.jetty.ee11.servlet.ServletHandler;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/public/index.html");
    }

    /**
     * The files of the editor all carry the date of the build tool, not of the build, so that date cannot tell a
     * browser that it has an old editor. Their content can, and answering "not changed" stays quick.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> editorFileVersions() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/public/*");
        return registration;
    }

    /**
     * A webhook without a path is called with a slash at the end, as the editor shows it.
     */
    @Bean
    public UrlHandlerFilter trailingSlashes() {
        return UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build();
    }

    /**
     * A task name can hold a slash, which comes as %2F in the address of its webhook, and Jetty refuses that by default.
     */
    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> taskNamesWithSlashes() {
        return factory -> factory.addServerCustomizers(server -> {
            for (Connector connector : server.getConnectors()) {
                connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration()
                        .setUriCompliance(UriCompliance.DEFAULT.with("task names with slashes", UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR));
            }
            for (ServletHandler handler : server.getDescendants(ServletHandler.class)) {
                handler.setDecodeAmbiguousURIs(true);
            }
        });
    }
}
