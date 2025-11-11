package com.example.demo.repo.mem;

import com.example.demo.model.Loan;
import com.example.demo.repo.LoanRepository;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryLoanRepository implements LoanRepository {
    private final Map<String, Loan> data = new HashMap<>();

    @Override
    public void save(Loan loan) {
        data.put(loan.getId(), loan);
    }

    @Override
    public Optional<Loan> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<Loan> findActiveByIsbn(String isbn) {
        return data.values().stream()
                .filter(l -> l.getIsbn().equals(isbn) && l.isActive())
                .findFirst();
    }

    @Override
    public List<Loan> findActiveByMember(String memberId) {
        return data.values().stream()
                .filter(l -> l.getMemberId().equals(memberId) && l.isActive())
                .collect(Collectors.toList());
    }
}
