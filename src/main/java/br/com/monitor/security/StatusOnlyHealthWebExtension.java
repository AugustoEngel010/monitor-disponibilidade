package br.com.monitor.security;

import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthEndpointWebExtension;
import org.springframework.stereotype.Component;

@Component
@EndpointWebExtension(endpoint = HealthEndpoint.class)
public class StatusOnlyHealthWebExtension extends HealthEndpointWebExtension {
    public StatusOnlyHealthWebExtension(HealthContributorRegistry registry, HealthEndpointGroups groups,
            HealthEndpointProperties properties) {
        super(registry, groups, properties.getLogging().getSlowIndicatorThreshold());
    }

    @Override
    public WebEndpointResponse<HealthComponent> health(ApiVersion version, WebServerNamespace namespace,
            SecurityContext securityContext, boolean showAll, String... path) {
        var response = super.health(version, namespace, securityContext, false, path);
        return response.getBody() == null ? response
                : new WebEndpointResponse<>(Health.status(response.getBody().getStatus()).build(),
                        response.getStatus());
    }
}
