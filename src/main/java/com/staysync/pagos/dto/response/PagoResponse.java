package com.staysync.pagos.dto.response;

import com.staysync.pagos.model.Pago.EstadoPago;
import com.staysync.pagos.model.Pago.MetodoPago;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PagoResponse {
    private Long id;
    private String referencia;
    private Long reservaId;
    private Long usuarioId;
    private BigDecimal monto;
    private String moneda;
    private MetodoPago metodoPago;
    private EstadoPago estado;
    private String gatewayId;
    private String descripcion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
