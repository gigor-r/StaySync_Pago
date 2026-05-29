package com.staysync.pagos.repository;

import com.staysync.pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByReservaId(Long reservaId);
    Optional<Pago> findByReferencia(String referencia);
    boolean existsByReferencia(String referencia);
    Optional<Pago> findByGatewayId(String gatewayId);
    boolean existsByGatewayId(String gatewayId);
}
