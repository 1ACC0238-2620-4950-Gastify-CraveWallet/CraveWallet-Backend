package pe.edu.upc.gastify.cravewallet.premium.application;

public class PremiumFailure extends RuntimeException {
    private final int status;

    public PremiumFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static final class CheckoutRejected extends PremiumFailure {
        public CheckoutRejected() {
            super(503, "Mercado Pago rechazó la creación del checkout.");
        }
    }
}
