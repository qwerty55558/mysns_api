-- ============================================================
-- FK constraints with ON DELETE CASCADE (or SET NULL).
-- Idempotent: DROP IF EXISTS then ADD.
-- Tables already created by Hibernate DDL; this runs after.
-- ============================================================

-- posts.author_id → users(id)
ALTER TABLE posts DROP CONSTRAINT IF EXISTS fk_posts_author;
ALTER TABLE posts ADD CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE;

-- post_image_urls.post_id → posts(id)
ALTER TABLE post_image_urls DROP CONSTRAINT IF EXISTS fk_post_image_urls_post;
ALTER TABLE post_image_urls ADD CONSTRAINT fk_post_image_urls_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

-- comments.post_id → posts(id)
ALTER TABLE comments DROP CONSTRAINT IF EXISTS fk_comments_post;
ALTER TABLE comments ADD CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

-- comments.author_id → users(id)
ALTER TABLE comments DROP CONSTRAINT IF EXISTS fk_comments_author;
ALTER TABLE comments ADD CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE;

-- post_likes.post_id → posts(id)
ALTER TABLE post_likes DROP CONSTRAINT IF EXISTS fk_post_likes_post;
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

-- post_likes.user_id → users(id)
ALTER TABLE post_likes DROP CONSTRAINT IF EXISTS fk_post_likes_user;
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- comment_likes.comment_id → comments(id)
ALTER TABLE comment_likes DROP CONSTRAINT IF EXISTS fk_comment_likes_comment;
ALTER TABLE comment_likes ADD CONSTRAINT fk_comment_likes_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE;

-- comment_likes.user_id → users(id)
ALTER TABLE comment_likes DROP CONSTRAINT IF EXISTS fk_comment_likes_user;
ALTER TABLE comment_likes ADD CONSTRAINT fk_comment_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- bookmarks.post_id → posts(id)
ALTER TABLE bookmarks DROP CONSTRAINT IF EXISTS fk_bookmarks_post;
ALTER TABLE bookmarks ADD CONSTRAINT fk_bookmarks_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

-- bookmarks.user_id → users(id)
ALTER TABLE bookmarks DROP CONSTRAINT IF EXISTS fk_bookmarks_user;
ALTER TABLE bookmarks ADD CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- follows.follower_id → users(id)
ALTER TABLE follows DROP CONSTRAINT IF EXISTS fk_follows_follower;
ALTER TABLE follows ADD CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE;

-- follows.followee_id → users(id)
ALTER TABLE follows DROP CONSTRAINT IF EXISTS fk_follows_followee;
ALTER TABLE follows ADD CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(id) ON DELETE CASCADE;

-- follow_requests.requester_id → users(id)
ALTER TABLE follow_requests DROP CONSTRAINT IF EXISTS fk_follow_requests_requester;
ALTER TABLE follow_requests ADD CONSTRAINT fk_follow_requests_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE;

-- follow_requests.target_id → users(id)
ALTER TABLE follow_requests DROP CONSTRAINT IF EXISTS fk_follow_requests_target;
ALTER TABLE follow_requests ADD CONSTRAINT fk_follow_requests_target FOREIGN KEY (target_id) REFERENCES users(id) ON DELETE CASCADE;

-- subscriptions.owner_id → users(id)
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS fk_subscriptions_owner;
ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- wallets.owner_id → users(id)
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS fk_wallets_owner;
ALTER TABLE wallets ADD CONSTRAINT fk_wallets_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- wallet_transactions.owner_id → users(id)
ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS fk_wallet_transactions_owner;
ALTER TABLE wallet_transactions ADD CONSTRAINT fk_wallet_transactions_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- wallet_transactions.counterparty_id → users(id)  (SET NULL — preserve history)
ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS fk_wallet_transactions_counterparty;
ALTER TABLE wallet_transactions ADD CONSTRAINT fk_wallet_transactions_counterparty FOREIGN KEY (counterparty_id) REFERENCES users(id) ON DELETE SET NULL;

-- split_bills.creator_id → users(id)
ALTER TABLE split_bills DROP CONSTRAINT IF EXISTS fk_split_bills_creator;
ALTER TABLE split_bills ADD CONSTRAINT fk_split_bills_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE;

-- split_participants.split_bill_id → split_bills(id)
ALTER TABLE split_participants DROP CONSTRAINT IF EXISTS fk_split_participants_bill;
ALTER TABLE split_participants ADD CONSTRAINT fk_split_participants_bill FOREIGN KEY (split_bill_id) REFERENCES split_bills(id) ON DELETE CASCADE;

-- split_participants.user_id → users(id)
ALTER TABLE split_participants DROP CONSTRAINT IF EXISTS fk_split_participants_user;
ALTER TABLE split_participants ADD CONSTRAINT fk_split_participants_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- conversations.user1_id → users(id)
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS fk_conversations_user1;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_user1 FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE;

-- conversations.user2_id → users(id)
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS fk_conversations_user2;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_user2 FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.conversation_id → conversations(id)
ALTER TABLE messages DROP CONSTRAINT IF EXISTS fk_messages_conversation;
ALTER TABLE messages ADD CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE;

-- messages.sender_id → users(id)
ALTER TABLE messages DROP CONSTRAINT IF EXISTS fk_messages_sender;
ALTER TABLE messages ADD CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.shared_post_id → posts(id)  (SET NULL — preserve message history)
ALTER TABLE messages DROP CONSTRAINT IF EXISTS fk_messages_shared_post;
ALTER TABLE messages ADD CONSTRAINT fk_messages_shared_post FOREIGN KEY (shared_post_id) REFERENCES posts(id) ON DELETE SET NULL;

-- notifications.recipient_id → users(id)
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS fk_notifications_recipient;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE;

-- notifications.actor_id → users(id)
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS fk_notifications_actor;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE;

-- crowdfundings.post_id → posts(id)
ALTER TABLE crowdfundings DROP CONSTRAINT IF EXISTS fk_crowdfundings_post;
ALTER TABLE crowdfundings ADD CONSTRAINT fk_crowdfundings_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

-- crowdfundings.creator_id → users(id)
ALTER TABLE crowdfundings DROP CONSTRAINT IF EXISTS fk_crowdfundings_creator;
ALTER TABLE crowdfundings ADD CONSTRAINT fk_crowdfundings_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE;

-- backings.crowdfunding_id → crowdfundings(id)
ALTER TABLE backings DROP CONSTRAINT IF EXISTS fk_backings_crowdfunding;
ALTER TABLE backings ADD CONSTRAINT fk_backings_crowdfunding FOREIGN KEY (crowdfunding_id) REFERENCES crowdfundings(id) ON DELETE CASCADE;

-- backings.user_id → users(id)
ALTER TABLE backings DROP CONSTRAINT IF EXISTS fk_backings_user;
ALTER TABLE backings ADD CONSTRAINT fk_backings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- crowdfunding_checklist_items.crowdfunding_id → crowdfundings(id)
ALTER TABLE crowdfunding_checklist_items DROP CONSTRAINT IF EXISTS fk_checklist_items_crowdfunding;
ALTER TABLE crowdfunding_checklist_items ADD CONSTRAINT fk_checklist_items_crowdfunding FOREIGN KEY (crowdfunding_id) REFERENCES crowdfundings(id) ON DELETE CASCADE;
