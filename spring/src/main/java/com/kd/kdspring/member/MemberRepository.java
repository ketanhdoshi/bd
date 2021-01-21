package com.kd.kdspring.member;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kd.kdspring.model.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Member findByEmail(String email);
}