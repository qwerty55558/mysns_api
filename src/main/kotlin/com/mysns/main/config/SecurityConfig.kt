package com.mysns.main.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/graphql", "/graphiql/**").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(
        @Value("\${mysns.cors.allowed-origins:http://localhost:3000}") originsCsv: String,
    ): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = originsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
