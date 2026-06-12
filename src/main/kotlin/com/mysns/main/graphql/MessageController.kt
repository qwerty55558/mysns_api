package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.Conversation
import com.mysns.main.graphql.data.ConversationStore
import com.mysns.main.graphql.data.Message
import com.mysns.main.graphql.data.MessageStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.SendMessageInput
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class MessageController(
    private val conversationStore: ConversationStore,
    private val messageStore: MessageStore,
    private val userStore: UserStore,
    private val postStore: PostStore,
) {

    // ---------------------------------------------------------------- Queries

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun conversations(@Argument limit: Int, @Argument offset: Int): List<Conversation> {
        val current = requireCurrentUser()
        return conversationStore.listFor(current.userId, limit, offset)
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun conversation(@Argument id: String): Conversation? {
        val current = requireCurrentUser()
        val conversation = conversationStore.findById(id.toLong()) ?: return null
        if (!conversation.hasParticipant(current.userId)) {
            throw AccessDeniedException("not a participant of this conversation")
        }
        return conversation
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun conversationWith(@Argument userId: String): Conversation? {
        val current = requireCurrentUser()
        return conversationStore.findExisting(current.userId, userId.toLong())
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun messages(
        @Argument conversationId: String,
        @Argument limit: Int,
        @Argument offset: Int,
    ): List<Message> {
        val current = requireCurrentUser()
        val conversation = conversationStore.findById(conversationId.toLong()) ?: return emptyList()
        if (!conversation.hasParticipant(current.userId)) {
            throw AccessDeniedException("not a participant of this conversation")
        }
        return messageStore.list(conversation.id, limit, offset)
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun unreadMessageCount(): Int {
        val current = requireCurrentUser()
        return conversationStore.totalUnread(current.userId)
    }

    // -------------------------------------------------------------- Mutations

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun sendMessage(@Argument input: SendMessageInput): Message {
        val current = requireCurrentUser()
        val recipientId = input.recipientId.toLong()
        userStore.findById(recipientId)
            ?: throw IllegalArgumentException("recipient not found: ${input.recipientId}")
        return messageStore.send(
            senderId = current.userId,
            recipientId = recipientId,
            text = input.text,
            sharedPostId = input.sharedPostId?.toLong(),
        )
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun markConversationRead(@Argument conversationId: String): Conversation {
        val current = requireCurrentUser()
        val conversation = conversationStore.findById(conversationId.toLong())
            ?: throw IllegalArgumentException("conversation not found: $conversationId")
        if (!conversation.hasParticipant(current.userId)) {
            throw AccessDeniedException("not a participant of this conversation")
        }
        return conversationStore.markRead(conversation, current.userId)
    }

    // -------------------------------------------- Conversation field resolvers

    @SchemaMapping(typeName = "Conversation", field = "participant")
    fun participant(conversation: Conversation): User {
        val current = requireCurrentUser()
        return userStore.findById(conversation.otherUserId(current.userId))!!
    }

    @SchemaMapping(typeName = "Conversation", field = "lastMessage")
    fun lastMessage(conversation: Conversation): Message? =
        conversationStore.lastMessage(conversation)

    @SchemaMapping(typeName = "Conversation", field = "unreadCount")
    fun unreadCount(conversation: Conversation): Int {
        val current = requireCurrentUser()
        return conversationStore.unreadCountFor(conversation, current.userId)
    }

    // ------------------------------------------------- Message field resolvers

    @SchemaMapping(typeName = "Message", field = "sender")
    fun sender(message: Message): User = userStore.findById(message.senderId)!!

    @SchemaMapping(typeName = "Message", field = "sharedPost")
    fun sharedPost(message: Message): Post? =
        message.sharedPostId?.let { postStore.findById(it) }

    @SchemaMapping(typeName = "Message", field = "viewerIsSender")
    fun viewerIsSender(message: Message): Boolean =
        currentUser()?.userId == message.senderId
}
