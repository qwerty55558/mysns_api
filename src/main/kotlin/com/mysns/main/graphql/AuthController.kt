package com.mysns.main.graphql

import com.mysns.main.auth.AuthService
import com.mysns.main.graphql.model.AuthPayload
import com.mysns.main.graphql.model.LoginInput
import com.mysns.main.graphql.model.RegisterInput
import jakarta.validation.Valid
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated

@Controller
@Validated
class AuthController(private val authService: AuthService) {

    @MutationMapping
    fun login(@Argument input: LoginInput): AuthPayload =
        authService.login(input.username, input.password)

    @MutationMapping
    fun register(@Argument @Valid input: RegisterInput): AuthPayload =
        authService.register(input.username, input.password, input.displayName)
}
