package com.nemanjanedic.simplewallet.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {

    @Query("""
            select t
            from TransactionEntry t
            where t.account.id = :accountId
            order by t.createdAt desc, t.id desc
            """)
    Page<TransactionEntry> findByAccountIdOrderByCreatedAtDesc(
            @Param("accountId") Long accountId,
            Pageable pageable
    );
}