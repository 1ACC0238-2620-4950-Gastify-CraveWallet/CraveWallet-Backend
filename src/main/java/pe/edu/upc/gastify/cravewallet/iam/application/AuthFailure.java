package pe.edu.upc.gastify.cravewallet.iam.application;

public class AuthFailure extends RuntimeException {
    private final int status;
    private final String messageKey;

    public AuthFailure(int status, String messageKey, String defaultMessage) {
        super(defaultMessage);
        this.status = status;
        this.messageKey = messageKey;
    }

    public AuthFailure(int status, String message) {
        this(status, message, message);
    }

    public int status() { return status; }
    public String messageKey() { return messageKey; }
}
