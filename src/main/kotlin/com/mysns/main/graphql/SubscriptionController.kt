package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.Subscription
import com.mysns.main.graphql.data.SubscriptionStore
import com.mysns.main.graphql.data.NameEmphasis
import com.mysns.main.graphql.data.NameFont
import com.mysns.main.graphql.data.ThemePreset
import com.mysns.main.graphql.model.SubscribeInput
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class SubscriptionController(
    private val subscriptionStore: SubscriptionStore,
) {
    /** 내 구독 정보 조회. */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun mySubscription(): Subscription? =
        subscriptionStore.mySubscription(requireCurrentUser().userId)

    /** 구독 신청. */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun subscribe(@Argument input: SubscribeInput): Subscription =
        subscriptionStore.subscribe(requireCurrentUser().userId, input.plan, input.theme, input.emphasis, input.font)

    /** 구독 테마 변경. */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun changeSubscriptionTheme(@Argument theme: ThemePreset): Subscription =
        subscriptionStore.changeTheme(requireCurrentUser().userId, theme)

    /** 이름 강조효과 변경 (ACTIVE 구독자만). */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun changeNameEmphasis(@Argument emphasis: NameEmphasis): Subscription =
        subscriptionStore.changeNameEmphasis(requireCurrentUser().userId, emphasis)

    /** 이름 폰트 변경 (ACTIVE 구독자만). */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun changeNameFont(@Argument font: NameFont): Subscription =
        subscriptionStore.changeNameFont(requireCurrentUser().userId, font)

    /** 구독 해지 (자동 갱신 비활성화, 기간말 만료). */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun cancelSubscription(): Subscription =
        subscriptionStore.cancel(requireCurrentUser().userId)

    /** User.isSubscriber — 배치 조회. */
    @BatchMapping(typeName = "User", field = "isSubscriber")
    fun isSubscriber(users: List<User>): Map<User, Boolean> {
        val activeMap = subscriptionStore.activeByOwnerIds(users.map { it.id })
        return users.associateWith { activeMap.containsKey(it.id) }
    }

    /** User.activeTheme — 배치 조회. ACTIVE 구독자만 테마 반환, 나머지는 null. */
    @BatchMapping(typeName = "User", field = "activeTheme")
    fun activeTheme(users: List<User>): Map<User, ThemePreset?> {
        val activeMap = subscriptionStore.activeByOwnerIds(users.map { it.id })
        return users.associateWith { activeMap[it.id]?.theme }
    }

    /** User.activeEmphasis — 배치 조회. ACTIVE 구독자만 강조효과 반환, 나머지는 null. */
    @BatchMapping(typeName = "User", field = "activeEmphasis")
    fun activeEmphasis(users: List<User>): Map<User, NameEmphasis?> {
        val activeMap = subscriptionStore.activeByOwnerIds(users.map { it.id })
        return users.associateWith { activeMap[it.id]?.nameEmphasis }
    }

    /** User.activeFont — 배치 조회. ACTIVE 구독자만 폰트 반환, 나머지는 null. */
    @BatchMapping(typeName = "User", field = "activeFont")
    fun activeFont(users: List<User>): Map<User, NameFont?> {
        val activeMap = subscriptionStore.activeByOwnerIds(users.map { it.id })
        return users.associateWith { activeMap[it.id]?.nameFont }
    }

    /**
     * User.subscription — 본인만 볼 수 있음.
     * 로그인하지 않았거나 타인 프로필 조회 시 null 반환.
     */
    @SchemaMapping(typeName = "User", field = "subscription")
    fun subscription(user: User): Subscription? {
        val current = currentUser() ?: return null
        if (current.userId != user.id) return null
        return subscriptionStore.mySubscription(user.id)
    }
}
