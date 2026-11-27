package vn.tmc.central.qrsecurity.internal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.Authentication;
import vn.tmc.central.qrsecurity.internal.repository.QrCredentialRepository;
import vn.tmc.central.qrsecurity.internal.model.QrCredential;
import vn.tmc.central.qrsecurity.internal.service.*;
import vn.tmc.central.qrsecurity.internal.key.*;
/** PHỤ TRÁCH: Task 8. Fixture keys and volatile credentials only in explicit preview profile. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewQrConfiguration {
 @Bean QrCredentialService previewQr(EmployeeManagement employees,Clock clock){
  var repo=new QrCredentialRepository(){
   private final Map<String,QrCredential> data=new LinkedHashMap<>();
   public synchronized Optional<QrCredential> findById(String id){return Optional.ofNullable(data.get(id));}
   public synchronized Optional<QrCredential> findActiveByEmployeeId(String id){return data.values().stream().filter(c->c.employeeId().equals(id)&&c.state()==QrCredential.State.ACTIVE).findFirst();}
   public synchronized Optional<QrCredential> findLatestByEmployeeId(String id){return data.values().stream().filter(c->c.employeeId().equals(id)).max(Comparator.comparing(QrCredential::createdAt));}
   public synchronized void save(QrCredential c){data.put(c.id(),c);}
   public synchronized void replaceActive(QrCredential old,QrCredential next){data.put(old.id(),old);data.put(next.id(),next);}
  };
  // Seed only an existing preview user, never create credentials for arbitrary users.
  var key=FixtureSigningKeyProvider.generateHandle("preview-only", "ACTIVE",clock.instant().minusSeconds(60),clock.instant().plusSeconds(172800));
  return new QrCredentialService(repo,employees,new FixtureSigningKeyProvider(List.of(key),1),new InMemoryQrRevocationStore(),clock);
 }
 @Bean @org.springframework.core.annotation.Order(20)
 ApplicationRunner seedQr(QrCredentialService qr,Authentication auth,@org.springframework.beans.factory.annotation.Value("${TMC_PREVIEW_PASSWORD}") String password){return args->{
  var session=auth.authenticate(new Authentication.LoginRequest("preview.admin",password));
  try{qr.grantCredential(session.actor(),session.actor().employeeId(),"Local preview seed");}finally{auth.logout(session.accessToken());}
 };}
}
