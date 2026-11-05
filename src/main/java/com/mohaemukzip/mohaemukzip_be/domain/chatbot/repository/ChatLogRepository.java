package com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    Page<ChatLog> findAllByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    Page<ChatLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("update ChatLog log set log.member = null where log.member.id = :memberId")
    int detachMember(@Param("memberId") Long memberId);
}
