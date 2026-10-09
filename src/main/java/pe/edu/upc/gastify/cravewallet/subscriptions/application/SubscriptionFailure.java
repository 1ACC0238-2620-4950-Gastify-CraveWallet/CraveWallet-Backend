package pe.edu.upc.gastify.cravewallet.subscriptions.application;

public class SubscriptionFailure extends RuntimeException {
    private final int status;
    public SubscriptionFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
