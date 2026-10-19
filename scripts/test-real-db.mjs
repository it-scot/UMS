import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import fs from "fs";

// Make sure FIRESTORE_EMULATOR_HOST is deleted so it connects to real cloud Firestore
delete process.env.FIRESTORE_EMULATOR_HOST;

const sa = JSON.parse(fs.readFileSync("ums-privatekey.json", "utf8"));
const app = initializeApp({ credential: cert(sa), projectId: sa.project_id });

for (const dbId of ["default", "(default)"]) {
  try {
    const db = getFirestore(app, dbId);
    const cols = await db.listCollections();
    console.log(`=== Database '${dbId}' collections ===`);
    console.log(cols.map((c) => c.id));
    for (const c of cols) {
      const snap = await db.collection(c.id).limit(3).get();
      console.log(`Collection '${c.id}' has at least ${snap.size} docs.`);
      snap.forEach((d) => console.log(`  doc [${d.id}]:`, JSON.stringify(d.data()).slice(0, 100)));
    }
  } catch (err) {
    console.log(`Database '${dbId}' error:`, err.message);
  }
}
