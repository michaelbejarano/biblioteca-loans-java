package com.example.demo.integration;

import com.example.demo.model.Book;
import com.example.demo.model.Member;
import com.example.demo.repo.mem.InMemoryBookRepository;
import com.example.demo.repo.mem.InMemoryLoanRepository;
import com.example.demo.repo.mem.InMemoryMemberRepository;
import com.example.demo.service.DomainException;
import com.example.demo.service.LoanService;
import com.example.demo.util.ClockProvider;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

class LoanFlowIT {

    InMemoryBookRepository bookRepo;
    InMemoryMemberRepository memberRepo;
    InMemoryLoanRepository loanRepo;
    LoanService service;

    @BeforeEach
    void setup() {
        // Fijamos el reloj para pruebas deterministas (2025-11-01)
        Clock fixed = Clock.fixed(LocalDate.of(2025, 11, 1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        ClockProvider.setFixedClock(fixed);

        bookRepo = new InMemoryBookRepository();
        memberRepo = new InMemoryMemberRepository();
        loanRepo = new InMemoryLoanRepository();

        // semillas
        bookRepo.save(new Book("ISBN-001", "Clean Code"));
        bookRepo.save(new Book("ISBN-002", "Domain-Driven Design"));
        bookRepo.save(new Book("ISBN-003", "Refactoring"));
        memberRepo.save(new Member("M-01", "Ana"));
        memberRepo.save(new Member("M-02", "Luis"));

        service = new LoanService(bookRepo, memberRepo, loanRepo);
    }

    @AfterEach
    void tearDown() {
        ClockProvider.reset();
    }

    @Test
    void flujoExitoso_prestarYDevolverSinMulta() {
        var loan = service.loanBook("ISBN-001", "M-01");
        assertNotNull(loan.getId());
        assertFalse(bookRepo.findByIsbn("ISBN-001").orElseThrow().isAvailable());

        // avanzar 5 días (aún antes del vencimiento 14 días)
        Clock newClock = Clock.offset(ClockProvider.getClock(),
                java.time.Duration.ofDays(5));
        ClockProvider.setFixedClock(newClock);

        var fee = service.returnBook(loan.getId());
        assertEquals(BigDecimal.ZERO, fee);

        assertTrue(bookRepo.findByIsbn("ISBN-001").orElseThrow().isAvailable());
        assertEquals(0, memberRepo.findById("M-01").orElseThrow().getActiveLoans());
    }

    @Test
    void noPermitePrestarLibroYaPrestado_R1() {
        var loan = service.loanBook("ISBN-002", "M-01");
        assertThrows(DomainException.class, () -> service.loanBook("ISBN-002", "M-02"));
    }

    @Test
    void noPermiteMasDeTresPrestamosActivos_R2() {
        service.loanBook("ISBN-001", "M-01");
        service.loanBook("ISBN-002", "M-01");
        service.loanBook("ISBN-003", "M-01");
        // cuarto préstamo debe fallar
           bookRepo.save(new Book("ISBN-004", "Patterns"));
        assertThrows(DomainException.class, () -> service.loanBook("ISBN-004", "M-01"));
    }

    @Test
    void noPermitePrestarSiHayMora_R3() {
        var loan = service.loanBook("ISBN-001", "M-02");

        // avanzar 20 días -> vence (14) y queda en mora
        Clock lateClock = Clock.offset(ClockProvider.getClock(),
                java.time.Duration.ofDays(20));
        ClockProvider.setFixedClock(lateClock);

        // intentar otro préstamo debe fallar
        assertThrows(DomainException.class, () -> service.loanBook("ISBN-002", "M-02"));
    }

    @Test
    void calculaMultaEnDevolucionTardia_R5() {
        var loan = service.loanBook("ISBN-003", "M-01");
        // vencer + 3 días
        Clock lateClock = Clock.offset(ClockProvider.getClock(),
                java.time.Duration.ofDays(17));
        ClockProvider.setFixedClock(lateClock);

        var fee = service.returnBook(loan.getId());
        assertEquals(new BigDecimal("4.5"), fee); // 3 días * 1.5
    }
}

