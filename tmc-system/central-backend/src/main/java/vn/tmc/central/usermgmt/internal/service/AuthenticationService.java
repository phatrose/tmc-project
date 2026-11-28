package vn.tmc.central.usermgmt.internal.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
@org.springframework.context.annotation.Profile("!platform")
public class AuthenticationService implements Authentication {

    private record StoredSession(Instant expiresAt, ActorContext actor) {}

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuerPort tokenIssuerPort;

    // Lưu hash SHA-256 của accessToken thay vì token thô (CONTRACT-V1.md §3), thời hạn 15 phút.
    private final Map<String, StoredSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> reauthDeadlines = new ConcurrentHashMap<>();

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

        String trimmedUsername = request.username().trim();
        UserAccount account = userAccountRepository.findByUsername(trimmedUsername)
                .or(() -> userAccountRepository.findByUsernameIgnoreCase(trimmedUsername))
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

        activeSessions.put(hashToken(accessToken), new StoredSession(expiresAt, actor));
        return new Session(accessToken, "Bearer", expiresAt, actor);
    }

    @Override
    public ActorContext getActorContext(String accessToken) {
        String token = extractBearerToken(accessToken);
        if (token == null) {
            throw new InvalidCredentialsException("Yêu cầu accessToken hợp lệ.");
        }
        String tokenHash = hashToken(token);
        StoredSession session = activeSessions.get(tokenHash);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            if (session != null) activeSessions.remove(tokenHash);
            throw new InvalidCredentialsException("Phiên làm việc đã hết hạn hoặc không tồn tại.");
        }
        return session.actor();
    }

    @Override
    public void logout(String accessToken) {
        String token = extractBearerToken(accessToken);
        if (token != null) {
            String hash = hashToken(token);
            activeSessions.remove(hash);
            reauthDeadlines.remove(hash);
        }
    }

    @Override
    public void revokeSessionsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        activeSessions.entrySet().removeIf(entry -> {
            if (userId.equals(entry.getValue().actor().userId())) {
                reauthDeadlines.remove(entry.getKey());
                return true;
            }
            return false;
        });
        reauthDeadlines.remove(userId);
    }

    @Override
    public ReauthResponse reauth(String accessToken, ReauthRequest request) {
        String token = extractBearerToken(accessToken);
        if (token == null) {
            throw new InvalidCredentialsException("Yêu cầu phiên làm việc hợp lệ.");
        }
        ActorContext actor = getActorContext(accessToken);
        Instant until = verifyReauthPassword(actor, request);
        reauthDeadlines.put(hashToken(token), until);
        return new ReauthResponse(until);
    }

    @Override
    public boolean isSessionReauthenticated(String accessToken) {
        String token = extractBearerToken(accessToken);
        if (token == null) return false;
        String hash = hashToken(token);
        StoredSession session = activeSessions.get(hash);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            reauthDeadlines.remove(hash);
            return false;
        }
        Instant deadline = reauthDeadlines.get(hash);
        return deadline != null && !Instant.now().isAfter(deadline);
    }

    @Override
    public ReauthResponse reauth(ActorContext actor, ReauthRequest request) {
        if (actor == null) {
            throw new InvalidCredentialsException("Yêu cầu phiên làm việc hợp lệ.");
        }
        Instant until = verifyReauthPassword(actor, request);
        reauthDeadlines.put(actor.userId(), until);
        return new ReauthResponse(until);
    }

    private Instant verifyReauthPassword(ActorContext actor, ReauthRequest request) {
        Objects.requireNonNull(request, "request required");
        if (request.password() == null || request.password().isEmpty()) {
            throw new InvalidCredentialsException("Mật khẩu xác thực lại không hợp lệ.");
        }
        UserAccount account = userAccountRepository.findById(actor.userId())
                .orElseThrow(() -> new ForbiddenException("Không tìm thấy thông tin tài khoản người dùng."));
        if (!account.isActive()) {
            revokeSessionsForUser(actor.userId());
            throw new AccountLockedException(account.getUsername());
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Mật khẩu xác thực lại không chính xác.");
        }
        return Instant.now().plus(5, ChronoUnit.MINUTES);
    }

    @Override
    public ActionGrantResponse issueActionGrant(ActorContext actor, ActionGrantRequest request) {
        Objects.requireNonNull(actor, "actor required");
        Objects.requireNonNull(request, "request required");

        if (!"MANUAL".equals(request.action()) && !"EMERGENCY".equals(request.action())) {
            throw new IllegalArgumentException("action phải là MANUAL hoặc EMERGENCY");
        }
        if (request.reason() == null || request.reason().trim().length() < 10 || request.reason().trim().length() > 500) {
            throw new IllegalArgumentException("reason phải có độ dài từ 10 đến 500 ký tự");
        }
        requireUuid(request.edgeId(), "edgeId");
        requireUuid(request.gateId(), "gateId");
        if ("MANUAL".equals(request.action())) {
            if (request.employeeId() == null || request.employeeId().isBlank()) {
                throw new IllegalArgumentException("MANUAL action grant yêu cầu employeeId");
            }
            requireUuid(request.employeeId(), "employeeId");
        }
        if ("EMERGENCY".equals(request.action()) && request.employeeId() != null && !request.employeeId().isBlank()) {
            throw new IllegalArgumentException("EMERGENCY action grant yêu cầu employeeId phải là null");
        }

        // 1. Kiểm tra permission theo loại action và cấm Auditor
        if (actor.isAuditor()) {
            throw new ForbiddenException("Auditor không được phép yêu cầu Action Grant.");
        }
        String requiredPerm = "MANUAL".equals(request.action()) ? "gate.override" : "gate.emergency";
        if (!actor.permissions().contains(requiredPerm)) {
            throw new ForbiddenException("Không có quyền yêu cầu Action Grant: " + requiredPerm);
        }

        // 2. Re-auth với mật khẩu
        UserAccount account = userAccountRepository.findById(actor.userId())
                .orElseThrow(() -> new ForbiddenException("Không tìm thấy thông tin tài khoản người dùng."));
        if (!account.isActive()) {
            revokeSessionsForUser(actor.userId());
            throw new AccountLockedException(account.getUsername());
        }

        boolean reauthValid = passwordEncoder.matches(request.password(), account.getPasswordHash());
        if (!reauthValid) {
            throw new InvalidCredentialsException("Mật khẩu xác thực lại không chính xác.");
        }

        // 3. Tạo token grant (hạn 60s, đầy đủ claims binding theo CONTRACT-V1 §5)
        Instant expiresAt = Instant.now().plus(60, ChronoUnit.SECONDS);
        Role primaryRole = actor.roles().isEmpty() ? Role.GUARD : actor.roles().get(0);
        String grant = tokenIssuerPort.issueActionGrant(new TokenIssuerPort.ActionGrantClaims(
                actor.userId(),
                request.action(),
                request.edgeId().trim(),
                request.gateId().trim(),
                "MANUAL".equals(request.action()) ? request.employeeId().trim() : null,
                request.reason().trim(),
                primaryRole,
                List.of(requiredPerm)
        ));

        return new ActionGrantResponse(grant, expiresAt);
    }

    private static void requireUuid(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " bắt buộc phải là UUID hợp lệ");
        }
        try {
            UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " phải là UUID hợp lệ", e);
        }
    }

    private static String extractBearerToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7).trim() : accessToken.trim();
        return token.isEmpty() ? null : token;
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
                    perms.add("attendance.read");
                    perms.add("attendance.approve");
                    perms.add("payroll.export");
                    perms.add("report.read");
                    perms.add("report.export");
                }
                case GUARD -> {
                    perms.add("gate.override");
                    perms.add("gate.emergency");
                    perms.add("attendance.read");
                }
                case ADMIN -> {
                    perms.add("system.manage");
                    perms.add("employee.manage");
                    perms.add("access-policy.manage");
                    perms.add("audit.read");
                    perms.add("audit.export");
                }
                case EXECUTIVE -> perms.add("report.read");
                case AUDITOR -> {
                    perms.add("audit.read");
                    perms.add("audit.export");
                }
            }
        }

        // Cấm đặc quyền nếu là Auditor theo CONTRACT-V1.md §3
        if (isAuditor) {
            perms.remove("attendance.approve");
            perms.remove("gate.override");
            perms.remove("gate.emergency");
            perms.remove("credential.manage");
            perms.remove("system.manage");
            perms.remove("employee.manage");
            perms.remove("access-policy.manage");
            perms.remove("payroll.export");
        }

        return new ArrayList<>(perms);
    }
}
