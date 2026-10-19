import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";

const app = initializeApp({ projectId: "university-mgt-system" });
const db = getFirestore(app);

const departments = [
  { id: "DEP-HR", departmentName: "Human Resources" },
  { id: "DEP-IT", departmentName: "Information Technology" },
  { id: "DEP-ADMIN", departmentName: "General Administration" },
  { id: "DEP-ENG", departmentName: "Faculty of Engineering" },
  { id: "DEP-MGT", departmentName: "Faculty of Management" },
];

const employees = [
  {
    id: "EMP001",
    staffId: "EMP001",
    nameInFull: "Sheran Kanishka Daniel",
    preferredName: "Sheran",
    title: "Mr.",
    designation: "Senior Manager - HR",
    department: "DEP-HR",
    email: "sheran@scot.lk",
    contactNumber: "0743866557",
    supervisor: "Board of Directors",
    status: "Permanent",
    userRole: "Super Admin",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2023-10-16",
    photoUrl: "",
  },
  {
    id: "EMP002",
    staffId: "EMP002",
    nameInFull: "Disitha Scot",
    preferredName: "Disitha",
    title: "Mr.",
    designation: "Head of IT & Systems",
    department: "DEP-IT",
    email: "it@scot.lk",
    contactNumber: "0771234560",
    supervisor: "EMP001",
    status: "Permanent",
    userRole: "Administrator",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2023-11-01",
    photoUrl: "",
  },
  {
    id: "EMP003",
    staffId: "EMP003",
    nameInFull: "Malsha Fernando",
    preferredName: "Malsha",
    title: "Ms.",
    designation: "HR Executive",
    department: "DEP-HR",
    email: "hr@scot.lk",
    contactNumber: "0719876543",
    supervisor: "EMP001",
    status: "Permanent",
    userRole: "Administrator",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2024-01-15",
    photoUrl: "",
  },
  {
    id: "EMP004",
    staffId: "EMP004",
    nameInFull: "Kamal Perera",
    preferredName: "Kamal",
    title: "Mr.",
    designation: "University Administrator",
    department: "DEP-ADMIN",
    email: "admin@scot.lk",
    contactNumber: "0751239876",
    supervisor: "Board of Directors",
    status: "Permanent",
    userRole: "Administrator",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2022-05-10",
    photoUrl: "",
  },
  {
    id: "EMP005",
    staffId: "EMP005",
    nameInFull: "Andrino Silva",
    preferredName: "Andrino",
    title: "Mr.",
    designation: "Senior Software Engineer",
    department: "DEP-IT",
    email: "andrino@scot.lk",
    contactNumber: "0771234567",
    supervisor: "EMP002",
    status: "Permanent",
    userRole: "Staff",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2024-03-01",
    photoUrl: "",
  },
  {
    id: "EMP006",
    staffId: "EMP006",
    nameInFull: "Dr. Nimal Wickramasinghe",
    preferredName: "Dr. Nimal",
    title: "Dr.",
    designation: "Dean - Faculty of Engineering",
    department: "DEP-ENG",
    email: "nimal@scot.lk",
    contactNumber: "0773344556",
    supervisor: "Board of Directors",
    status: "Permanent",
    userRole: "Staff",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:00 to 5:00",
    dateJoined: "2021-08-01",
    photoUrl: "",
  },
  {
    id: "EMP007",
    staffId: "EMP007",
    nameInFull: "Dilani Jayawardena",
    preferredName: "Dilani",
    title: "Mrs.",
    designation: "Senior Lecturer",
    department: "DEP-MGT",
    email: "dilani@scot.lk",
    contactNumber: "0712233445",
    supervisor: "EMP004",
    status: "Permanent",
    userRole: "Staff",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2022-09-15",
    photoUrl: "",
  },
  {
    id: "EMP008",
    staffId: "EMP008",
    nameInFull: "Kasun Bandara",
    preferredName: "Kasun",
    title: "Mr.",
    designation: "Assistant Registrar",
    department: "DEP-ADMIN",
    email: "kasun@scot.lk",
    contactNumber: "0765566778",
    supervisor: "EMP004",
    status: "Permanent",
    userRole: "Staff",
    workingDaysOption: "Monday to Friday",
    shiftTimeOption: "8:30 to 5:30",
    dateJoined: "2024-06-01",
    photoUrl: "",
  },
];

async function seedDatabase(targetDb, name) {
  console.log(`Seeding database '${name}'...`);
  const batch = targetDb.batch();

  for (const dept of departments) {
    const ref = targetDb.collection("departments").doc(dept.id);
    batch.set(ref, {
      departmentName: dept.departmentName,
      createdAt: Timestamp.now(),
    });
  }

  for (const emp of employees) {
    const ref = targetDb.collection("employees").doc(emp.id);
    batch.set(ref, {
      staffId: emp.staffId,
      nameInFull: emp.nameInFull,
      preferredName: emp.preferredName,
      title: emp.title,
      designation: emp.designation,
      department: emp.department,
      email: emp.email,
      contactNumber: emp.contactNumber,
      supervisor: emp.supervisor,
      status: emp.status,
      userRole: emp.userRole,
      workingDaysOption: emp.workingDaysOption,
      shiftTimeOption: emp.shiftTimeOption,
      dateJoined: emp.dateJoined,
      photoUrl: emp.photoUrl,
      updatedAt: Timestamp.now(),
    });
  }

  await batch.commit();
  console.log(`Successfully seeded ${departments.length} departments and ${employees.length} employees into '${name}'!`);
}

async function seed() {
  await seedDatabase(getFirestore(app), "(default)");
  await seedDatabase(getFirestore(app, "default"), "default");
}

seed().catch((err) => {
  console.error("Error seeding emulator:", err);
  process.exit(1);
});
