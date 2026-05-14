package com.mysns.main.graphql

import com.mysns.main.auth.AuthService
import com.mysns.main.graphql.model.AuthPayload
import com.mysns.main.graphql.model.LoginInput
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class AuthController(private val authService: AuthService) {

    @MutationMapping
    fun login(@Argument input: LoginInput): AuthPayload =
        authService.login(input.username, input.password)
}