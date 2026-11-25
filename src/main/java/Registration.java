import java.util.Scanner;

/**
 * Console UI for user registration.
 * Each field is validated immediately and re-asked until it is valid.
 */
public class Registration {

    private final UserAuthentication auth;
    private final Scanner scanner;

    public Registration(UserAuthentication auth, Scanner scanner) {
        this.auth = auth;
        this.scanner = scanner;
    }

    /** @return true if registration succeeded */
    public boolean start() {
        System.out.println("\n========== USER REGISTRATION ==========");
        try {
            String fullName = askUntilValid("Full name", auth::validateFullName);
            String email = askUntilValid("Email address", auth::validateEmail);
            String phone = askUntilValid("Phone number (10 digits)", auth::validatePhone);
            String username = askUntilValid("Username", auth::validateUsername);

            String password;
            String confirm;
            while (true) {
                password = askUntilValid("Password", auth::validatePassword);
                System.out.print("Confirm password: ");
                confirm = scanner.nextLine();
                String err = auth.validateConfirmPassword(password, confirm);
                if (err == null) break;
                System.out.println("  [Error] " + err);
            }

            User user = auth.register(fullName, email, phone, username, password, confirm);
            System.out.println("\n[Success] Registration successful! Welcome, " + user.getFullName() + ".");
            System.out.println("Your account number is: " + user.getAccountNumber());
            System.out.println("Please log in to continue.");
            return true;
        } catch (AuthException e) {
            // Safety net: e.g. a duplicate created between validation and save
            System.out.println("\n[Error] Registration failed: " + e.getMessage());
            return false;
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n[Error] Input closed. Registration cancelled.");
            return false;
        }
    }

    private String askUntilValid(String label, java.util.function.Function<String, String> validator) {
        while (true) {
            System.out.print(label + ": ");
            String value = scanner.nextLine();
            String error = validator.apply(value);
            if (error == null) return value.trim();
            System.out.println("  [Error] " + error);
        }
    }
}
