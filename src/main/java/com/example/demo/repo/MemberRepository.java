package com.example.demo.repo;

import com.example.demo.model.Member;
import java.util.Optional;

public interface MemberRepository {
    Optional<Member> findById(String id);
    void save(Member member);
}
