package dao;

import models.Account;
import models.user;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Optional;
import java.util.UUID;

/** JDBC data-access layer for SmartBank accounts. */
public final class AccountDAO {
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static final Properties CONFIG = loadConfig();
    private AccountDAO() { }

    private static Properties loadConfig() {
        Properties p = new Properties();
        p.setProperty("db.driver", DEFAULT_DRIVER);
        p.setProperty("db.url", DEFAULT_URL);
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "");
        try (InputStream in = AccountDAO.class.getResourceAsStream("/db.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Could not load db.properties: " + e.getMessage());
        }
        return p;
    }

    private static Connection connection() throws SQLException {
        try { Class.forName(CONFIG.getProperty("db.driver", DEFAULT_DRIVER)); }
        catch (ClassNotFoundException e) { throw new SQLException("MySQL JDBC driver is missing.", e); }
        return DriverManager.getConnection(CONFIG.getProperty("db.url", DEFAULT_URL),
                CONFIG.getProperty("db.user", "root"), CONFIG.getProperty("db.password", ""));
    }

    /** Original SmartBank API: creates an account for an existing user and returns its generated account number. */
    public static boolean createAccount(int userId, String accountType, BigDecimal initialBalance,
                                        BigDecimal interestRate) throws SQLException {
        if (userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        requireNonNegative(initialBalance, "Initial balance");
        requireNonNegative(interestRate, "Interest rate");
        String accountNumber = "SB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String sql = "INSERT INTO accounts (account_number, user_id, account_holder_name, account_type, balance, interest_rate, status) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setInt(2, userId);
            ps.setString(3, "Customer " + userId);
            ps.setString(4, accountType == null || accountType.isBlank() ? "SAVINGS" : accountType.trim());
            ps.setBigDecimal(5, initialBalance);
            ps.setBigDecimal(6, interestRate);
            return ps.executeUpdate() == 1;
        }
    }

    /** Creates a zero-balance savings account for the current registration flow. */
    public static boolean createAccount(String accountNumber, String holderName) throws SQLException {
        String sql = "INSERT INTO accounts (account_number, account_holder_name, account_type, balance, interest_rate, status) VALUES (?, ?, 'Savings', 0.00, 0.00, 'ACTIVE')";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, holderName);
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean accountExists(String accountNumber) throws SQLException {
        String sql = "SELECT 1 FROM accounts WHERE account_number = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public static BigDecimal getAccountBalance(String accountNumber) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getBigDecimal("balance");
            }
        }
    }

    public static boolean updateBalance(String accountNumber, BigDecimal newBalance) throws SQLException {
        requireNonNegative(newBalance, "Balance");
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setString(2, accountNumber);
            return ps.executeUpdate() == 1;
        }
    }

    public static Optional<Account> findAccount(String accountNumber, user accountHolder) throws SQLException {
        String sql = "SELECT account_number, balance FROM accounts WHERE account_number = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Account(rs.getString("account_number"), accountHolder,
                        rs.getBigDecimal("balance"))) : Optional.empty();
            }
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(field + " cannot be null or negative");
    }
}
