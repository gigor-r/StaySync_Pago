package com.staysync.pagos.controller;

import com.staysync.pagos.dto.request.ProcesarPagoRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Procesamiento de pagos, reembolsos y facturación")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping
    @Operation(summary = "Procesar un pago de reserva")
    public ResponseEntity<PagoResponse> procesar(@Valid @RequestBody ProcesarPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.procesar(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pago por ID")
    public ResponseEntity<PagoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pagoService.obtenerPorId(id));
    }

    @GetMapping("/referencia/{referencia}")
    @Operation(summary = "Obtener pago por referencia")
    public ResponseEntity<PagoResponse> obtenerPorReferencia(@PathVariable String referencia) {
        return ResponseEntity.ok(pagoService.obtenerPorReferencia(referencia));
    }

    @GetMapping("/reserva/{reservaId}")
    @Operation(summary = "Listar pagos de una reserva")
    public ResponseEntity<List<PagoResponse>> obtenerPorReserva(@PathVariable Long reservaId) {
        return ResponseEntity.ok(pagoService.obtenerPorReserva(reservaId));
    }

    @PostMapping("/{pagoId}/reembolso")
    @Operation(summary = "Solicitar reembolso de un pago completado")
    public ResponseEntity<PagoResponse> solicitarReembolso(@PathVariable Long pagoId) {
        return ResponseEntity.ok(pagoService.solicitarReembolso(pagoId));
    }
}
