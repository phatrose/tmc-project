package vn.tmc.central.qrsecurity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.tmc.central.qrsecurity.api.QrCredentialView;
import vn.tmc.central.qrsecurity.api.QrSecurityException;
import vn.tmc.central.qrsecurity.internal.key.FixtureSigningKeyProvider;
import vn.tmc.central.qrsecurity.internal.key.QrSigningKeyHandle;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;
import vn.tmc.central.qrsecurity.internal.repository.QrCredentialRepository;
import vn.tmc.central.qrsecurity.internal.service.InMemoryQrRevocationStore;
import vn.tmc.central.qrsecurity.internal.service.QrCredentialService;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.EmployeeView;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.QrClaims;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.contracts.QrTokenResponse;
import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6. Unit test lifecycle credential và ký JWS theo
 * CONTRACT-V1.md §4/§8 và điều kiện kiểm thử §11 (mục QR phía Central).
 * Chạy: mvn -pl central-backend test (JDK 21). Fixture khóa sinh trong test,
 * KHÔNG commit khóa production.
 */
class QrCredentialServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-17T02:00:00.000Z");
    private static final String FACTORY_ID = "00000000-0000-4000-8000-000000000023";
    private static final String EMPLOYEE_ID = "00000000-0000-4000-8000-000000000011";
    private static final String USER_ID = "00000000-0000-4000-8000-000000000010";
    private static final String KID = "qr-key-2026-11";

    private static final ObjectMapper JSON = new ObjectMapper();

    private StubCredentialRepository repository;
    private InMemoryQrRevocationStore revocationStore;
    private QrCredentialService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = T0;
        Clock clock = new Clock() {
            @Override
            public Instant instant() {
                return now;
            }

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }
        };
        repository = new StubCredentialRepository();
        revocationStore = new InMemoryQrRevocationStore();
        QrSigningKeyHandle handle = FixtureSigningKeyProvider.generateHandle(
                KID, QrKeySetEntry.STATE_ACTIVE, T0.minus(Duration.ofHours(1)), T0.plus(Duration.ofDays(365)));
        EmployeeManagement employees = mock(EmployeeManagement.class);
        when(employees.getEmployee(any(ActorContext.class), anyString())).thenAnswer(invocation ->
                new EmployeeView(invocation.getArgument(1), "EMP-01", "Employee", FACTORY_ID, null, true, 1, T0));
        service = new QrCredentialService(repository, employees,
                new FixtureSigningKeyProvider(List.of(handle), 1L), revocationStore, clock);
        repository.save(new QrCredential("00000000-0000-4000-8000-0000000000c1", EMPLOYEE_ID,
                QrCredential.State.ACTIVE, T0.minus(Duration.ofDays(1)), null));
    }

    private ActorContext hr() {
        return new ActorContext(USER_ID, null, List.of(Role.HR),
                List.of(QrCredentialService.PERMISSION_MANAGE_CREDENTIAL),
                List.of(FACTORY_ID), List.of(), ActorContext.SCOPE_FACTORY);
    }

    private ActorContext employee() {
        return new ActorContext(USER_ID, EMPLOYEE_ID, List.of(Role.EMPLOYEE),
                List.of(), List.of(FACTORY_ID), List.of(), ActorContext.SCOPE_SELF);
    }

    private ActorContext auditorWithManage() {
        return new ActorContext(USER_ID, null, List.of(Role.AUDITOR, Role.HR),
                List.of(QrCredentialService.PERMISSION_MANAGE_CREDENTIAL),
                List.of(FACTORY_ID), List.of(), ActorContext.SCOPE_FACTORY);
    }

    @Test
    void issueReturnsSignedTokenWithContractClaims() throws Exception {
        QrTokenResponse response = service.issue(employee());
        assertNotNull(response.token());
        assertEquals(T0.plusSeconds(QrClaims.DEFAULT_TTL_SECONDS), response.expiresAt());

        String[] parts = JwsCompact.splitCompact(response.token());
        JsonNode header = JSON.readTree(JwsCompact.decodeBase64Url(parts[0]));
        JsonNode claims = JSON.readTree(JwsCompact.decodeBase64Url(parts[1]));
        assertEquals(JwsCompact.ES256_RAW_SIGNATURE_BYTES, JwsCompact.decodeBase64Url(parts[2]).length);
        assertEquals(2, header.size());
        assertEquals("ES256", header.get("alg").asText());
        assertEquals(KID, header.get("kid").asText());
        assertEquals(7, claims.size());
        assertEquals(EMPLOYEE_ID, claims.get("sub").asText());
        assertEquals(QrClaims.ISSUER, claims.get("iss").asText());
        assertEquals(QrClaims.AUDIENCE, claims.get("aud").asText());
        assertEquals(T0.getEpochSecond(), claims.get("iat").asLong());
        assertEquals(claims.get("iat").asLong(), claims.get("nbf").asLong());
        assertEquals(T0.getEpochSecond() + 60, claims.get("exp").asLong());
        assertEquals(4, UUID.fromString(claims.get("jti").asText()).version());
    }

    @Test
    void issueEachTimeGeneratesFreshJti() {
        String first = service.issue(employee()).token();
        String second = service.issue(employee()).token();
        assertNotEquals(first, second);
    }

    @Test
    void issueFailsWithoutActiveCredential() {
        repository.revokeAll(EMPLOYEE_ID, T0);
        QrSecurityException error = assertThrows(QrSecurityException.class, () -> service.issue(employee()));
        assertEquals("INVALID_STATE", error.code());
    }

    @Test
    void issueFailsForActorWithoutEmployeeLink() {
        ActorContext actor = new ActorContext(USER_ID, null, List.of(Role.EMPLOYEE), List.of(),
                List.of(), List.of(), ActorContext.SCOPE_SELF);
        assertEquals("INVALID_STATE",
                assertThrows(QrSecurityException.class, () -> service.issue(actor)).code());
    }

    @Test
    void issueFailsForInactiveEmployee() {
        EmployeeManagement inactiveEmployees = mock(EmployeeManagement.class);
        when(inactiveEmployees.getEmployee(any(ActorContext.class), anyString())).thenAnswer(invocation ->
                new EmployeeView(invocation.getArgument(1), "EMP-01", "Inactive", FACTORY_ID, null, false, 1, T0));
        QrCredentialService inactiveService = new QrCredentialService(repository, inactiveEmployees,
                new FixtureSigningKeyProvider(List.of(FixtureSigningKeyProvider.generateHandle(
                        KID, QrKeySetEntry.STATE_ACTIVE, T0.minusSeconds(3600), T0.plusSeconds(86400))), 1L),
                revocationStore, clockAt(now));
        assertEquals("INVALID_STATE",
                assertThrows(QrSecurityException.class, () -> inactiveService.issue(employee())).code());
    }

    @Test
    void issueAfterReissueSkipsRevocationCutoff() throws Exception {
        service.revokeCredential(hr(), EMPLOYEE_ID, "thu hồi cũ để test cutoff");
        QrCredentialView reissued = service.reissueCredential(hr(), EMPLOYEE_ID, "cấp lại ngay");
        assertEquals(QrCredentialView.STATE_ACTIVE, reissued.state());
        QrTokenResponse token = service.issue(employee());
        JsonNode claims = JSON.readTree(JwsCompact.decodeBase64Url(JwsCompact.splitCompact(token.token())[1]));
        long cutoff = T0.getEpochSecond();
        assertTrue(claims.get("iat").asLong() > cutoff,
                "iat phải sau cutoff để không bị SUB revocation từ chối");
    }

    @Test
    void grantThenDuplicateGrantFailsWithInvalidState() {
        repository.revokeAll(EMPLOYEE_ID, T0);
        QrCredentialView created = service.grantCredential(hr(), EMPLOYEE_ID, "cấp mới theo FR-UQ-02");
        assertEquals(QrCredentialView.STATE_ACTIVE, created.state());
        assertEquals("INVALID_STATE",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(hr(), EMPLOYEE_ID, "cấp nữa")).code());
    }

    @Test
    void grantRequiresManagePermissionAndScope() {
        repository.revokeAll(EMPLOYEE_ID, T0);
        ActorContext noPermission = new ActorContext(USER_ID, null, List.of(Role.GUARD), List.of(),
                List.of(FACTORY_ID), List.of(), ActorContext.SCOPE_FACTORY);
        assertEquals("FORBIDDEN",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(noPermission, EMPLOYEE_ID, "lý do")).code());
        assertEquals("FORBIDDEN",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(auditorWithManage(), EMPLOYEE_ID, "lý do")).code());
        ActorContext wrongFactory = new ActorContext(USER_ID, null, List.of(Role.HR),
                List.of(QrCredentialService.PERMISSION_MANAGE_CREDENTIAL),
                List.of("00000000-0000-4000-8000-000000000099"), List.of(), ActorContext.SCOPE_FACTORY);
        assertEquals("SCOPE_MISMATCH",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(wrongFactory, EMPLOYEE_ID, "lý do")).code());
    }

    @Test
    void revokeMarksRevokedAndPublishesSubRevocation() {
        QrCredentialView revoked = service.revokeCredential(hr(), EMPLOYEE_ID, "nghi việc");
        assertEquals(QrCredentialView.STATE_REVOKED, revoked.state());
        assertNotNull(revoked.revokedAt());
        assertEquals(1, revocationStore.count());
        RevocationChange change = revocationStore.activeChanges().get(0);
        assertEquals(RevocationChange.KIND_SUB, change.kind());
        assertEquals(EMPLOYEE_ID, change.value());
        assertEquals(T0.getEpochSecond(), change.revokedBefore().getEpochSecond());
    }

    @Test
    void reissueRevokesOldAndCreatesNewAtomically() {
        Optional<QrCredential> before = repository.findActiveByEmployeeId(EMPLOYEE_ID);
        assertTrue(before.isPresent());
        QrCredentialView reissued = service.reissueCredential(hr(), EMPLOYEE_ID, "mất thiết bị");
        assertEquals(QrCredentialView.STATE_ACTIVE, reissued.state());
        assertNotEquals(before.get().id(), reissued.id());
        assertEquals(QrCredential.State.REVOKED, repository.findById(before.get().id()).orElseThrow().state());
        assertTrue(repository.findActiveByEmployeeId(EMPLOYEE_ID).isPresent());
        assertEquals(1, revocationStore.count());
    }

    @Test
    void lifecycleValidatesInput() {
        assertEquals("VALIDATION_ERROR",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(hr(), EMPLOYEE_ID, " ")).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(QrSecurityException.class,
                        () -> service.grantCredential(hr(), EMPLOYEE_ID, "r".repeat(501))).code());
        assertEquals("VALIDATION_ERROR",
                assertThrows(QrSecurityException.class, () -> service.grantCredential(hr(), "khong-phai-uuid", "lý do")).code());
    }

    @Test
    void keySetFollowsContractShape() {
        QrKeySet keySet = service.keySet();
        assertEquals(1, keySet.version());
        assertEquals(T0.plus(Duration.ofHours(QrKeySet.TRUST_WINDOW_HOURS)).truncatedTo(ChronoUnit.SECONDS),
                keySet.trustedUntil());
        assertEquals(1, keySet.keys().size());
        QrKeySetEntry entry = keySet.keys().get(0);
        assertEquals(KID, entry.kid());
        assertEquals("EC", entry.kty());
        assertEquals("P-256", entry.crv());
        assertEquals("ES256", entry.alg());
        assertEquals("sig", entry.use());
        assertEquals("ACTIVE", entry.state());
        assertEquals(32, JwsCompact.decodeBase64Url(entry.x()).length);
        assertEquals(32, JwsCompact.decodeBase64Url(entry.y()).length);
    }

    @Test
    void refreshKeysRequiresDeviceBinding() {
        assertEquals("DEVICE_BINDING_MISMATCH",
                assertThrows(QrSecurityException.class, () -> service.refreshKeys(" ", FACTORY_ID)).code());
        QrKeySet keySet = service.refreshKeys("00000000-0000-4000-8000-000000000021", FACTORY_ID);
        assertNotNull(keySet);
        assertEquals(1, keySet.version());
    }

    private Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** Stub repository in-memory; JPA adapter sẽ thay thế, giữ đúng nghĩa port. */
    private static final class StubCredentialRepository implements QrCredentialRepository {
        private final Map<String, QrCredential> byId = new LinkedHashMap<>();
        private final List<QrCredential> order = new ArrayList<>();

        @Override
        public Optional<QrCredential> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<QrCredential> findActiveByEmployeeId(String employeeId) {
            return order.stream()
                    .filter(credential -> credential.employeeId().equals(employeeId))
                    .filter(credential -> credential.state() == QrCredential.State.ACTIVE)
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<QrCredential> findLatestByEmployeeId(String employeeId) {
            return order.stream()
                    .filter(credential -> credential.employeeId().equals(employeeId))
                    .reduce((first, second) -> second);
        }

        @Override
        public void save(QrCredential credential) {
            byId.put(credential.id(), credential);
            order.removeIf(existing -> existing.id().equals(credential.id()));
            order.add(credential);
        }

        @Override
        public void replaceActive(QrCredential revokedOld, QrCredential createdNew) {
            save(revokedOld);
            save(createdNew);
        }

        void revokeAll(String employeeId, Instant at) {
            order.stream()
                    .filter(credential -> credential.employeeId().equals(employeeId))
                    .filter(credential -> credential.state() == QrCredential.State.ACTIVE)
                    .map(credential -> new QrCredential(credential.id(), credential.employeeId(),
                            QrCredential.State.REVOKED, credential.createdAt(), at))
                    .toList()
                    .forEach(this::save);
        }
    }
}
