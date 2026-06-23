package com.staysync.pagos.controller;

import com.staysync.pagos.dto.request.StripeCheckoutRequest;
import com.staysync.pagos.dto.request.StripeConfirmarRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.dto.response.StripeCheckoutResponse;
import com.staysync.pagos.service.StripeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagos/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe", description = "Integración con Stripe Checkout — modo Test")
public class StripeController {

    private final StripeService stripeService;

    @PostMapping("/checkout")
    @Operation(summary = "Crear sesión de pago en Stripe Checkout")
    public ResponseEntity<StripeCheckoutResponse> crearCheckout(
            @Valid @RequestBody StripeCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stripeService.crearCheckout(request));
    }

    @PostMapping("/confirmar")
    @Operation(summary = "Confirmar pago Stripe tras redirección exitosa")
    public ResponseEntity<PagoResponse> confirmarPago(
            @Valid @RequestBody StripeConfirmarRequest request) {
        return ResponseEntity.ok(stripeService.confirmarPago(request));
    }
}
