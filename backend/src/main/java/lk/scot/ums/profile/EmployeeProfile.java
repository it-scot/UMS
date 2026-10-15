package lk.scot.ums.profile;

public record EmployeeProfile(
        String staffId,
        String fullName,
        String preferredName,
        String title,
        String designation,
        String department,
        String workEmail,
        String dateJoined,
        String status,
        String role,
        String supervisor
) {
}
