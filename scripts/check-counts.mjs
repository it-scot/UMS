import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import fs from "fs";

delete process.env.FIRESTORE_EMULATOR_HOST;
const sa = JSON.parse(fs.readFileSync("ums-privatekey.json", "utf8"));
const app = initializeApp({ credential: cert(sa), projectId: sa.project_id });
const db = getFirestore(app, "default");

const cols = await db.listCollections();
for (const col of cols) {
  const snap = await col.get();
  console.log(`${col.id}: ${snap.size} documents`);
}
