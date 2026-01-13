package com.paulfernandosr.possystembackend.sale.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.user.domain.User;

import java.util.Optional;

public interface SaleSessionRepository {
    void create(SaleSession saleSession);

    Page<SaleSession> findPage(String query, Pageable pageable);

    Optional<SaleSession> findById(Long saleSessionId);

    void update(SaleSession saleSession);

    void closeById(Long saleSessionId);

    boolean existsOpenSession(SaleSession saleSession);

    boolean existsOpenSessionByUser(User user);
}
