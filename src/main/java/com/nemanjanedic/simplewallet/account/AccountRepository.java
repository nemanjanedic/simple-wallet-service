package com.nemanjanedic.simplewallet.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from Account a
            where a.id = :id
            """)
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from Account a
            where a.id in :ids
            order by a.id asc
            """)
    List<Account> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}