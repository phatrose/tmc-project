package vn.tmc.central.usermgmt.internal.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 1.
 * Triển khai dịch vụ xác thực, quản lý phiên và cấp Action Grant.
 * Theo CONTRACT-V1.md §3, §5, §10.
 */
@Service
public class AuthenticationService implements Authentication {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuerPort tokenIssuerPort;

    // Quản lý phiên trong bộ nhớ (in-memory) có thời hạn 15 phút (CONTRACT-V1.md §3)
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public AuthenticationService(UserAccountRepository userAccountRepository,
                                 PasswordEncoder passwordEncoder,
                                 TokenIssuerPort tokenIssuerPort) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenIssuerPort = Objects.requireNonNull(tokenIssuerPort);
    }

    @Override
    public Session authenticate(LoginRequest request) {
        Objects.requireNonNull(request, "request required");
        Objects.requireNonNull(request.username(), "username required");
        Objects.requireNonNull(request.password(), "password required");

        UserAccount account = userAccountRepository.findByUsername(request.username().trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!account.isActive()) {
            throw new AccountLockedException(request.username());
        }

        if (account.isLocked()) {
            throw new AccountLockedException(request.username());
        }

        boolean valid = passwordEncoder.matches(request.password(), account.getPasswordHash());
        if (!valid) {
            throw new InvalidCredentialsException();
        }

        String accessToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        List<String> permissions = resolvePermissions(account.getRoles());

        ActorContext actor = new ActorContext(
                account.getId(),
                account.getEmployeeId(),
                account.getRoles(),
                permissions,
                account.getFactoryIds(),
                account.getDepartmentIds(),
                account.getScope()
        );

        Session session = new Session(accessToken, "Bearer", expiresAt, actor);
        activeSessions.put(accessToken, session);
        return session;
    }

    @Override
    public ActorContext getActorContext(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidCredentialsException("Yêu cầu accessToken hợp lệ.");
        }
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        Session session = activeSessions.get(token);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            if (session != null) activeSessions.remove(token);
            throw new InvalidCredentialsException("Phiên làm việc đã hết hạn hoặc không tồn tại.");
        }
        return session.actor();
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken != null) {
            String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
            activeSessions.remove(token);
        }
    }

    @Override
    public ActionGrantResponse issueActionGrant(ActorContext actor, ActionGrantRequest request) {
        Objects.requireNonNull(actor, "actor required");
        Objects.requireNonNull(request, "request required");

        // 1. Kiểm tra permission theo loại action
        String requiredPerm = "MANUAL".equalsIgnoreCase(request.action()) ? "gate.override" : "gate.emergency";
        if (!actor.permissions().contains(requiredPerm)) {
            throw new ForbiddenException("Không có quyền yêu cầu Action Grant: " + requiredPerm);
        }

        // 2. Re-auth với mật khẩu
        UserAccount account = userAccountRepository.findById(actor.userId())
                .orElseThrow(() -> new ForbiddenException("Không tìm thấy thông tin tài khoản người dùng."));

        boolean reauthValid = passwordEncoder.matches(request.password(), account.getPasswordHash());
        if (!reauthValid) {
            throw new InvalidCredentialsException("Mật khẩu xác thực lại không chính xác.");
        }

        // 3. Tạo token grant (hạn 60s) qua tokenIssuerPort
        Instant expiresAt = Instant.now().plus(60, ChronoUnit.SECONDS);
        String grant = tokenIssuerPort.issueToken(actor.userId(), actor.roles().isEmpty() ? Role.GUARD : actor.roles().get(0), List.of(requiredPerm));

        return new ActionGrantResponse(grant, expiresAt);
    }

    private List<String> resolvePermissions(List<Role> roles) {
        Set<String> perms = new HashSet<>();
        boolean isAuditor = roles.contains(Role.AUDITOR);

        for (Role role : roles) {
            switch (role) {
                case EMPLOYEE -> perms.add("employee.self");
                case SUPERVISOR -> {
                    perms.add("employee.self");
                    perms.add("attendance.read");
                }
                case HR -> {
                    perms.add("employee.manage");
                    perms.add("access-policy.manage");
                    perms.add("credential.manage");
                    perms.add("attendance.approve");
                    perms.add("payroll.export");
                    perms.add("report.read");
                    perms.add("report.export");
                }
                case GUARD -> {
                    perms.add("gate.override");
                    perms.add("gate.emergency");
                }
                case ADMIN -> {
                    perms.add("system.manage");
                    perms.add("employee.manage");
                    perms.add("access-policy.manage");
                }
                case EXECUTIVE -> perms.add("report.read");
                case AUDITOR -> perms.add("audit.read");
            }
        }

        // Cấm đặc quyền nếu là Auditor theo CONTRACT-V1.md §3
        if (isAuditor) {
            perms.remove("attendance.approve");
            perms.remove("gate.override");
            perms.remove("gate.emergency");
            perms.remove("credential.manage");
            perms.remove("system.manage");
        }

        return new ArrayList<>(perms);
    }
}
