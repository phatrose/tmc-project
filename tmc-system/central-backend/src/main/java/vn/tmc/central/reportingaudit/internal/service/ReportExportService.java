package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportExport;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.model.ReportFile;
import vn.tmc.central.reportingaudit.internal.repository.ReportingRepository;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class ReportExportService {
    private final ReportingRepository repository;
    private final ReportingAccess access;
    private final Clock clock;

    public ReportExportService(ReportingRepository repository, ReportingAccess access, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.clock = clock;
    }

    public ReportExport attendance(ActorContext actor, UUID factoryId, String period,
            ReportExport.Format format, UUID idempotencyKey) {
        access.require(actor, "report.export");
        access.factory(actor, factoryId);
        validatePeriod(period);
        Objects.requireNonNull(format);
        return create(actor, ExportJob.Kind.ATTENDANCE, factoryId, period, null, null, format, idempotencyKey,
                fingerprint(factoryId + "\n" + period + "\n" + format));
    }

    public ReportExport audit(ActorContext actor, UUID factoryId, Instant from, Instant to,
            ReportExport.Format format, UUID idempotencyKey) {
        access.require(actor, "audit.export");
        access.audit(actor, factoryId);
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("from/to không hợp lệ");
        if (from.getNano() % 1_000_000 != 0 || to.getNano() % 1_000_000 != 0)
            throw new IllegalArgumentException("from/to phải có độ chính xác milliseconds");
        Objects.requireNonNull(format);
        return create(actor, ExportJob.Kind.AUDIT, factoryId, null, from, to, format, idempotencyKey,
                fingerprint(factoryId + "\n" + from + "\n" + to + "\n" + format));
    }

    private ReportExport create(ActorContext actor, ExportJob.Kind kind, UUID factoryId,
            String period, Instant from, Instant to, ReportExport.Format format, UUID key, String hash) {
        if (key == null || key.version() != 4 || key.variant() != 2) throw new IllegalArgumentException("Idempotency-Key UUIDv4 bắt buộc");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        UUID owner = UUID.fromString(actor.userId());
        String path = kind == ExportJob.Kind.AUDIT ? "/api/v1/audit/exports" : "/api/v1/reports/exports";
        return repository.idempotent(owner, path, key, hash, now, ReportExport.class, () -> {
            var view = new ReportExport(UUID.randomUUID(), ReportExport.State.QUEUED, format, now, null, null);
            repository.create(new ExportJob(view, kind, factoryId, owner, actor, period, from, to, null));
            return view;
        });
    }

    public ReportExport status(ActorContext actor, UUID id, ExportJob.Kind kind) {
        return readable(actor, id, kind).view();
    }

    public ReportFile file(ActorContext actor, UUID id, ExportJob.Kind kind) {
        var job = readable(actor, id, kind);
        if (job.view().state() != ReportExport.State.READY)
            throw new ReportingException("INVALID_STATE", "Tệp chưa sẵn sàng", 409);
        if (job.view().expiresAt() == null || !clock.instant().isBefore(job.view().expiresAt())) notFound();
        return repository.file(id).orElseThrow(() -> new ReportingException("NOT_FOUND", "Không tìm thấy tệp", 404));
    }

    private ExportJob readable(ActorContext actor, UUID id, ExportJob.Kind kind) {
        if (actor == null) throw new ReportingException("UNAUTHENTICATED", "Cần đăng nhập", 401);
        var job = repository.find(id).orElseThrow(() -> new ReportingException("NOT_FOUND", "Không tìm thấy job", 404));
        if (job.kind() != kind) notFound();
        String permission = kind == ExportJob.Kind.AUDIT ? "audit.export" : "report.export";
        if (kind == ExportJob.Kind.AUDIT) access.require(actor, permission);
        boolean owner = actor.userId().equals(job.ownerId().toString());
        if (!owner && !(actor.roles().contains(Role.HR) && actor.permissions().contains(permission))) notFound();
        try { access.factory(actor, job.factoryId()); }
        catch (ReportingException denied) { notFound(); }
        if (!covers(actor, job.actor())) notFound();
        return job;
    }

    private boolean covers(ActorContext current, ActorContext original) {
        return switch (current.scope()) {
            case "GLOBAL" -> current.roles().contains(Role.ADMIN);
            case "FACTORY" -> true;
            case "DEPARTMENT" -> "DEPARTMENT".equals(original.scope())
                    && current.departmentIds().containsAll(original.departmentIds());
            case "SELF" -> "SELF".equals(original.scope()) && Objects.equals(current.employeeId(), original.employeeId());
            default -> false;
        };
    }

    public static YearMonth validatePeriod(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("period phải có dạng YYYY-MM");
        return YearMonth.parse(value);
    }

    private static String fingerprint(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void notFound() { throw new ReportingException("NOT_FOUND", "Không tìm thấy tài nguyên trong scope", 404); }
}
