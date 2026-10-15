import { createReadStream, existsSync } from "node:fs";
import { createHash } from "node:crypto";
import path from "node:path";
import { parse } from "csv-parse/sync";
import ExcelJS from "exceljs";
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

const projectId = process.env.FIREBASE_PROJECT_ID ?? "university-mgt-system";
const leaveWorkbookPath = path.resolve(
  process.env.LEAVE_WORKBOOK ?? "Leave Management.xlsx",
);
const employeeCsvPath = path.resolve(
  process.env.EMPLOYEE_CSV ??
    "Master File (Recorrect) (88).xlsm - Master File.csv",
);

const sheetDefinitions = [
  { sheet: "User Roles", collection: "roles", idHeader: "Role ID" },
  { sheet: "Holidays_Master", collection: "holidays", idHeader: "Holiday ID" },
  { sheet: "Employees", collection: "employees", idHeader: "Staff ID" },
  {
    sheet: "Departments_Master",
    collection: "departments",
    idHeader: "Department ID",
  },
  {
    sheet: "Annual Leaves",
    collection: "annualLeaves",
    idHeader: "AL ID",
  },
  {
    sheet: "Casual Leaves",
    collection: "casualLeaves",
    idHeader: "CL ID",
  },
  {
    sheet: "Lieu Leaves",
    collection: "lieuLeaves",
    idHeader: "EXL_ID",
  },
  { sheet: "No Pay", collection: "noPayLeaves", idHeader: "NP ID" },
  { sheet: "WFH", collection: "workFromHome", idHeader: "WFH ID" },
  { sheet: "Holiday Work", collection: "holidayWork", idHeader: "LL ID" },
  { sheet: "OT", collection: "overtime", idHeader: "OT ID" },
  {
    sheet: "Work Schedules",
    collection: "workSchedules",
    idHeader: "Schedule ID",
  },
];

function cellValue(value) {
  if (value === null || value === undefined || value === "") {
    return undefined;
  }
  if (value instanceof Date) {
    return Timestamp.fromDate(value);
  }
  if (typeof value === "object") {
    if (Array.isArray(value.richText)) {
      return value.richText.map((part) => part.text).join("");
    }
    if (typeof value.text === "string" && value.hyperlink) {
      return value.text;
    }
    if (Object.hasOwn(value, "result")) {
      return cellValue(value.result);
    }
    throw new Error("Encountered an unsupported spreadsheet cell value.");
  }
  return value;
}

function normalizedFieldNames(headers) {
  const seen = new Map();
  return headers.map((header, index) => {
    const words = String(header ?? "")
      .trim()
      .split(/[^A-Za-z0-9]+/)
      .filter(Boolean);
    if (words.length === 0) {
      return `column${index + 1}`;
    }
    let name = words
      .map((word, wordIndex) =>
        wordIndex === 0
          ? word.toLowerCase()
          : `${word[0].toUpperCase()}${word.slice(1)}`,
      )
      .join("");
    if (/^[0-9]/.test(name)) {
      name = `field${name}`;
    }
    const occurrences = (seen.get(name) ?? 0) + 1;
    seen.set(name, occurrences);
    return occurrences === 1 ? name : `${name}${occurrences}`;
  });
}

function rowToDocument(row, headers, fieldNames, idHeader, sourceRow) {
  const idColumn = headers.indexOf(idHeader);
  if (idColumn < 0) {
    throw new Error(`Required ID column "${idHeader}" was not found.`);
  }

  const id = cellValue(row.getCell(idColumn + 1).value);
  if (id === undefined || String(id).trim() === "") {
    throw new Error(`Missing "${idHeader}" at source row ${sourceRow}.`);
  }

  const document = {};
  headers.forEach((header, index) => {
    if (String(header ?? "").trim() === "") {
      return;
    }
    const value = cellValue(row.getCell(index + 1).value);
    if (value !== undefined) {
      document[fieldNames[index]] = value;
    }
  });
  return { id: String(id).trim(), document };
}

