package com.staysync.pagos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StripeConfirmarRequest {
    @NotBlank
    private String sessionId;
    @NotNull
    private Long usuarioId;
}
