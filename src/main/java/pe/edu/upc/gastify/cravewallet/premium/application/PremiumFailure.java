package pe.edu.upc.gastify.cravewallet.premium.application;

public class PremiumFailure extends RuntimeException {
    private final int status;
    private final String messageKey;

    public PremiumFailure(int status, String messageKey, String defaultMessage) {
        super(defaultMessage);
        this.status = status;
        this.messageKey = messageKey;
    }

    public PremiumFailure(int status, String message) {
        this(status, message, message);
    }

    public int status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    public static final class CheckoutRejected extends PremiumFailure {
        public CheckoutRejected() {
            super(503, "premium.checkout.rejected", "Mercado Pago rejected the checkout creation.");
        }
    }
}
