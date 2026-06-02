# 11 — 3 mixed-format inputs → JSON/JSCH output: Payroll (CLEAN / SMALL / LARGE)

**Formats exercised:** input1 **OData (osch/EDMX)**, input2 **YAML (jsch)**, input3 **CSV
(tsch)**; output **JSON (jsch)**. One shared input set, three output contracts of
escalating gap. The OData properties are PascalCase (`EmployeeID`…) and match the
camelCase targets case-insensitively — coverage normalizes names.

- **Inputs (shared by all three outputs):**
  - `input1.employee.edmx` (odata) — Employee master · sample `sample.employee.json`
  - `input2.config.schema.json` (yaml) — Payroll config · sample `sample.config.yaml`
  - `input3.timesheet.tsch.json` (csv) — Hours per period · sample `sample.timesheet.csv`
- **Outputs:** `output.clean.*` · `output.small-gap.*` · `output.large-gap.*`

## Verified coverage

**CLEAN** — `output.clean.payroll-record.schema.json` → **11 direct, delta none**
(`employeeId/firstName/lastName/departmentId/hireDate` ← Employee; `payrollCycle/currency/region`
← Config; `hoursWorked/overtimeHours/periodId` ← Timesheet).

**SMALL GAP** — `output.small-gap.payroll-record.schema.json` → **10 direct, 2 gap**
delta: `grossPay` (hours × rate — no rate in inputs), `payDate` (pay calendar).

**LARGE GAP** — `output.large-gap.payslip.schema.json` → **4 direct, 10 gap**
- direct: `employeeId`, `firstName`, `lastName`, `currency`
- delta (10): `grossPay, netPay, taxAmount, socialSecurity, pensionContribution,
  bankAccount, iban, bic, payDate, costCenter` — all from a payroll engine / bank master.

> Demonstrates coverage across **OData + YAML/JSON-Schema + CSV-table** inputs feeding a
> single **jsch** contract, including OData PascalCase → camelCase name matching.
