package lk.scot.ums.auth;

import lk.scot.ums.profile.EmployeeProfile;

public record VerifiedSession(
        EmployeeProfile employee,
        boolean evaluationAdmin,
        boolean itAdmin
) {
}
