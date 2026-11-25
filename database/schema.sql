-- ====================================================================
-- Banking Operations Database Schema (Member 3)
-- Database: bank_db
-- ====================================================================

CREATE DATABASE IF NOT EXISTS bank_db;
USE bank_db;

-- Drop existing tables if re-creating
-- DROP TABLE IF EXISTS transactions;
-- DROP TABLE IF EXISTS accounts;

-- 1. Accounts Table
CREATE TABLE IF NOT EXISTS accounts (
    account_number VARCHAR(20) PRIMARY KEY,
    account_holder_name VARCHAR(100) NOT NULL,
    user_id INT NULL,
    account_type VARCHAR(50) NOT NULL DEFAULT 'Savings', -- e.g., 'Savings', 'Checking', 'Current'
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    interest_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',       -- e.g., 'ACTIVE', 'INACTIVE', 'SUSPENDED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id VARCHAR(50) PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,              -- 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT'
    amount DECIMAL(15, 2) NOT NULL,
    date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,                        -- 'SUCCESS', 'FAILED'
    reference_account VARCHAR(20) NULL,                 -- Counterparty account (e.g. recipient/sender in transfer)
    description VARCHAR(255) NULL,
    FOREIGN KEY (account_number) REFERENCES accounts(account_number) ON DELETE CASCADE
);

-- 3. Sample Seed Accounts for Testing
INSERT INTO accounts (account_number, account_holder_name, account_type, balance, status)
VALUES 
    ('1001', 'Alice Smith', 'Savings', 5000.00, 'ACTIVE'),
    ('1002', 'Bob Johnson', 'Checking', 2500.00, 'ACTIVE'),
    ('1003', 'Charlie Davis', 'Savings', 1200.00, 'ACTIVE')
ON DUPLICATE KEY UPDATE 
    account_holder_name = VALUES(account_holder_name),
    balance = VALUES(balance);
