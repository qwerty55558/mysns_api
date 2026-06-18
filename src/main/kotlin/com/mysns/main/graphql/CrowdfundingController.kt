package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.Backing
import com.mysns.main.graphql.data.BackingStatus
import com.mysns.main.graphql.data.ChecklistItem
import com.mysns.main.graphql.data.Crowdfunding
import com.mysns.main.graphql.data.CrowdfundingStatus
import com.mysns.main.graphql.data.CrowdfundingStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class CrowdfundingController(
    private val crowdfundingStore: CrowdfundingStore,
    private val postStore: PostStore,
    private val userStore: UserStore,
) {

    // ─── Queries ───────────────────────────────────────────────────────────────

    @QueryMapping
    fun crowdfundings(@Argument limit: Int, @Argument offset: Int): List<Crowdfunding> =
        crowdfundingStore.list(limit, offset)

    @QueryMapping
    fun crowdfunding(@Argument id: String): Crowdfunding? =
        crowdfundingStore.findById(id.toLong())

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun myCrowdfundings(@Argument limit: Int, @Argument offset: Int): List<Crowdfunding> {
        val current = requireCurrentUser()
        return crowdfundingStore.myCrowdfundings(current.userId, limit, offset)
    }

    // ─── Mutations ─────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun backCrowdfunding(@Argument crowdfundingId: String, @Argument amount: Int): Crowdfunding {
        val current = requireCurrentUser()
        crowdfundingStore.back(current.userId, crowdfundingId.toLong(), amount)
        return crowdfundingStore.findById(crowdfundingId.toLong())
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun cancelBacking(@Argument crowdfundingId: String): Crowdfunding {
        val current = requireCurrentUser()
        crowdfundingStore.cancelBacking(current.userId, crowdfundingId.toLong())
        return crowdfundingStore.findById(crowdfundingId.toLong())
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun closeCrowdfunding(@Argument crowdfundingId: String): Crowdfunding =
        crowdfundingStore.closeCrowdfunding(requireCurrentUser().userId, crowdfundingId.toLong())

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun addChecklistItem(@Argument crowdfundingId: String, @Argument text: String): ChecklistItem =
        crowdfundingStore.addChecklistItem(requireCurrentUser().userId, crowdfundingId.toLong(), text)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun toggleChecklistItem(@Argument itemId: String): ChecklistItem =
        crowdfundingStore.toggleChecklistItem(requireCurrentUser().userId, itemId.toLong())

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun removeChecklistItem(@Argument itemId: String): Boolean =
        crowdfundingStore.removeChecklistItem(requireCurrentUser().userId, itemId.toLong())

    // ─── Schema Mappings on Crowdfunding type ──────────────────────────────────

    @SchemaMapping(typeName = "Crowdfunding", field = "post")
    fun post(cf: Crowdfunding): Post =
        postStore.findById(cf.postId) ?: error("post not found for crowdfunding ${cf.id}")

    @SchemaMapping(typeName = "Crowdfunding", field = "creator")
    fun creator(cf: Crowdfunding): User =
        userStore.findById(cf.creatorId) ?: error("creator not found for crowdfunding ${cf.id}")

    @SchemaMapping(typeName = "Crowdfunding", field = "backerCount")
    fun backerCount(cf: Crowdfunding): Int =
        crowdfundingStore.activeBackingCount(cf.id).toInt()

    @SchemaMapping(typeName = "Crowdfunding", field = "progressPercent")
    fun progressPercent(cf: Crowdfunding): Int =
        if (cf.goalAmount > 0) (cf.currentAmount.toLong() * 100 / cf.goalAmount).toInt() else 0

    @SchemaMapping(typeName = "Crowdfunding", field = "canCloseEarly")
    fun canCloseEarly(cf: Crowdfunding): Boolean {
        val v = currentUser() ?: return false
        return v.userId == cf.creatorId &&
            cf.status == CrowdfundingStatus.OPEN &&
            cf.currentAmount >= cf.goalAmount
    }

    @SchemaMapping(typeName = "Crowdfunding", field = "viewerBacking")
    fun viewerBacking(cf: Crowdfunding): Backing? {
        val v = currentUser() ?: return null
        return crowdfundingStore.viewerBacking(v.userId, cf.id)
    }

    @SchemaMapping(typeName = "Crowdfunding", field = "checklist")
    fun checklist(cf: Crowdfunding): List<ChecklistItem> =
        crowdfundingStore.checklist(cf.id)
}
