# UMS Firebase database setup

This repository is being prepared to use Cloud Firestore in the
`university-mgt-system` Firebase project. The current import setup reads the
local source files and writes records to Firestore; it does not upload the
spreadsheets to source control.

## Collections

The `Leave Management.xlsx` tabs are imported into `roles`, `holidays`,
`employees`, `departments`, `annualLeaves`, `casualLeaves`, `lieuLeaves`,
`noPayLeaves`, `workFromHome`, `holidayWork`, `overtime`, and `workSchedules`.
Each record uses the ID from its corresponding ID column as the Firestore
document ID. The employee records in this workbook remain canonical because
leave records refer to their staff IDs.

The separate employee CSV is imported into `employeeProfiles`, keyed by
`Emp. Number`. It is not merged with `employees`, because the files use
different identifiers. Duplicate CSV column names are made unique with a
numeric suffix. A CSV record without an employee number is retained using a
stable content-derived document ID and is marked with a missing employee
number and source row.

## Provision Firestore and import

1. In Firebase Console, open the `university-mgt-system` project and create a
   Cloud Firestore database. Choose its location carefully; a database
   location cannot be changed after creation.
2. Sign in to the Firebase CLI with an account that can access this project,
   then deploy the default-deny rules and indexes:
   `firebase deploy --only firestore:rules,firestore:indexes`
3. Install the Node dependencies with `npm install`.
4. Create a service account with access to Cloud Firestore and download its
   JSON key outside this repository. Do not commit or share that key.
5. Set `GOOGLE_APPLICATION_CREDENTIALS` to the key's absolute path. The
   project ID defaults to `university-mgt-system`; set `FIREBASE_PROJECT_ID`
   only if importing to a different project. The importer targets the existing
   Firestore database ID `default`; set `FIRESTORE_DATABASE_ID` only if using
   another database.
6. Keep `Leave Management.xlsx` and
   `Master File (Recorrect) (88).xlsm - Master File.csv` in the repository
   root, then run `npm run db:import`. To check the input without connecting to
   Firebase, run `npm run db:import -- --dry-run`.

The importer validates required sheets, IDs, and duplicate IDs before writing.
It uses the worksheet ID columns as document IDs, writes in batches, and
overwrites matching documents without deleting other existing documents.
Firestore rules deny all client access by default; the trusted Spring Boot
backend should access Firestore through the Admin SDK.
