package com.staysync.pagos.exception;

public class PagoFallidoException extends RuntimeException {
    public PagoFallidoException(String motivo) { super("Pago fallido: " + motivo); }
}
