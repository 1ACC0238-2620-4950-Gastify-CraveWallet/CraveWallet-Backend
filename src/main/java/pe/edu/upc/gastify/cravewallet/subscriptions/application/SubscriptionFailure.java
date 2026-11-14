package pe.edu.upc.gastify.cravewallet.subscriptions.application;

public class SubscriptionFailure extends RuntimeException {
    private final int status;
    private final String messageKey;

    public SubscriptionFailure(int status, String messageKey, String defaultMessage) {
        super(defaultMessage);
        this.status = status;
        this.messageKey = messageKey;
    }

    public SubscriptionFailure(int status, String message) {
        this(status, message, message);
    }

    public int status() { return status; }
    public String messageKey() { return messageKey; }
}
