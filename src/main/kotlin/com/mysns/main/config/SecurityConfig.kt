package com.mysns.main.config

import com.mysns.main.auth.AuthenticatedUser
import com.mysns.main.auth.DevAutoAuthFilter
import com.mysns.main.auth.JwtAuthFilter
import com.mysns.main.graphql.data.UserStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthFilter: JwtAuthFilter,
        userStore: UserStore,
        @Value("\${mysns.security.dev-mode:false}") devMode: Boolean,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        if (devMode) {
            log.warn("===== DEV MODE ENABLED — all requests permitted, auto-auth as first seeded user =====")
            http.addFilterAfter(DevAutoAuthFilter(userStore), JwtAuthFilter::class.java)
            http.authorizeHttpRequests { it.anyRequest().permitAll() }
        } else {
            http.authorizeHttpRequests {
                it.requestMatchers("/graphql", "/graphiql/**").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                it.requestMatchers("/upload").authenticated()
                // 본인이 업로드한 temp 폴더만 접근 가능 (path 의 userId 와 현재 사용자 비교) — 아래 공개 GET 보다 먼저 매칭.
                it.requestMatchers("/uploads/temp/**").access(tempOwnerAuth())
                // post·seed·avatar 등 미디어 GET 은 공개 — <img>·next/image 옵티마이저는 Bearer 토큰을 못 싣는다.
                it.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                it.anyRequest().authenticated()
            }
        }
        return http.build()
    }

    private fun tempOwnerAuth(): AuthorizationManager<RequestAuthorizationContext> =
        AuthorizationManager { authSupplier, ctx ->
            val auth = authSupplier.get()
            val principal = auth?.principal as? AuthenticatedUser
                ?: return@AuthorizationManager AuthorizationDecision(false)
            // URI: /uploads/temp/{userId}/{filename}
            val segments = ctx.request.requestURI.split('/').filter { it.isNotEmpty() }
            // [uploads, temp, userId, filename, ...]
            val pathUserId = segments.getOrNull(2)?.toLongOrNull()
                ?: return@AuthorizationManager AuthorizationDecision(false)
            AuthorizationDecision(pathUserId == principal.userId)
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

    companion object {
        private val log = LoggerFactory.getLogger(SecurityConfig::class.java)
    }
}
