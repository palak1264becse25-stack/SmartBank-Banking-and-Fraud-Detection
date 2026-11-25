import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * DatabaseConnection
 * 
 * Provides centralized database connection management and automatic table
 * initialization for the Core Banking Operations (Member 3).
 *
 * Supports configuration via 'db.properties' file or defaults to local MySQL.
 */
public class DatabaseConnection {

    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/bank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static String driver = DEFAULT_DRIVER;
    private static String url = DEFAULT_URL;
    private static String user = DEFAULT_USER;
    private static String password = DEFAULT_PASSWORD;

    static {
        loadProperties();
    }

    /**
     * Loads database credentials from 'db.properties' if present in the working directory.
     */
    private static void loadProperties() {
        File propFile = new File("db.properties");
        if (!propFile.exists()) {
            try (InputStream resource = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
                if (resource != null) {
                    Properties props = new Properties();
                    props.load(resource);
                    applyProperties(props);
                }
            } catch (IOException e) {
                System.err.println("[WARN] Could not read bundled db.properties.");
            }
        }
        if (propFile.exists()) {
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(propFile)) {
                props.load(input);
                applyProperties(props);
            } catch (IOException e) {
                System.err.println("[WARN] Could not read db.properties, using default configurations.");
            }
        }
    }


    private static void applyProperties(Properties props) {
        if (props.getProperty("db.driver") != null) driver = props.getProperty("db.driver").trim();
        if (props.getProperty("db.url") != null) url = props.getProperty("db.url").trim();
        if (props.getProperty("db.user") != null) user = props.getProperty("db.user").trim();
        if (props.getProperty("db.password") != null) password = props.getProperty("db.password").trim();
    }

    /** Creates the SQL account corresponding to a newly registered application user. */
    public static void createAccountIfMissing(String accountNumber, String holderName) throws SQLException {
        String sql = "INSERT INTO accounts (account_number, account_holder_name, account_type, balance, status) "
                + "VALUES (?, ?, 'Savings', 0.00, 'ACTIVE') ON DUPLICATE KEY UPDATE account_number = VALUES(account_number)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ps.setString(2, holderName);
            ps.executeUpdate();
        }
    }

    /**
     * Obtains a new database connection.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            System.err.println("\n[ERROR] JDBC Driver class not found: " + driver);
            System.err.println("[TIP] Ensure MySQL Connector/J JAR is included in the classpath (e.g. -cp .:mysql-connector-j-8.x.x.jar)\n");
            throw new SQLException("JDBC Driver not found: " + driver, e);
        }

        Connection conn = DriverManager.getConnection(url, user, password);
        initializeDatabase(conn);
        return conn;
    }

    /**
     * Automatically creates 'accounts' and 'transactions' tables if they don't exist,
     * and seeds initial sample accounts for testing.
     *
     * @param conn active database connection
     */
    public static void initializeDatabase(Connection conn) {
        String createAccountsTableSQL = "CREATE TABLE IF NOT EXISTS accounts ("
                + "account_number VARCHAR(20) PRIMARY KEY, "
                + "account_holder_name VARCHAR(100) NOT NULL, "
                + "user_id INT NULL, "
                + "account_type VARCHAR(50) NOT NULL DEFAULT 'Savings', "
                + "balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00, "
                + "interest_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00, "
                + "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createTransactionsTableSQL = "CREATE TABLE IF NOT EXISTS transactions ("
                + "transaction_id VARCHAR(50) PRIMARY KEY, "
                + "account_number VARCHAR(20) NOT NULL, "
                + "transaction_type VARCHAR(30) NOT NULL, "
                + "amount DECIMAL(15, 2) NOT NULL, "
                + "date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "status VARCHAR(20) NOT NULL, "
                + "reference_account VARCHAR(20) NULL, "
                + "description VARCHAR(255) NULL, "
                + "FOREIGN KEY (account_number) REFERENCES accounts(account_number) ON DELETE CASCADE"
                + ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createAccountsTableSQL);
            stmt.execute(createTransactionsTableSQL);
            ensureColumn(conn, "accounts", "user_id", "INT NULL");
            ensureColumn(conn, "accounts", "interest_rate", "DECIMAL(5, 2) NOT NULL DEFAULT 0.00");

            // Check if accounts table is empty, seed initial test accounts if so
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedInitialAccounts(conn);
                }
            }
        } catch (SQLException e) {
            // Log but don't prevent existing schema usage if tables already present or syntax differs
            // (e.g. SQLite vs MySQL foreign key subtleties)
        }
    }

    /** Adds compatibility columns when upgrading an existing SmartBank database. */
    private static void ensureColumn(Connection conn, String table, String column, String definition) throws SQLException {
        String check = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement alter = conn.createStatement()) {
                        alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    }
                }
            }
        }
    }

    /**
     * Seeds sample test accounts if the database is newly initialized.
     */
    private static void seedInitialAccounts(Connection conn) {
        String seedSQL = "INSERT INTO accounts (account_number, account_holder_name, account_type, balance, status) VALUES "
                + "('1001', 'Alice Smith', 'Savings', 5000.00, 'ACTIVE'), "
                + "('1002', 'Bob Johnson', 'Checking', 2500.00, 'ACTIVE'), "
                + "('1003', 'Charlie Davis', 'Savings', 1200.00, 'ACTIVE');";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(seedSQL);
            System.out.println("[INFO] Seeded default test accounts: 1001 ($5000), 1002 ($2500), 1003 ($1200).");
        } catch (SQLException ignored) {
        }
    }

    /**
     * Utility method to safely close JDBC resources.
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Standalone main method to test database connectivity.
     */
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("          DATABASE CONNECTION TESTER             ");
        System.out.println("=================================================");
        System.out.println("Driver  : " + driver);
        System.out.println("URL     : " + url);
        System.out.println("User    : " + user);
        System.out.println("-------------------------------------------------");
        System.out.println("Attempting connection...");

        try (Connection conn = getConnection()) {
            System.out.println("[SUCCESS] Successfully connected to the database!");
            System.out.println("[SUCCESS] Database product: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            System.err.println("[FAILED] Could not connect to database.");
            System.err.println("Details: " + e.getMessage());
            System.out.println("\nTroubleshooting tips:");
            System.out.println("1. Verify MySQL server is running.");
            System.out.println("2. Create database 'bank_db' in MySQL: CREATE DATABASE bank_db;");
            System.out.println("3. Verify username and password in 'db.properties'.");
            System.out.println("4. Ensure MySQL Connector/J is on the classpath when running.");
        }
    }
}
