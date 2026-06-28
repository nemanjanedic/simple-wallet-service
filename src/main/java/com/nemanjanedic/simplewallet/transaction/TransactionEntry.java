package com.nemanjanedic.simplewallet.transaction;

import com.nemanjanedic.simplewallet.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_entries")
public class TransactionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String referenceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionEntry() {
        // Required by JPA
    }

    public TransactionEntry(
            Account account,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description,
            String referenceId
    ) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.referenceId = referenceId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
