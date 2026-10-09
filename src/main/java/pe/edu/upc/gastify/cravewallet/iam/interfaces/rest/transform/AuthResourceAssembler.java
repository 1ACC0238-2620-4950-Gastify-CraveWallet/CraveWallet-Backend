package pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.transform;

import pe.edu.upc.gastify.cravewallet.iam.application.AuthApplicationService.Authentication;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.User;
import pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.resources.AuthResources.*;

public final class AuthResourceAssembler {
    private AuthResourceAssembler() { }
    public static ProfileResource profile(User user) {
        return new ProfileResource(user.id(), user.email(), user.referenceCurrency());
    }
    public static AuthenticationResource authentication(Authentication result) {
        return new AuthenticationResource(result.accessToken(), result.refreshToken(), "Bearer",
                result.expiresAt(), profile(result.user()));
    }
}
