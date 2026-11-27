package vn.tmc.central.qrsecurity.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.qrsecurity.api.QrCredentialView;
import vn.tmc.central.qrsecurity.internal.service.QrCredentialService;
import vn.tmc.central.usermgmt.api.Authentication;
import vn.tmc.contracts.QrTokenResponse;

/** PHỤ TRÁCH: Task 8. Preview HTTP adapter; no device routes bypassed. */
@RestController
@Profile("integration-preview")
public class PreviewQrController {
    public record ReasonRequest(String reason) {}

    private final Authentication sessions;
    private final QrCredentialService qr;

    public PreviewQrController(Authentication sessions, QrCredentialService qr) {
        this.sessions = sessions;
        this.qr = qr;
    }

    @GetMapping("/api/v1/me/qr-token")
    ResponseEntity<QrTokenResponse> token(@RequestHeader("Authorization") String bearer) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(qr.issue(sessions.getActorContext(bearer)));
    }

    @PostMapping("/api/v1/employees/{id}/qr-credential")
    ResponseEntity<QrCredentialView> grant(@RequestHeader("Authorization") String bearer,
                                           @PathVariable("id") String employeeId,
                                           @RequestBody ReasonRequest body) {
        QrCredentialView created = qr.grantCredential(sessions.getActorContext(bearer), employeeId,
                body == null ? null : body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/api/v1/employees/{id}/qr-credential/revoke")
    ResponseEntity<QrCredentialView> revoke(@RequestHeader("Authorization") String bearer,
                                            @PathVariable("id") String employeeId,
                                            @RequestBody ReasonRequest body) {
        QrCredentialView revoked = qr.revokeCredential(sessions.getActorContext(bearer), employeeId,
                body == null ? null : body.reason());
        return ResponseEntity.ok(revoked);
    }

    @PostMapping("/api/v1/employees/{id}/qr-credential/reissue")
    ResponseEntity<QrCredentialView> reissue(@RequestHeader("Authorization") String bearer,
                                             @PathVariable("id") String employeeId,
                                             @RequestBody ReasonRequest body) {
        QrCredentialView reissued = qr.reissueCredential(sessions.getActorContext(bearer), employeeId,
                body == null ? null : body.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(reissued);
    }
}
