package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.term;

import com.mohaemukzip.mohaemukzip_be.domain.member.dto.TermRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.MemberTerm;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Term;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberTermRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.TermRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermCommandServiceImpl implements TermCommandService {
    private final TermRepository termRepository;
    private final MemberTermRepository memberTermRepository;
    private final MemberRepository memberRepository;

    /**
     * 회원가입 시 약관 동의 처리
     */
    @Transactional
    public void createMemberTerms(Member member, List<TermRequestDTO.TermAgreementRequest> termAgreements) {
        validateDuplicateTermIds(termAgreements);
        // 필수 약관 체크
        validateRequiredTerms(termAgreements);


        // 약관 동의 저장
        List<MemberTerm> memberTerms = termAgreements.stream()
                .map(agreement -> {
                    Term term = termRepository.findById(agreement.termId())
                            .orElseThrow(() -> new BusinessException(ErrorStatus.TERM_NOT_FOUND));

                    return MemberTerm.builder()
                            .member(member)
                            .term(term)
                            .termName(term.getTitle())
                            .isAgreed(agreement.isAgreed())
                            .agreedAt(LocalDate.now())
                            .build();
                })
                .toList();

        memberTermRepository.saveAll(memberTerms);
    }
    @Transactional
    public void updateMemberTerms(Long memberId, List<TermRequestDTO.TermAgreementRequest> terms) {
        validateDuplicateTermIds(terms);
        validateRequiredTerms(terms);

        // Member 조회 (영속성 확보)
        Member member = memberRepository.findById(memberId)  // ← MemberRepository 주입!
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        memberTermRepository.deleteAllByMember(member);
        // MemberTerm의 PK가 IDENTITY 전략이라 saveAll()의 INSERT는 즉시 실행되는 반면,
        // deleteAllByMember()의 DELETE는 flush 시점까지 지연된다. flush 없이 바로 재삽입하면
        // (member_id, term_id) 유니크 제약을 위반할 수 있어 명시적으로 flush한다.
        memberTermRepository.flush();
        // createMemberTerms 재사용 (termName, agreedAt 완벽)
        createMemberTerms(member, terms);
        member.agreeToTerms();
    }

    /**
     * 필수 약관 동의 여부 검증
     */
    private void validateRequiredTerms(List<TermRequestDTO.TermAgreementRequest> termAgreements) {
        List<Term> requiredTerms = termRepository.findByIsActiveTrueAndIsRequiredTrue();

        Set<Long> agreedTermIds = termAgreements.stream()
                .filter(agreement -> Boolean.TRUE.equals(agreement.isAgreed()))
                .map(TermRequestDTO.TermAgreementRequest::termId)
                .collect(Collectors.toSet());

        for (Term requiredTerm : requiredTerms) {
            if (!agreedTermIds.contains(requiredTerm.getId())) {
                throw new BusinessException(ErrorStatus.REQUIRED_TERM_NOT_AGREED);
            }
        }

    }

    private void validateDuplicateTermIds(List<TermRequestDTO.TermAgreementRequest> termAgreements) {
        Set<Long> seen = new HashSet<>();
        Set<Long> duplicates = termAgreements.stream()
                .map(TermRequestDTO.TermAgreementRequest::termId)
                .filter(id -> !seen.add(id))  // 이미 있는 id면 중복
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) {
            throw new BusinessException(ErrorStatus.DUPLICATE_TERM_ID); // 새 에러코드 정의 추천
        }
    }
}