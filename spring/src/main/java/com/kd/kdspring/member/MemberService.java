package com.kd.kdspring.member;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

import com.kd.kdspring.model.Member;

@Service("memberService")
public class MemberService {

    @Autowired
    private MemberRepository memberRepository;
 
    public Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email);
    }
 
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public Member saveMember(Member member) {
        return memberRepository.save(member);
    }

}
