import ExcelJS from "exceljs";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import fs from "fs";

process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";

const app = initializeApp({ projectId: "university-mgt-system" });

const excelPath = "C:/Users/Disitha/Downloads/Updated Staff List.xlsx";
if (!fs.existsSync(excelPath)) {
  console.error("Staff list Excel file not found at:", excelPath);
  process.exit(1);
}

const wb = new ExcelJS.Workbook();
await wb.xlsx.readFile(excelPath);
const sheet = wb.worksheets[0];

// Extract distinct departments and employees
const deptMap = new Map(); // name -> id
const employees = [];

// Known specific emails for core staff
const knownEmails = {
  157: "disitha@scot.lk",
  63: "sheran@scot.lk",
  2: "yohan@scot.lk",
  62: "musthaque@scot.lk",
  6: "niroshan@scot.lk",
  161: "kaushalya@scot.lk",
  160: "wasana@scot.lk",
  66: "deshan@scot.lk",
  130: "ishala@scot.lk",
  89: "pavithra@scot.lk",
  33: "rasadi@scot.lk",
  169: "chandima@scot.lk",
  37: "madushani@scot.lk",
  134: "kalpani@scot.lk",
  120: "rashmi@scot.lk",
  27: "nimantha@scot.lk",
  124: "shashika@scot.lk",
  72: "kirubanuja@scot.lk",
  171: "lushanthika@scot.lk",
  128: "madushan@scot.lk",
  104: "ishan.udayanga@scot.lk",
  151: "madhavi@scot.lk",
  56: "thishara@scot.lk",
  108: "rushen@scot.lk",
  155: "nirmani@scot.lk",
  141: "lahiru@scot.lk",
  46: "parartha@scot.lk",
  145: "tehani@scot.lk",
};

let deptCounter = 1;

sheet.eachRow((row, rowIdx) => {
  if (rowIdx === 1) return; // skip header
  const empNo = String(row.getCell(1).value ?? "").trim();
  const name = String(row.getCell(2).value ?? "").trim();
  const designation = String(row.getCell(3).value ?? "").trim();
  const deptName = String(row.getCell(4).value ?? "").trim();

  if (!empNo || !name) return;

  if (deptName && !deptMap.has(deptName)) {
    const deptId = "DEP-" + String(deptCounter++).padStart(3, "0");
    deptMap.set(deptName, deptId);
  }

  // Derive preferred name and email
  const nameParts = name.split(/\s+/).filter(Boolean);
  const preferredName = nameParts.length > 2 
    ? `${nameParts[nameParts.length - 2]} ${nameParts[nameParts.length - 1]}`
    : name;

  let email = knownEmails[empNo];
  if (!email) {
    const cleanFirstName = (nameParts[nameParts.length - 1] || "staff")
      .toLowerCase()
      .replace(/[^a-z0-9]/g, "");
    email = `${cleanFirstName}@scot.lk`;
  }

  const isDisitha = empNo === "157" || email === "disitha@scot.lk";
  const isSheran = empNo === "63" || email === "sheran@scot.lk";

  employees.push({
    staffId: empNo,
    nameInFull: name,
    preferredName: isDisitha ? "Disitha Ranasinghe" : preferredName,
    title: isDisitha ? "Mr." : "Mr./Ms.",
    designation: designation || "Staff Member",
    departmentName: deptName || "General Administration",
    departmentId: deptMap.get(deptName) || "DEP-GEN",
    email: email.toLowerCase(),
    contactNumber: isDisitha ? "0774808044" : "0771234567",
    supervisor: isDisitha ? "Yohan Deshapriya" : "Senior Management",
    status: "Permanent",
    userRole: isDisitha || isSheran ? "Super Admin" : "Employee",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: isDisitha ? "2026-03-16" : "2024-01-01",
    photoUrl: "",
  });
});

console.log(`Parsed ${deptMap.size} departments and ${employees.length} employees from Excel.`);

async function seedTargetDb(targetDb, dbLabel) {
  console.log(`\nWriting to emulator database '${dbLabel}'...`);

  // 1. Departments
  for (const [deptName, deptId] of deptMap.entries()) {
    await targetDb.collection("departments").doc(deptId).set({
      departmentID: deptId,
      departmentName: deptName,
      createdAt: Timestamp.now(),
    });
  }

  // 2. Employees (in chunks)
  const chunkSize = 25;
  for (let i = 0; i < employees.length; i += chunkSize) {
    const chunk = employees.slice(i, i + chunkSize);
    const batch = targetDb.batch();
    for (const emp of chunk) {
      const ref = targetDb.collection("employees").doc(emp.staffId);
      batch.set(ref, {
        staffID: emp.staffId,
        staffId: emp.staffId,
        nameInFull: emp.nameInFull,
        preferredName: emp.preferredName,
        title: emp.title,
        designation: emp.designation,
        department: emp.departmentId,
        email: emp.email,
        contactNumber: emp.contactNumber,
        supervisor: emp.supervisor,
        status: emp.status,
        userRole: emp.userRole,
        workingDaysOption: emp.workingDaysOption,
        shiftTimeOption: emp.shiftTimeOption,
        dateJoined: emp.dateJoined,
        photo: emp.photoUrl,
        updatedAt: Timestamp.now(),
      });
    }
    await batch.commit();
  }

  // 3. Work Schedules for Disitha & General
  const schedules = [
    { day: "Monday", startTime: "08:30", endTime: "17:30" },
    { day: "Tuesday", startTime: "08:30", endTime: "17:30" },
    { day: "Wednesday", startTime: "08:30", endTime: "17:30" },
    { day: "Thursday", startTime: "08:30", endTime: "17:30" },
    { day: "Friday", startTime: "08:30", endTime: "17:30" },
  ];

  for (const s of schedules) {
    await targetDb.collection("workSchedules").add({
      employeeID: "157",
      day: s.day,
      startTime: s.startTime,
      endTime: s.endTime,
    });
  }

  // 4. Sample Attendance Punches for Disitha (Today & Past Days)
  const today = new Date().toISOString().slice(0, 10);
  const punches = [
    { eventTime: `${today}T08:28:15`, readerName: "192.168.137.52-1-In", readerState: "0" },
    { eventTime: `${today}T12:30:10`, readerName: "192.168.137.52-1-Out", readerState: "1" },
    { eventTime: `${today}T13:15:45`, readerName: "192.168.137.52-1-In", readerState: "0" },
    { eventTime: `${today}T17:32:00`, readerName: "192.168.137.52-1-Out", readerState: "1" },
  ];

  for (const p of punches) {
    await targetDb.collection("attendance").add({
      staffIDFK: "157",
      employeeID: "157",
      eventTime: p.eventTime,
      readerName: p.readerName,
      readerState: p.readerState,
      areaName: "Main Entrance",
      verificationMode: "Fingerprint",
      eventName: "Normal Attendance",
    });
  }

  console.log(`Successfully seeded ${employees.length} employees, ${deptMap.size} departments, schedules, and attendance into '${dbLabel}'.`);
}

async function run() {
  await seedTargetDb(getFirestore(app), "(default)");
  await seedTargetDb(getFirestore(app, "default"), "default");
  console.log("\nALL REAL DATA IMPORTED INTO LOCAL FIRESTORE EMULATOR SUCCESSFULLY!");
}

run().catch((err) => {
  console.error("Error importing real staff:", err);
  process.exit(1);
});
