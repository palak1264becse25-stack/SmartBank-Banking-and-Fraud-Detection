import java.util.Scanner;

/**
 * Console UI for user login. Accepts username or email plus password.
 * Allows a limited number of attempts before returning to the main menu.
 */
public class Login {

    private static final int MAX_ATTEMPTS = 3;

    private final UserAuthentication auth;
    private final Scanner scanner;

    public Login(UserAuthentication auth, Scanner scanner) {
        this.auth = auth;
        this.scanner = scanner;
    }

    /** @return true if the user logged in successfully */
    public boolean start() {
        System.out.println("\n============== USER LOGIN ==============");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                System.out.print("Username or email: ");
                String id = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();

                User user = auth.login(id, password);
                System.out.println("\n[Success] Login successful. Welcome back, " + user.getFullName() + "!");
                return true;
            } catch (AuthException e) {
                System.out.println("[Error] " + e.getMessage()
                        + " (Attempt " + attempt + " of " + MAX_ATTEMPTS + ")");
            } catch (java.util.NoSuchElementException e) {
                System.out.println("\n[Error] Input closed. Login cancelled.");
                return false;
            }
        }
        System.out.println("[Error] Too many failed attempts. Returning to main menu.");
        return false;
    }
}
