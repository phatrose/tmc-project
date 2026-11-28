package vn.tmc.central.usermgmt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.api.ActionGrantResponse;
import vn.tmc.central.usermgmt.api.Authentication.ActionGrantRequest;
import vn.tmc.central.usermgmt.api.Authentication.LoginRequest;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.usermgmt.api.Session;
import vn.tmc.central.usermgmt.api.TokenIssuerPort;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;
import vn.tmc.central.usermgmt.internal.service.AccountLockedException;
import vn.tmc.central.usermgmt.internal.service.AuthenticationService;
import vn.tmc.central.usermgmt.internal.service.InvalidCredentialsException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * PHỤ TRÁCH: Task 1.
 * Unit tests cho AuthenticationService theo CONTRACT-V1.md §3, §5.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenIssuerPort tokenIssuerPort;

    private AuthenticationService authenticationService;

    @Test
    void inactiveAccountCannotAuthenticate() {
        UserAccount account = new UserAccount("disabled", "hash", null,
                List.of(Role.EMPLOYEE), List.of(), List.of(), "SELF");
        assertThat(account.isLocked()).isFalse();
        account.update(null, null, null, null, false, account.getVersion());
        assertThat(account.isLocked()).isTrue();
        when(userAccountRepository.findByUsername("disabled")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> authenticationService.authenticate(new LoginRequest("disabled", "password")))
                .isInstanceOf(AccountLockedException.class);
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder, tokenIssuerPort);
    }
    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userAccountRepository, passwordEncoder, tokenIssuerPort
        );
    }

    @Test
    @DisplayName("authenticate: trả về Session gồm opaque accessToken và ActorContext")
    void authenticate_success_returnsSession() {
        UserAccount account = new UserAccount(
                "admin", "$2a$10$hashedPassword", null,
                List.of(Role.ADMIN), List.of("FAC-01"), List.of(), "GLOBAL"
        );

        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedPassword")).thenReturn(true);

        Session session = authenticationService.authenticate(new LoginRequest("admin", "secret123"));

        assertThat(session).isNotNull();
        assertThat(session.tokenType()).isEqualTo("Bearer");
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.actor().roles()).containsExactly(Role.ADMIN);
        assertThat(session.actor().scope()).isEqualTo("GLOBAL");
    }

    @Test
    @DisplayName("authenticate: sai mật khẩu ném InvalidCredentialsException")
    void authenticate_wrongPassword_throwsException() {
        UserAccount account = new UserAccount("user1", "$2a$10$hashedPassword", null, List.of(Role.EMPLOYEE), List.of(), List.of(), "SELF");

        when(userAccountRepository.findByUsername("user1")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.authenticate(new LoginRequest("user1", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("issueActionGrant: cấp grant 60s cho Guard có quyền gate.override")
    void issueActionGrant_success() {
        UserAccount guard = new UserAccount("guard1", "$2a$10$hashed", null, List.of(Role.GUARD), List.of("FAC-01"), List.of(), "FACTORY");

        when(userAccountRepository.findByUsername("guard1")).thenReturn(Optional.of(guard));
        when(passwordEncoder.matches("pass123", "$2a$10$hashed")).thenReturn(true);

        Session session = authenticationService.authenticate(new LoginRequest("guard1", "pass123"));

        when(userAccountRepository.findById(guard.getId())).thenReturn(Optional.of(guard));
        when(tokenIssuerPort.issueActionGrant(any())).thenReturn("action.grant.jwt");

        ActionGrantRequest request = new ActionGrantRequest(
                "pass123",
                "MANUAL",
                "00000000-0000-4000-8000-0000000000e1",
                "00000000-0000-4000-8000-000000000011",
                "00000000-0000-4000-8000-000000000101",
                "Mất thẻ nhân viên cần vào khẩn cấp"
        );

        ActionGrantResponse response = authenticationService.issueActionGrant(session.actor(), request);

        assertThat(response).isNotNull();
        assertThat(response.grant()).isEqualTo("action.grant.jwt");
    }
}
