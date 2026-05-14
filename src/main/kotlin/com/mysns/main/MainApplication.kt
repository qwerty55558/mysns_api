package com.mysns.main

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class MainApplication

fun main(args: Array<String>) {
	runApplication<MainApplication>(*args)
}
