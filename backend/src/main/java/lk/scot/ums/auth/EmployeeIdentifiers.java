package lk.scot.ums.auth;

import com.google.cloud.firestore.DocumentSnapshot;

public final class EmployeeIdentifiers {
    private static final String[] STAFF_ID_FIELDS = {
        "staffID", "staffId", "empID", "empId", "employeeID", "employeeId",
        "employeeNumber", "empNumber", "empNo"
    };

    private EmployeeIdentifiers() {
    }

    public static String staffId(DocumentSnapshot employee) {
        for (String field : STAFF_ID_FIELDS) {
            Object value = employee.get(field);
            if (value != null && !value.toString().trim().isBlank()) {
                return value.toString().trim();
            }
        }
        return employee.getId();
    }
}