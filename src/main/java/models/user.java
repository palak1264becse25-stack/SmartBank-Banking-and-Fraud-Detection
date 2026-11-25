package models;

/** Customer model retained from the original SmartBank account-management module. */
public class user {
    private final int userId;
    private final String fullName;
    private final String email;
    private final String password;
    private final String phoneNumber;

    public user(int userId, String fullName, String email, String password, String phoneNumber) {
        if (userId < 0) throw new IllegalArgumentException("User ID cannot be negative");
        this.userId = userId;
        this.fullName = requireText(fullName, "Full name");
        this.email = requireText(email, "Email");
        this.password = requireText(password, "Password");
        this.phoneNumber = requireText(phoneNumber, "Phone number");
    }

    public int getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getPhoneNumber() { return phoneNumber; }

    public boolean verifyPassword(String candidate) {
        return candidate != null && password.equals(candidate);
    }

    public void displayUserDetails() {
        System.out.println("User ID: " + userId);
        System.out.println("Name: " + fullName);
        System.out.println("Email: " + email);
        System.out.println("Phone: " + phoneNumber);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
