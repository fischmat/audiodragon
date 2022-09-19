package de.matthiasfisch.audiodragon.config

import de.matthiasfisch.audiodragon.service.CAPTURE_EVENTS_TOPIC
import de.matthiasfisch.audiodragon.service.METRICS_EVENTS_TOPIC
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer


@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfiguration: WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/dragon")
            .setAllowedOrigins("http://localhost*", "app://.")
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker(CAPTURE_EVENTS_TOPIC, METRICS_EVENTS_TOPIC)
        registry.setApplicationDestinationPrefixes("/app")
    }
}