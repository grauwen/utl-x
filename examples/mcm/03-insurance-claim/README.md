# 03 — Healthcare: Patient Admission → Insurance Claim (LARGE GAP)

**Scenario.** A hospital `PatientAdmission` must become a professional `InsuranceClaim`
(837-style). The admission carries patient **demographics only**. Everything a claim
needs to be payable — member/payer eligibility, the provider's NPI, an ICD-10 diagnosis
code, and charges — is **absent**. This is the canonical "the source doesn't have what
the target requires" case: the contract **cannot be satisfied from this input alone**.

- **Input schema:** `input.patient-admission.schema.json`
- **Output schema:** `output.insurance-claim.schema.json`
- **Sample instance:** `sample.patient-admission.json`

## Expected coverage

| Target field       | Status        | Source / why |
|--------------------|---------------|--------------|
| `claimId`          | ✗ **gap**     | generated at claim creation |
| `patientFirstName` | ✓ direct      | `patientFirstName` |
| `patientLastName`  | ✓ direct      | `patientLastName` |
| `dateOfBirth`      | ✓ direct      | `dateOfBirth` |
| `gender`           | ✓ direct      | `gender` |
| `memberId`         | ✗ **gap**     | **eligibility lookup** (extra input) |
| `payerId`          | ✗ **gap**     | **eligibility lookup** |
| `providerNPI`      | ✗ **gap**     | **provider-registry lookup** from `attendingPhysician` |
| `diagnosisCode`    | ✗ **gap**     | **ICD-10 coding** from `primaryComplaint` |
| `serviceDate`      | ✗ **gap**     | **derivable** from `admissionDate` (LLM refine) |
| `totalCharge`      | ✗ **gap**     | no charges in the admission |
| `placeOfService`   | ✗ **gap**     | POS default / derive from `roomNumber` |

**Delta (required, no source): `claimId, memberId, payerId, providerNPI, diagnosisCode,
serviceDate, totalCharge, placeOfService`** — 8 of 12 required fields.

This example makes the point that coverage analysis answers a *design-time* question
("can this contract even be fulfilled?") before any code is written: here the answer is
"not without an eligibility lookup, a provider registry, a coding step, and charge data."
After **"✨ Refine gaps (AI)"**, `serviceDate` should resolve to derivable; the rest
should remain flagged as genuinely unmappable from this input.
