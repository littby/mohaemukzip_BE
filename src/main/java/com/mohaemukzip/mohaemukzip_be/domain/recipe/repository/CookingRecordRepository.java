package com.mohaemukzip.mohaemukzip_be.domain.recipe.repository;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.CookingRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CookingRecordRepository extends JpaRepository<CookingRecord, Long> {

    // 이달의 집밥 횟수
    @Query("SELECT COUNT(c) FROM CookingRecord c " +
            "WHERE c.member.id = :memberId " +
            "AND YEAR(c.createdAt) = YEAR(CURRENT_DATE) " +
            "AND MONTH(c.createdAt) = MONTH(CURRENT_DATE)")
    Long countMonthlyCooking(@Param("memberId") Long memberId);

    // CookingRecordRepository.java
    @Query("SELECT DISTINCT FUNCTION('DAYOFWEEK', c.createdAt) as dayOfWeek " +
            "FROM CookingRecord c " +
            "WHERE c.member.id = :memberId " +
            "AND c.createdAt BETWEEN :weekStart AND :weekEnd")
    List<Integer> findWeeklyCookingDays(
            @Param("memberId") Long memberId,
            @Param("weekStart") LocalDateTime weekStart,
            @Param("weekEnd") LocalDateTime weekEnd
    );

    //특정 기간 동안 집밥 횟수 조회
    long countByMember_IdAndCreatedAtBetween(Long memberId, LocalDateTime start, LocalDateTime end);

    //특정 기간 동안 요리한 날짜 목록 조회
    @Query("""
    SELECT DISTINCT c.createdAt
    FROM CookingRecord c
    WHERE c.member.id = :memberId
      AND c.createdAt >= :start
      AND c.createdAt < :end
    ORDER BY c.createdAt DESC
""")
    List<LocalDateTime> findDistinctCookingDatesBetween(
            @Param("memberId") Long memberId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 전체 요리 횟수
    long countByMemberId(Long memberId);

    // 평균 rating
    @Query("SELECT AVG(c.rating) FROM CookingRecord c WHERE c.member.id = :memberId")
    Double findAverageRatingByMemberId(@Param("memberId") Long memberId);

    // 월별 요리 횟수 (특정 연도)
    @Query("SELECT MONTH(c.createdAt) as month, COUNT(c) as count " +
            "FROM CookingRecord c " +
            "WHERE c.member.id = :memberId AND YEAR(c.createdAt) = :year " +
            "GROUP BY MONTH(c.createdAt)")
    List<Object[]> countByMemberIdGroupByMonth(@Param("memberId") Long memberId, @Param("year") int year);

    // 특정 월의 요리한 날짜 목록
    @Query("SELECT DISTINCT DAY(c.createdAt) FROM CookingRecord c " +
            "WHERE c.member.id = :memberId AND YEAR(c.createdAt) = :year AND MONTH(c.createdAt) = :month")
    List<Integer> findCookedDaysByMemberIdAndYearMonth(
            @Param("memberId") Long memberId,
            @Param("year") int year,
            @Param("month") int month
    );

    // 특정 날짜의 요리 기록 (Recipe JOIN FETCH)
    @Query("SELECT c FROM CookingRecord c JOIN FETCH c.recipe " +
            "WHERE c.member.id = :memberId AND DATE(c.createdAt) = :date")
    List<CookingRecord> findByMemberIdAndDate(
            @Param("memberId") Long memberId,
            @Param("date") LocalDate date
    );

    // ===== 홈 화면 추천 레시피용 메서드 =====

    // 최근 요리 기록 조회 (카테고리 확인용)
    @Query("SELECT cr FROM CookingRecord cr JOIN FETCH cr.recipe " +
            "WHERE cr.member.id = :memberId ORDER BY cr.createdAt DESC")
    List<CookingRecord> findRecentByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    // 사용자가 요리 기록이 있는지 확인
    boolean existsByMemberId(Long memberId);

    // 특정 시점 이후의 요리 기록 조회 (추천 시 최근 요리 필터링용)
    List<CookingRecord> findAllByMemberIdAndCreatedAtAfter(Long memberId, LocalDateTime startDate);

    void deleteAllByMember_Id(Long memberId);
}

