/**
 * Thrown for any validation, registration, login or access-control failure.
 * The message is safe to show directly to the user.
 */
public class AuthException extends Exception {
    public AuthException(String message) {
        super(message);
    }
}
