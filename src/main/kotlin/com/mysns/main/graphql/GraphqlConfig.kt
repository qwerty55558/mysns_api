package com.mysns.main.graphql

import graphql.scalars.ExtendedScalars
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

@Configuration
class GraphqlConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { builder ->
            builder.scalar(ExtendedScalars.DateTime)
        }
}
