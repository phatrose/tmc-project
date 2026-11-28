-- PHỤ TRÁCH: Task 1; Task 8 nối schema với entity trong bản tích hợp.
CREATE TABLE departments (id varchar(36) PRIMARY KEY, factory_id varchar(36) NOT NULL, name varchar(200) NOT NULL, version integer NOT NULL);
CREATE TABLE employees (id varchar(36) PRIMARY KEY, employee_code varchar(64) UNIQUE NOT NULL, display_name varchar(200) NOT NULL, factory_id varchar(36) NOT NULL, department_id varchar(36), active boolean NOT NULL, version integer NOT NULL, updated_at timestamptz NOT NULL, anonymized_at timestamptz);
CREATE TABLE user_accounts (id varchar(36) PRIMARY KEY, username varchar(64) UNIQUE NOT NULL, password_hash varchar(255) NOT NULL, employee_id varchar(36), scope varchar(50) NOT NULL, active boolean NOT NULL, version integer NOT NULL, created_at timestamptz NOT NULL);
CREATE TABLE user_account_roles (account_id varchar(36) REFERENCES user_accounts(id), role varchar(255) NOT NULL);
CREATE TABLE user_account_factories (account_id varchar(36) REFERENCES user_accounts(id), factory_id varchar(255) NOT NULL);
CREATE TABLE user_account_departments (account_id varchar(36) REFERENCES user_accounts(id), department_id varchar(255) NOT NULL);
CREATE TABLE policy_assignments (id varchar(36) PRIMARY KEY, employee_id varchar(36) NOT NULL, valid_from timestamptz NOT NULL, valid_until timestamptz, version integer NOT NULL);
CREATE TABLE policy_assignment_gates (assignment_id varchar(36) REFERENCES policy_assignments(id), gate_id varchar(255) NOT NULL);
CREATE TABLE policy_assignment_shifts (assignment_id varchar(36) REFERENCES policy_assignments(id), shift_id varchar(255) NOT NULL);
