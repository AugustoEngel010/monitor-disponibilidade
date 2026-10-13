package br.com.monitor.monitoring;

import java.net.InetAddress;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MonitoringConfiguration {
    @Bean
    public DestinationPolicy destinationPolicy(Environment environment) {
        return new DestinationPolicy(environment.getProperty("monitor.demo-origin", ""));
    }

    @Bean
    public HttpCheckExecutor httpCheckExecutor(DestinationPolicy policy, Clock clock) {
        return new HttpCheckExecutor(policy, InetAddress::getAllByName, clock);
    }
}
