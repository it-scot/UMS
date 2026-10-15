package lk.scot.ums.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
        @NotBlank @Email String email
) {
}
