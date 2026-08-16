package com.makeup.location.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP WebSocket endpoint for mobile & web telemetry stream
        registry.addEndpoint("/ws-location")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws-location")
                .setAllowedOriginPatterns("*")
                .withSockJS();

    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Messages bound for clients subscribed to /topic or /queue
        registry.enableSimpleBroker("/topic", "/queue");
        // Messages bound for methods annotated with @MessageMapping in controllers
        registry.setApplicationDestinationPrefixes("/app");
    }
}
