package com.staysync.pagos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "pagos", indexes = {
        @Index(name = "idx_reserva_id", columnList = "reserva_id"),
        @Index(name = "idx_usuario_id", columnList = "usuario_id"),
        @Index(name = "idx_estado",     columnList = "estado"),
        @Index(name = "idx_referencia", columnList = "referencia")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String referencia;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String moneda = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(name = "gateway_id", length = 255)
    private String gatewayId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_respuesta", columnDefinition = "json")
    private Map<String, Object> gatewayRespuesta;

    @Column(length = 255)
    private String descripcion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum MetodoPago {
        TARJETA_CREDITO, TARJETA_DEBITO, TRANSFERENCIA, EFECTIVO, PAYPAL
    }

    public enum EstadoPago {
        PENDIENTE, PROCESANDO, COMPLETADO, FALLIDO, REEMBOLSADO, PARCIALMENTE_REEMBOLSADO
    }
}
