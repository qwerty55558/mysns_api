package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.stub.InMemoryPostStore
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class UserController(
    private val userStore: InMemoryUserStore,
    private val postStore: InMemoryPostStore,
) {

    @QueryMapping
    fun me(): User? {
        val current = currentUser() ?: return null
        return userStore.findById(current.userId)
    }

    @QueryMapping
    fun user(@Argument id: String): User? = userStore.findById(id.toLong())

    @SchemaMapping(typeName = "User", field = "posts")
    fun posts(user: User, @Argument limit: Int, @Argument offset: Int): List<Post> =
        postStore.findByAuthor(user.id, limit, offset)

    @SchemaMapping(typeName = "User", field = "followerCount")
    fun followerCount(user: User): Int = 0

    @SchemaMapping(typeName = "User", field = "followingCount")
    fun followingCount(user: User): Int = 0
}
