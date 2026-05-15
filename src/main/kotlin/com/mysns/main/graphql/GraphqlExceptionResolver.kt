package com.mysns.main.graphql

import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import jakarta.validation.ConstraintViolationException
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType as SpringErrorType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class GraphqlExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToMultipleErrors(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): List<GraphQLError>? {
        if (ex is ConstraintViolationException) {
            return ex.constraintViolations.map { v ->
                buildError(
                    message = v.message,
                    classification = SpringErrorType.BAD_REQUEST,
                    env = env,
                    field = v.propertyPath?.toString()?.substringAfterLast('.'),
                )
            }
        }
        val rse = walk(ex).filterIsInstance<ResponseStatusException>().firstOrNull()
        if (rse != null) {
            return listOf(
                buildError(
                    message = rse.reason ?: rse.statusCode.toString(),
                    classification = httpToGraphql(rse.statusCode.value()),
                    env = env,
                )
            )
        }
        val ade = walk(ex).filterIsInstance<AccessDeniedException>().firstOrNull()
        if (ade != null) {
            return listOf(
                buildError(
                    message = ade.message ?: "접근 권한이 없습니다",
                    classification = SpringErrorType.FORBIDDEN,
                    env = env,
                )
            )
        }
        val bce = walk(ex).filterIsInstance<BadCredentialsException>().firstOrNull()
        if (bce != null) {
            return listOf(
                buildError(
                    message = bce.message ?: "인증 실패",
                    classification = SpringErrorType.UNAUTHORIZED,
                    env = env,
                )
            )
        }
        val authE = walk(ex).filterIsInstance<AuthenticationException>().firstOrNull()
        if (authE != null) {
            return listOf(
                buildError(
                    message = authE.message ?: "인증이 필요합니다",
                    classification = SpringErrorType.UNAUTHORIZED,
                    env = env,
                )
            )
        }
        val iae = walk(ex).filterIsInstance<IllegalArgumentException>().firstOrNull()
        if (iae != null) {
            return listOf(
                buildError(
                    message = iae.message ?: "잘못된 요청입니다",
                    classification = SpringErrorType.BAD_REQUEST,
                    env = env,
                )
            )
        }
        return null
    }

    private fun walk(ex: Throwable): Sequence<Throwable> =
        generateSequence<Throwable>(ex) { it.cause.takeIf { c -> c !== it } }

    private fun httpToGraphql(status: Int): ErrorClassification = when (status) {
        400 -> SpringErrorType.BAD_REQUEST
        401 -> SpringErrorType.UNAUTHORIZED
        403 -> SpringErrorType.FORBIDDEN
        404 -> SpringErrorType.NOT_FOUND
        409 -> SpringErrorType.BAD_REQUEST
        else -> ErrorType.DataFetchingException
    }

    private fun buildError(
        message: String,
        classification: ErrorClassification,
        env: DataFetchingEnvironment,
        field: String? = null,
    ): GraphQLError {
        val builder = GraphqlErrorBuilder.newError(env)
            .message(message)
            .errorType(classification)
        if (field != null) {
            builder.extensions(mapOf("field" to field))
        }
        return builder.build()
    }
}
