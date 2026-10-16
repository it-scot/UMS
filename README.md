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

## Run the staff portal and 360° evaluations

The React/Vite frontend lives in `frontend/`; the Spring Boot API lives in
`backend/`. Evaluation data is read and written by the backend through the
Firebase Admin SDK. Firestore rules continue to deny direct client access.
Verified staff can use the Staff directory to browse active colleagues by name
or department and view their SCOT work email, mobile number, position,
department, and supervisor. It only displays profile photos when an employee record has a
`profilePhotoUrl`, `photoUrl`, `photoURL`, or `avatarUrl` field; otherwise it
uses the colleague's initials.

For localhost UI testing, the ignored `frontend/.env.local` may set
`VITE_LOCAL_EMAIL_LOGIN=true`. This development-only option uses the backend's
email-only test login for an active employee record; it does not verify control
of the email address. Start the backend with `start-backend.bat` or
`start-all.bat`, which enables demo authentication and binds the API to
`127.0.0.1`. Demo login is disabled by default in application configuration
and must never be enabled on a network-accessible server. Local demo sessions
exist only in backend memory and expire when the backend restarts.

For verified sign-in, set `VITE_LOCAL_EMAIL_LOGIN=false` or remove it, then:

1. In Firebase Authentication, enable the Email/Password provider and its
   **Email link (passwordless sign-in)** option. Add `localhost` and the
   deployed frontend host under Authentication → Settings → Authorized domains.
   Add the Firebase Web App configuration to an untracked `frontend/.env.local`
   file:

   ```dotenv
   VITE_FIREBASE_API_KEY=your-web-api-key
   VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
   VITE_FIREBASE_PROJECT_ID=your-project-id
   VITE_FIREBASE_APP_ID=your-web-app-id
   ```

   These are Firebase web-app settings, not service-account credentials. Never
   put a service-account key in the frontend or commit it.
2. Create a backend service account with access to the configured Firestore
   database. Store its JSON key outside source control and set
   `GOOGLE_APPLICATION_CREDENTIALS` to its absolute path. Set
   `FIREBASE_PROJECT_ID` and `FIRESTORE_DATABASE_ID` if they differ from the
   defaults in `application.yml`.
3. Start the applications from separate PowerShell terminals:

   ```powershell
   npm install --prefix frontend
   npm run dev --prefix frontend
   ```

   ```powershell
   $env:GOOGLE_APPLICATION_CREDENTIALS = "C:\secure\path\service-account.json"
   $env:UMS_DEMO_AUTH_ENABLED = "false"
   $env:SERVER_ADDRESS = "127.0.0.1"
   mvn -f backend\pom.xml spring-boot:run
   ```

   The frontend proxies `/api` to `http://localhost:8081` by default. Set
   `UMS_BACKEND_URL` in the frontend terminal if the backend uses another URL.

In verified mode, staff enter their work email and receive a secure Firebase
sign-in link; no password is entered in the portal. Only verified `sheran@scot.lk` and
`it@scot.lk` accounts receive evaluation
administration access; this allowlist is enforced by the backend. Other active
employees can access only their own results and their assigned forms. Peer
evaluator identities are returned only to those backend-authorized admins.
Create self, primary, and peer question templates before starting a cycle. For
each department round, choose its departments, the three question templates,
and custom opening and submission-deadline dates, then publish that round.
Additional rounds can be published in the same evaluation year with different
departments, dates, or templates; a department can only be included once in
that year. Track submissions and release results separately for each round.
Completed marks remain private to each employee until an admin releases the
results for their round.

Evaluation templates support individual five-point rating questions and
five-column rating tables. In a rating table, the form creator defines the
instruction, the criteria rows, and the label for each rating column.
Evaluators select one rating for every criterion; table-row ratings contribute
to the form's average score like individual rating questions.

The evaluation assignment planner uses employee department, designation, and
supervisor fields from the existing `employees` collection and department
document IDs from `departments`. Confirm those records are current before
publishing a cycle. The backend does not silently substitute demo employees or
marks when records or configuration are missing.
