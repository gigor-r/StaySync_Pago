package com.staysync.pagos.exception;

public class PagoNotFoundException extends RuntimeException {
    public PagoNotFoundException(Long id) { super("Pago no encontrado con ID: " + id); }
    public PagoNotFoundException(String ref) { super("Pago no encontrado con referencia: " + ref); }
}
