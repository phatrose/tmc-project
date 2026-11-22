package vn.tmc.central.reportingaudit;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.central.reportingaudit.api.PayrollBatch;
import vn.tmc.central.reportingaudit.internal.adapter.HrPayrollAdapter;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.Request;
import vn.tmc.central.reportingaudit.internal.service.*;
import vn.tmc.contracts.ApiError;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HrPayrollHttpsIT extends ReportingIntegrationFixture {
    @TempDir static Path directory;
    private static SSLContext serverTls,clientTls;
    private HttpsServer server;
    private ExecutorService executor;
    private URI origin;
    private HttpClient client;
    private HrPayrollAdapter adapter;
    private final AtomicReference<HrMockService.Mode> mode=new AtomicReference<>(HrMockService.Mode.SUCCESS);
    private final List<byte[]> bodies=new CopyOnWriteArrayList<>();
    private final CountDownLatch received=new CountDownLatch(1);

    @BeforeAll static void certificates()throws Exception{
        Path storePath=directory.resolve("fixture.p12");String password=UUID.randomUUID().toString();
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var process=new ProcessBuilder(executable,"-genkeypair","-alias","fixture","-keyalg","EC","-groupname","secp256r1",
                "-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-startdate","2026/01/01 00:00:00","-validity","3650",
                "-storetype","PKCS12","-keystore",storePath.toString(),"-storepass",password,"-keypass",password,"-noprompt")
                .redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as(output).isZero();
        var store=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(storePath)){store.load(input,password.toCharArray());}
        var keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keys.init(store,password.toCharArray());
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        serverTls=SSLContext.getInstance("TLS");serverTls.init(keys.getKeyManagers(),trust.getTrustManagers(),null);
        clientTls=SSLContext.getInstance("TLS");clientTls.init(null,trust.getTrustManagers(),null);
    }
    @BeforeEach void startServer()throws Exception{
        var service=new HrMockService(db.named,db.transactions,json,codec,"fixture-only-service-credential",mode::get);
        server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/warmup",exchange->{exchange.sendResponseHeaders(204,-1);exchange.close();});
        server.createContext("/hr/v1/payroll/batches",exchange->{
            int status=200;byte[] response;var current=mode.get();byte[] body=exchange.getRequestBody().readAllBytes();bodies.add(body.clone());
            try {
                var receipt=service.receive(exchange.getRequestHeaders().getFirst("Authorization"),exchange.getRequestHeaders().getFirst("Idempotency-Key"),body);
                received.countDown();response=json.writeValueAsBytes(receipt);
                if(current==HrMockService.Mode.TIMEOUT)Thread.sleep(1200);
            }catch(ReportingException failure){status=failure.status();response=json.writeValueAsBytes(new ApiError(failure.code(),failure.getMessage(),UUID.randomUUID().toString(),failure.retryable()));}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();exchange.close();return;}
            exchange.getResponseHeaders().set("Content-Type","application/json");
            try{exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);}
            catch(IOException disconnected){}finally{exchange.close();}
        });
        server.start();origin=URI.create("https://127.0.0.1:"+server.getAddress().getPort());
        client=HttpClient.newBuilder().sslContext(clientTls).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        client.send(HttpRequest.newBuilder(origin.resolve("/warmup")).GET().build(),HttpResponse.BodyHandlers.discarding());
        adapter=new HrPayrollAdapter(client,origin,"fixture-only-service-credential",Duration.ofSeconds(5),json);
    }
    @AfterEach void stopServer()throws Exception{
        if(server!=null)server.stop(0);if(executor!=null){executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}
    }
    @Test void httpsSuccessDuplicateAndChangedBodyConflict()throws Exception{
        night();var batch=close();byte[] bytes=db.jdbc.queryForObject("SELECT payload FROM export_batches",byte[].class);
        var request=codec.read(bytes);var prepared=new PayrollPayload.Prepared(request,bytes);
        var first=adapter.send(prepared);assertThat(adapter.send(prepared)).isEqualTo(first);
        assertThat(first.batchId()).isEqualTo(batch.id());assertThat(first.acceptedCount()).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM hr_mock_batches",Long.class)).isEqualTo(1);
        var changed=new Request(request.batchId(),request.factoryId(),request.period(),2,request.checksum(),request.records());
        assertThatThrownBy(()->adapter.send(new PayrollPayload.Prepared(changed,json.writeValueAsBytes(changed))))
                .isInstanceOf(HrDeliveryException.class).extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
    }
    @Test void malformedRecordIdentifiersAndNumericStringsAreRejected()throws Exception{
        night();close();byte[] original=db.jdbc.queryForObject("SELECT payload FROM export_batches",byte[].class);
        for(String field:List.of("employeeId","workedMinutes")) {
            var body=json.readTree(original);var row=(com.fasterxml.jackson.databind.node.ObjectNode)body.get("records").get(0);
            if(field.equals("employeeId"))row.putNull(field);else row.put(field,"480");
            var request=HttpRequest.newBuilder(origin.resolve("/hr/v1/payroll/batches"))
                    .header("Authorization","Bearer fixture-only-service-credential").header("Content-Type","application/json")
                    .header("Idempotency-Key",body.get("batchId").asText()).POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            assertThat(client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(400);
        }
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM hr_mock_batches",Long.class)).isZero();
    }
    @Test void partialHttp200IsOnlyPartial(){
        mode.set(HrMockService.Mode.PARTIAL);night();var batch=close();worker(adapter).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.PARTIAL);
        assertThat(payroll.batch(hr,batch.id()).acceptedCount()).isZero();
    }
    @Test void countMismatchHttp200FailsReconciliation(){
        mode.set(HrMockService.Mode.COUNT_MISMATCH);night();var batch=close();worker(adapter).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.FAILED);
    }
    @Test void checksumMismatchHttp200FailsReconciliation(){
        mode.set(HrMockService.Mode.CHECKSUM_MISMATCH);night();var batch=close();worker(adapter).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.FAILED);
    }
    @Test void lostAckAfterHrCommitRetriesIdenticalBytesAndReceivesStoredAck()throws Exception{
        mode.set(HrMockService.Mode.TIMEOUT);night();var batch=close();
        var shortTimeout=new HrPayrollAdapter(client,origin,"fixture-only-service-credential",Duration.ofMillis(250),json);
        worker(shortTimeout).runOnce();assertThat(received.await(5,TimeUnit.SECONDS)).isTrue();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.QUEUED);
        mode.set(HrMockService.Mode.SUCCESS);clock.advance(Duration.ofSeconds(3));worker(adapter).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.EXPORT_SUCCESS);
        assertThat(bodies).hasSize(2);assertThat(bodies.get(0)).isEqualTo(bodies.get(1));
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM hr_mock_batches",Long.class)).isEqualTo(1);
    }
    @Test void rejectsPlainHttpRedirectCapableClientAndWrongCredential(){
        assertThatIllegalArgumentException().isThrownBy(()->new HrPayrollAdapter(client,URI.create("http://localhost"),"secret",Duration.ofSeconds(1),json));
        var redirect=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        assertThatIllegalArgumentException().isThrownBy(()->new HrPayrollAdapter(redirect,origin,"secret",Duration.ofSeconds(1),json));
        night();var batch=close();worker(new HrPayrollAdapter(client,origin,"wrong-fixture-credential",Duration.ofSeconds(5),json)).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.FAILED);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM hr_mock_batches",Long.class)).isZero();
    }
}
