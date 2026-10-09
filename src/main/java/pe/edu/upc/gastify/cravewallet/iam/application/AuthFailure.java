package pe.edu.upc.gastify.cravewallet.iam.application;

public class AuthFailure extends RuntimeException {
    private final int status;
    public AuthFailure(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
}
