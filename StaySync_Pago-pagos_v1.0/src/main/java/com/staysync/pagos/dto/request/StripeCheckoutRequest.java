package com.staysync.pagos.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StripeCheckoutRequest {

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "1", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotBlank(message = "El título de la reserva es obligatorio")
    private String tituloReserva;

    private Long reservaId;
}
