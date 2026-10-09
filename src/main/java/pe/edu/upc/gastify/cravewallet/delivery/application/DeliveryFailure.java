package pe.edu.upc.gastify.cravewallet.delivery.application;

public class DeliveryFailure extends RuntimeException {
    private final int status;
    public DeliveryFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
