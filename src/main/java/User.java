/**
 * Simple user model. Passwords are never stored in plain text:
 * only a salted SHA-256 hash is kept.
 */
public class User {
    private final String fullName;
    private final String email;
    private final String phone;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String accountNumber;
    private double balance;

    public User(String fullName, String email, String phone, String username,
                String passwordHash, String salt, String accountNumber, double balance) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    public String getFullName()      { return fullName; }
    public String getEmail()         { return email; }
    public String getPhone()         { return phone; }
    public String getUsername()      { return username; }
    public String getPasswordHash()  { return passwordHash; }
    public String getSalt()          { return salt; }
    public String getAccountNumber() { return accountNumber; }
    public double getBalance()       { return balance; }

    // Banking Operations module (other team member) can use this to update balance.
    public void setBalance(double balance) { this.balance = balance; }
}
