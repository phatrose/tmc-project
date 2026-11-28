# PRODUCT.md — TMC Access Control & Attendance System

## Product Overview
**TMC** is a QR-based Access Control and Attendance Management System for **TMC Manufacturing Joint Stock Company**, operating two factories in Vietnam: **Bình Dương** (~800 employees) and **Long An** (~600 employees).

## Surface Mode
- **Primary mode:** `Operate` — daily operational workspace for factory access control, shift attendance, payroll closing, and compliance auditing.
- **Priorities:** High scanability, fast task completion, clear role-based visibility (unauthorized features hidden), tabular data clarity, and unmistakable status feedback (`ALLOW` / `DENY`, `PENDING` / `ACKNOWLEDGED` / `FAILED`).

## Users (Actors)
- **Employee (`EMPLOYEE`):** Views personal dynamic 60s ES256 QR code, scans at gate kiosk, views own attendance records and submits adjustment requests.
- **Security Guard (`GUARD`):** Operates gate kiosk and manual/emergency gate override console with re-authentication (60s ES256 action grant) and factory presence monitoring.
- **HR & Supervisor (`HR`, `SUPERVISOR`):** Manages employee profiles, reviews attendance adjustments, runs official period closing (06:30 on the 1st of each month) and exports payroll data before 12:00.
- **Auditor (`AUDITOR`):** Read-only compliance actor for audit logs, manual overrides, adjustment history, and period closing history. All mutations are strictly blocked.
- **System Administrator (`ADMIN`):** Manages factories, gates/turnstiles, shifts, holidays, system configuration, device status, and Data Subject Requests (DSR).

## Key Workflows
1. **Dynamic QR Pass:** Authenticated employee displays a 60-second single-use ES256 QR code on screen (never via email/SMS).
2. **Hands-Free Gate Kiosk:** Auto-starting webcam QR scanner verifies tokens, blocks replay (`jti`), toggles `IN`/`OUT`, unlocks gate, and records immutable attendance events.
3. **Central Operations:** Employee directory, attendance & adjustments, reporting/audit/payroll export, and system administration.
