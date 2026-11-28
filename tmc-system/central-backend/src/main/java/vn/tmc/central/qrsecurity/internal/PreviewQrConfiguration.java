package vn.tmc.central.qrsecurity.internal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.Authentication;
import vn.tmc.central.qrsecurity.internal.repository.JdbcQrCredentialRepository;
import vn.tmc.central.qrsecurity.internal.repository.QrCredentialRepository;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;
import vn.tmc.central.qrsecurity.internal.service.*;
import vn.tmc.central.qrsecurity.internal.key.*;
/** PHỤ TRÁCH: Task 8 — tích hợp với Task 6. QR credentials & revocations lưu PostgreSQL (V011); signing key vẫn là fixture trong preview. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewQrConfiguration {
 @Bean QrCredentialService previewQr(
         EmployeeManagement employees,
         Clock clock,
         @Autowired(required = false) JdbcTemplate jdbcTemplate,
         @Autowired(required = false) PlatformTransactionManager txManager,
         @org.springframework.beans.factory.annotation.Value("${TMC_QR_KEYSTORE_PATH:}") String keystorePath
 ){
  QrCredentialRepository repo = jdbcTemplate != null
          ? new JdbcQrCredentialRepository(jdbcTemplate, txManager)
          : new QrCredentialRepository(){
   private final Map<String,QrCredential> data=new LinkedHashMap<>();
   public synchronized Optional<QrCredential> findById(String id){return Optional.ofNullable(data.get(id));}
   public synchronized Optional<QrCredential> findActiveByEmployeeId(String id){return data.values().stream().filter(c->c.employeeId().equals(id)&&c.state()==QrCredential.State.ACTIVE).findFirst();}
   public synchronized Optional<QrCredential> findLatestByEmployeeId(String id){return data.values().stream().filter(c->c.employeeId().equals(id)).max(Comparator.comparing(QrCredential::createdAt));}
   public synchronized void save(QrCredential c){data.put(c.id(),c);}
   public synchronized void replaceActive(QrCredential old,QrCredential next){data.put(old.id(),old);data.put(next.id(),next);}
  };
  QrRevocationStore revocationStore = jdbcTemplate != null
          ? new JdbcQrRevocationStore(jdbcTemplate)
          : new InMemoryQrRevocationStore();
  // DATA-05: dùng FileBackedSigningKeyProvider ngoài Git nếu cấu hình TMC_QR_KEYSTORE_PATH;
  // ngược lại dùng thư mục tạm hệ thống trong integration-preview để sống qua restart tiến trình.
  java.nio.file.Path resolvedKeystore = (keystorePath != null && !keystorePath.isBlank())
          ? java.nio.file.Path.of(keystorePath)
          : java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tmc-preview-keystore", "qr-signing-keys.json");
  QrSigningKeyProvider keyProvider = new FileBackedSigningKeyProvider(resolvedKeystore, clock);
  return new QrCredentialService(repo,employees,keyProvider,revocationStore,clock);
 }
 @Bean @org.springframework.core.annotation.Order(20)
 ApplicationRunner seedQr(QrCredentialService qr,Authentication auth,@org.springframework.beans.factory.annotation.Value("${TMC_PREVIEW_PASSWORD:123456}") String password){return args->{
  var admin=auth.authenticate(new Authentication.LoginRequest("preview.admin",password));
  try{
   ensureCredential(qr,admin.actor(),admin.actor().employeeId());
   for(var username:List.of("admin","hr","guard","emp")){
    var user=auth.authenticate(new Authentication.LoginRequest(username,password));
    try{ensureCredential(qr,admin.actor(),user.actor().employeeId());}finally{auth.logout(user.accessToken());}
   }
  }finally{auth.logout(admin.accessToken());}
 };}
 private static void ensureCredential(QrCredentialService qr, vn.tmc.central.usermgmt.api.ActorContext admin, String employeeId){
  if (employeeId == null || employeeId.isBlank()) return;
  try { qr.grantCredential(admin, employeeId, "Local preview seed"); }
  catch (RuntimeException ignored) { /* đã có ACTIVE credential trong PostgreSQL sau restart */ }
 }
}
