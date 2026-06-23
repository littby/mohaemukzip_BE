package com.mohaemukzip.mohaemukzip_be.domain.member.repository;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member,Long> {
    Optional<Member> findById(Long id);
    Optional<Member> findByOauthId(String oauthId);
    Optional<Member> findByLoginTypeAndOauthId(LoginType loginType, String oauthId);
    Boolean existsByOauthId(String oauthId);
    Optional<Member> findByLoginId(String loginId);
    Boolean existsByLoginId(String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동시성 방지용 (같은 사용자가 2번 complete 호출 경우)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
