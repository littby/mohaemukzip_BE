package com.mohaemukzip.mohaemukzip_be.domain.mission.repository;

import com.mohaemukzip.mohaemukzip_be.domain.mission.entity.MemberMission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;


public interface MemberMissionRepository extends JpaRepository<MemberMission,Long> {

    // 오늘의 퀘스트 할당 조회
    @EntityGraph(attributePaths = {"mission"})
    Optional<MemberMission> findByMemberIdAndAssignedDate(Long memberId, LocalDate assignedDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"mission"})
    @Query("SELECT mm FROM MemberMission mm WHERE mm.member.id = :memberId AND mm.assignedDate = :date")
    Optional<MemberMission> findByMemberIdAndAssignedDateForUpdate(
            @Param("memberId") Long memberId,
            @Param("date") LocalDate date
    );

    void deleteAllByMember_Id(Long memberId);
}
