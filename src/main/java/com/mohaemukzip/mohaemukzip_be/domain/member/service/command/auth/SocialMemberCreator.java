package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SocialMemberCreator {

    private final MemberRepository memberRepository;

    // 중복 가입으로 저장 실패해도, 로그인 트랜잭션은 영향받지 않도록 새 트랜잭션에 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member create(Member newMember) {
        return memberRepository.saveAndFlush(newMember);
    }
}
