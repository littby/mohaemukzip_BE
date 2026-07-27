package com.mohaemukzip.mohaemukzip_be.domain.member.repository;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
    Boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Modifying
    @Query(value = "DELETE FROM member_cook_histories WHERE member_id = :memberId", nativeQuery = true)
    void deleteCookHistoriesByMemberId(@Param("memberId") Long memberId);
}
