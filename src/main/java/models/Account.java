package models;

import java.math.BigDecimal;
import java.util.Objects;

/** Account model from the original SmartBank work, integrated with the banking modules. */
public class Account {
    private final String accountNumber;
    private final user accountHolder;
    private BigDecimal balance;

    public Account(String accountNumber, user accountHolder, BigDecimal initialBalance) {
        if (accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("Account number is required");
        this.accountNumber = accountNumber.trim();
        this.accountHolder = Objects.requireNonNull(accountHolder, "Account holder is required");
        this.balance = Objects.requireNonNull(initialBalance, "Initial balance is required");
        if (initialBalance.signum() < 0) throw new IllegalArgumentException("Initial balance cannot be negative");
    }

    public String getAccountNumber() { return accountNumber; }
    public user getAccountHolder() { return accountHolder; }
    public BigDecimal getBalance() { return balance; }
    public String getAccountType() { return "General Account"; }

    public void deposit(BigDecimal amount) {
        validatePositive(amount);
        balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        validatePositive(amount);
        if (balance.compareTo(amount) < 0) throw new IllegalArgumentException("Insufficient balance");
        balance = balance.subtract(amount);
    }

    public void displayAccountDetails() {
        System.out.println("Account number: " + accountNumber);
        System.out.println("Account holder: " + accountHolder.getFullName());
        System.out.println("Account type: " + getAccountType());
        System.out.println("Balance: Rs. " + balance);
    }

    private static void validatePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
    }
}
