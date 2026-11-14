package pe.edu.upc.gastify.cravewallet.delivery.application;

public class DeliveryFailure extends RuntimeException {
    private final int status;
    private final String messageKey;

    public DeliveryFailure(int status, String messageKey, String defaultMessage) {
        super(defaultMessage);
        this.status = status;
        this.messageKey = messageKey;
    }

    public DeliveryFailure(int status, String message) {
        this(status, message, message);
    }

    public int status() { return status; }
    public String messageKey() { return messageKey; }
}
