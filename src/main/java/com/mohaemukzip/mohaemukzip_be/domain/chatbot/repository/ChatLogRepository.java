package com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    Page<ChatLog> findAllByMember_IdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    Page<ChatLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
