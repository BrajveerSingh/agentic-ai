-- =============================================
-- Audit Trail Agent - Database Initialization
-- =============================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================
-- Tables (will be managed by JPA in later phases)
-- =============================================

-- Placeholder for Phase 2
-- Tables will be created by Hibernate with ddl-auto

-- =============================================
-- Seed Data for Development
-- =============================================

-- Sample policy documents (for testing)
CREATE TABLE IF NOT EXISTS policy_document (
    policy_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_name VARCHAR(200) NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    content TEXT,
    version VARCHAR(20) DEFAULT '1.0',
    effective_date DATE DEFAULT CURRENT_DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO policy_document (policy_name, policy_type, content) VALUES
('Minimum Credit Score Policy', 'CREDIT', 'Minimum credit score for loan approval is 650'),
('Maximum Loan Amount Policy', 'AMOUNT', 'Maximum unsecured loan amount is $500,000'),
('KYC Verification Policy', 'KYC', 'KYC must be completed within the last 12 months');

-- =============================================
-- Grants
-- =============================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ata;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ata;

