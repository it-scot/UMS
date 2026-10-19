import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import fs from "fs";

delete process.env.FIRESTORE_EMULATOR_HOST;
const sa = JSON.parse(fs.readFileSync("ums-privatekey.json", "utf8"));
const app = initializeApp({ credential: cert(sa), projectId: sa.project_id });
const db = getFirestore(app, "default");

const snap = await db.collection("employees").limit(30).get();
console.log(`Found ${snap.size} sample employees in cloud database:`);
snap.forEach((d) => {
  const data = d.data();
  console.log(
    `[${d.id}] staffID: "${data.staffID || data.staffId}", name: "${data.nameInFull || data.preferredName}", email: "${data.email}", dept: "${data.department}", status: "${data.status}"`
  );
});
