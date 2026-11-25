
import dao.AccountDAO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Core authentication service: user store, validation, login-state (session)
 * management and access control.
 *
 * Other modules should call requireLogin() before any banking operation and
 * getCurrentUser() to find out which account the operation belongs to.
 */
public class UserAuthentication {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,20}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z .'-]{1,49}$");

    private final Map<String, User> usersByUsername = new HashMap<>();
    private final Map<String, User> usersByEmail = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    // Login state (session)
    private User currentUser = null;

    // ---------------------------------------------------------------
    // Field validators (return null when valid, otherwise an error message)
    // ---------------------------------------------------------------

    public String validateFullName(String name) {
        if (isBlank(name)) return "Full name is required.";
        if (!NAME_PATTERN.matcher(name.trim()).matches())
            return "Full name must be 2-50 characters and contain only letters, spaces, . ' or -.";
        return null;
    }

    public String validateEmail(String email) {
        if (isBlank(email)) return "Email address is required.";
        if (!EMAIL_PATTERN.matcher(email.trim()).matches())
            return "Invalid email format (example: name@example.com).";
        if (usersByEmail.containsKey(normalizeEmail(email)))
            return "This email address is already registered.";
        return null;
    }

    public String validatePhone(String phone) {
        if (isBlank(phone)) return "Phone number is required.";
        if (!PHONE_PATTERN.matcher(phone.trim()).matches())
            return "Phone number must be a valid 10-digit number starting with 6-9.";
        return null;
    }

    public String validateUsername(String username) {
        if (isBlank(username)) return "Username is required.";
        if (!USERNAME_PATTERN.matcher(username.trim()).matches())
            return "Username must be 4-20 characters: letters, digits or underscore only.";
        if (usersByUsername.containsKey(normalizeUsername(username)))
            return "This username is already taken.";
        return null;
    }

    public String validatePassword(String password) {
        if (isBlank(password)) return "Password is required.";
        if (password.length() < 8) return "Password must be at least 8 characters long.";
        if (!password.matches(".*[A-Z].*")) return "Password must contain an uppercase letter.";
        if (!password.matches(".*[a-z].*")) return "Password must contain a lowercase letter.";
        if (!password.matches(".*[0-9].*")) return "Password must contain a digit.";
        if (!password.matches(".*[^A-Za-z0-9].*")) return "Password must contain a special character.";
        return null;
    }

    public String validateConfirmPassword(String password, String confirm) {
        if (isBlank(confirm)) return "Please confirm your password.";
        if (!confirm.equals(password)) return "Password and confirm password do not match.";
        return null;
    }

    // ---------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------

    /**
     * Registers a new user after validating every field.
     * @return the newly created user
     * @throws AuthException with a user-friendly message if anything is invalid
     */
    public synchronized User register(String fullName, String email, String phone,
                                      String username, String password,
                                      String confirmPassword) throws AuthException {
        String error;
        if ((error = validateFullName(fullName)) != null) throw new AuthException(error);
        if ((error = validateEmail(email)) != null) throw new AuthException(error);
        if ((error = validatePhone(phone)) != null) throw new AuthException(error);
        if ((error = validateUsername(username)) != null) throw new AuthException(error);
        if ((error = validatePassword(password)) != null) throw new AuthException(error);
        if ((error = validateConfirmPassword(password, confirmPassword)) != null)
            throw new AuthException(error);

        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        String accountNumber = generateUniqueAccountNumber();

        User user = new User(fullName.trim(), email.trim(), phone.trim(), username.trim(),
                hash, salt, accountNumber, 0.0);
        try {
            if (!AccountDAO.createAccount(accountNumber, fullName.trim())) {
                throw new java.sql.SQLException("Account number already exists.");
            }
        } catch (java.sql.SQLException e) {
            throw new AuthException("Could not create your bank account in MySQL. Check the database connection and try again. Details: " + e.getMessage());
        }
        usersByUsername.put(normalizeUsername(username), user);
        usersByEmail.put(normalizeEmail(email), user);
        return user;
    }

    // ---------------------------------------------------------------
    // Login / logout / session
    // ---------------------------------------------------------------

    /**
     * Logs in using username OR email plus password.
     * A generic message is used on failure so attackers cannot tell
     * whether the username/email exists.
     */
    public synchronized User login(String usernameOrEmail, String password) throws AuthException {
        if (isBlank(usernameOrEmail) || isBlank(password))
            throw new AuthException("Username/email and password are required.");

        String key = usernameOrEmail.trim();
        User user = key.contains("@")
                ? usersByEmail.get(normalizeEmail(key))
                : usersByUsername.get(normalizeUsername(key));

        if (user == null || !hashPassword(password, user.getSalt()).equals(user.getPasswordHash()))
            throw new AuthException("Incorrect username/email or password.");

        currentUser = user;
        return user;
    }

    public synchronized void logout() {
        currentUser = null;
    }

    public synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    /** The user associated with the current session (null if not logged in). */
    public synchronized User getCurrentUser() {
        return currentUser;
    }

    /**
     * Access control gate. Call this at the start of EVERY banking operation.
     * @throws AuthException if nobody is logged in
     */
    public synchronized User requireLogin() throws AuthException {
        if (currentUser == null)
            throw new AuthException("Access denied. Please log in to perform banking operations.");
        return currentUser;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Shows only the last 4 digits, e.g. XXXXXXXX1234. */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accountNumber.length() - 4; i++) sb.append('X');
        return sb.append(accountNumber.substring(accountNumber.length() - 4)).toString();
    }

    private String generateUniqueAccountNumber() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            sb.append(random.nextInt(9) + 1); // no leading zero
            for (int i = 0; i < 11; i++) sb.append(random.nextInt(10));
            String candidate = sb.toString();
            boolean taken = false;
            for (User u : usersByUsername.values()) {
                if (u.getAccountNumber().equals(candidate)) { taken = true; break; }
            }
            if (!taken) return candidate;
        }
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}