async function loadWorkbookDocuments() {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(leaveWorkbookPath);

  const collections = [];
  for (const definition of sheetDefinitions) {
    const worksheet = workbook.getWorksheet(definition.sheet);
    if (!worksheet) {
      throw new Error(`Required worksheet "${definition.sheet}" was not found.`);
    }

    const headerRow = worksheet.getRow(1);
    const headers = Array.from(
      { length: headerRow.cellCount },
      (_, index) => headerRow.getCell(index + 1).text.trim(),
    );
    const fieldNames = normalizedFieldNames(headers);
    const ids = new Set();
    const documents = [];

    for (let rowNumber = 2; rowNumber <= worksheet.rowCount; rowNumber += 1) {
      const row = worksheet.getRow(rowNumber);
      const hasData = headers.some(
        (header, index) =>
          String(header ?? "").trim() !== "" &&
          row.getCell(index + 1).value !== null &&
          row.getCell(index + 1).value !== undefined &&
          row.getCell(index + 1).value !== "",
      );
      if (!hasData) {
        continue;
      }
      const item = rowToDocument(
        row,
        headers,
        fieldNames,
        definition.idHeader,
        rowNumber,
      );
      if (ids.has(item.id)) {
        throw new Error(
          `Duplicate ID "${item.id}" in worksheet "${definition.sheet}".`,
        );
      }
      ids.add(item.id);
      documents.push(item);
    }

    collections.push({ name: definition.collection, documents });
  }
  return collections;
}

async function loadEmployeeProfiles() {
  const csvText = await new Promise((resolve, reject) => {
    let contents = "";
    const stream = createReadStream(employeeCsvPath, { encoding: "utf8" });
    stream.on("data", (chunk) => {
      contents += chunk;
    });
    stream.on("error", reject);
    stream.on("end", () => resolve(contents));
  });
  const rows = parse(csvText, {
    bom: true,
    relax_column_count: true,
    skip_empty_lines: true,
  });
  if (rows.length < 2) {
    throw new Error("The employee CSV must contain a group header and columns.");
  }

  const headers = rows[1].map((header) => String(header ?? "").trim());
  if (headers[0] !== "Emp. Number") {
    throw new Error('Expected "Emp. Number" in the first employee CSV column.');
  }
  const fieldNames = normalizedFieldNames(headers);
  const ids = new Set();
  const documents = [];

  for (let index = 2; index < rows.length; index += 1) {
    const row = rows[index];
    if (!row.some((value) => String(value ?? "").trim() !== "")) {
      continue;
    }
    const employeeNumber = String(row[0] ?? "").trim();
    const id =
      employeeNumber ||
      `unassigned-${createHash("sha256")
        .update(JSON.stringify(row))
        .digest("hex")
        .slice(0, 16)}`;
    if (ids.has(id)) {
      throw new Error(`Duplicate employee number in CSV row ${index + 1}.`);
    }
    if (row.slice(headers.length).some((value) => String(value ?? "").trim())) {
      throw new Error(`Unexpected extra data in employee CSV row ${index + 1}.`);
    }
    ids.add(id);

    const document = {};
    headers.forEach((header, column) => {
      if (!header) {
        return;
      }
      const value = row[column];
      if (value !== undefined && String(value).trim() !== "") {
        document[fieldNames[column]] = String(value);
      }
    });
    if (!employeeNumber) {
      document[fieldNames[0]] = null;
      document.sourceRow = index + 1;
    }
    documents.push({ id, document });
  }

  return { name: "employeeProfiles", documents };
}

async function writeCollections(db, collections) {
  const batchLimit = 450;
  for (const collection of collections) {
    for (let offset = 0; offset < collection.documents.length; offset += batchLimit) {
      const batch = db.batch();
      const items = collection.documents.slice(offset, offset + batchLimit);
      for (const item of items) {
        batch.set(db.collection(collection.name).doc(item.id), item.document);
      }
      await batch.commit();
    }
    console.log(`${collection.name}: imported ${collection.documents.length} documents`);
  }
}

async function main() {
  if (!existsSync(leaveWorkbookPath)) {
    throw new Error(`Leave workbook not found: ${leaveWorkbookPath}`);
  }
  if (!existsSync(employeeCsvPath)) {
    throw new Error(`Employee CSV not found: ${employeeCsvPath}`);
  }
  const collections = await loadWorkbookDocuments();
  collections.push(await loadEmployeeProfiles());

  if (process.argv.includes("--dry-run")) {
    for (const collection of collections) {
      console.log(
        `${collection.name}: validated ${collection.documents.length} documents`,
      );
    }
    return;
  }

  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    throw new Error(
      "Set GOOGLE_APPLICATION_CREDENTIALS to a Firebase service-account JSON file path.",
    );
  }

  initializeApp({
    credential: applicationDefault(),
    projectId,
  });
  await writeCollections(getFirestore(), collections);
}

main().catch((error) => {
  console.error(`Import failed: ${error.message}`);
  process.exitCode = 1;
});
