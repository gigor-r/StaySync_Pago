package com.staysync.pagos.dto.request;

import com.staysync.pagos.model.Pago.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Datos para procesar un pago")
public class ProcesarPagoRequest {

    @NotNull(message = "El ID de reserva es obligatorio")
    private Long reservaId;

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    @Schema(example = "200.00")
    private BigDecimal monto;

    @Size(min = 3, max = 3, message = "La moneda debe ser un código ISO de 3 letras")
    @Schema(example = "USD")
    private String moneda = "USD";

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPago metodoPago;

    @Schema(description = "Token o ID del gateway de pago externo")
    private String gatewayToken;

    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String descripcion;
}
