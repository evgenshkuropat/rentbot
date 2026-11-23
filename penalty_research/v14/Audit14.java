import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Append-only evidence, deliberately separate from lossy CSV summary projections. */
public final class Audit14 {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUN = UUID.randomUUID().toString();
    private static final ThreadLocal<Receipt> RECEIPT = new ThreadLocal<>();
    private record Receipt(String utc, long mono) {}
    private static FileChannel channel;
    private static Path journalPath;
    private static String previousHash = "GENESIS";
    private static long seq, previousMono;
    private static Instant previousWall;
    public static synchronized void initialize() throws Exception {
        if (channel != null) return;
        Path dir=Path.of("penalty-evidence-v14"); Files.createDirectories(dir);
        journalPath=dir.resolve("events-"+RUN+".jsonl");
        channel=FileChannel.open(journalPath,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        ObjectNode build=JSON.createObjectNode();
        build.put("collectorVersion","V14");build.put("schemaVersion",1);
        build.put("javaVersion",System.getProperty("java.version"));
        build.put("runId",RUN);build.put("wallClockSynchronized","UNKNOWN");
        build.put("videoBallLaunchMeasured",false);build.put("uiPollIntervalMs",750);
        Path source=Path.of("src/main/java/com/ucl/Main.java");
        if(Files.isRegularFile(source))build.put("mainSourceSha256",sha(Files.readAllBytes(source)));
        Path auditSource=Path.of("src/main/java/com/ucl/Audit14.java");
        if(Files.isRegularFile(auditSource))build.put("auditSourceSha256",sha(Files.readAllBytes(auditSource)));
        Path executable=Path.of(Audit14.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(Files.isRegularFile(executable))build.put("executableSha256",sha(Files.readAllBytes(executable)));
        record("RUN_START",build,0);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            synchronized(Audit14.class) {
                if(channel==null)return;
                record("RUN_END",JSON.createObjectNode(),0);
                try{channel.close();}catch(Exception e){System.err.println("AUDIT CLOSE FAILED: "+e);}
            }
        },"v14-evidence-close"));
    }
    public static void beginReceipt(){RECEIPT.set(new Receipt(Instant.now().toString(),System.nanoTime()));}
    public static Path journalPath(){return journalPath;}
    public static String receivedUtc(){Receipt r=RECEIPT.get();return r==null?Instant.now().toString():r.utc;}
    public static synchronized void record(String type,JsonNode payload,int session){
        try {
            if(channel==null)throw new IllegalStateException("Evidence journal not initialized");
            Instant wall=Instant.now();long mono=System.nanoTime();Receipt receipt=RECEIPT.get();
            ObjectNode body=JSON.createObjectNode();
            body.put("schemaVersion",1);body.put("collectorVersion","V14");body.put("runId",RUN);
            body.put("sessionId",session);body.put("eventSeq",++seq);body.put("kind",type);
            body.put("recordedUtc",wall.toString());body.put("recordedMonotonicNs",mono);
            if(receipt!=null){body.put("receivedUtc",receipt.utc);body.put("receivedMonotonicNs",receipt.mono);}
            if(previousWall!=null){
                double elapsed=(mono-previousMono)/1e9;
                double wallElapsed=java.time.Duration.between(previousWall,wall).toNanos()/1e9;
                body.put("elapsedSincePreviousRecordSec",elapsed);
                body.put("wallMinusMonotonicElapsedSec",wallElapsed-elapsed);
                body.put("possibleCoverageGap",elapsed>5);
            }
            body.set("payload",payload.deepCopy());body.put("previousSha256",previousHash);
            String encoded=JSON.writeValueAsString(body);String digest=sha(encoded.getBytes(StandardCharsets.UTF_8));
            ObjectNode envelope=JSON.createObjectNode();envelope.put("bodyJson",encoded);envelope.put("sha256",digest);
            ByteBuffer bytes=ByteBuffer.wrap((JSON.writeValueAsString(envelope)+"\n").getBytes(StandardCharsets.UTF_8));
            while(bytes.hasRemaining())channel.write(bytes);
            channel.force(false);
            previousHash=digest;previousMono=mono;previousWall=wall;
        }catch(Exception e){
            System.err.println("FATAL: evidence write failed; collecting without evidence is forbidden: "+e);
            Runtime.getRuntime().halt(74);
        }
    }
    public static void market(String utc,String match,String round,String market,String goal,String save,String source,int session){
        ObjectNode p=JSON.createObjectNode();p.put("observationUtc",utc);p.put("matchId",match);p.put("round",round);
        p.put("marketState",market);p.put("goalStatus",goal);p.put("saveStatus",save);p.put("source",source);
        p.put("binarySettlement",("WON".equals(goal)&&"LOST".equals(save))||("LOST".equals(goal)&&"WON".equals(save)));
        record("MARKET_OBSERVATION",p,session);
    }
    public static void conflict(String key,String previous,String current,String source,int session){
        ObjectNode p=JSON.createObjectNode();p.put("key",key);p.put("previousResult",previous);p.put("newResult",current);p.put("source",source);
        p.put("analysisDisposition","QUARANTINE_UNTIL_RECONCILED");record("SETTLEMENT_CONFLICT",p,session);
    }
    public static void connection(String state,String reason,int session){
        ObjectNode p=JSON.createObjectNode();p.put("state",state);p.put("reason",reason);p.put("continuityBroken",true);record("CONNECTION",p,session);
    }
    public static java.util.Set<String> priorQuarantineKeys() throws Exception {
        java.util.Set<String> keys=new java.util.HashSet<>();
        try(var paths=Files.list(Path.of("penalty-evidence-v14"))) {
            for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".jsonl")).toList()) {
                String previous="GENESIS";long expectedSeq=0;
                try(var lines=Files.lines(path,StandardCharsets.UTF_8)) {
                    var it=lines.iterator();
                    while(it.hasNext()) {
                        String line=it.next();JsonNode envelope;
                        try{envelope=JSON.readTree(line);}catch(Exception e){throw new IllegalStateException("Truncated/corrupt journal: "+path,e);}
                        String body=envelope.path("bodyJson").asText();
                        if(!sha(body.getBytes(StandardCharsets.UTF_8)).equals(envelope.path("sha256").asText()))throw new IllegalStateException("Corrupt journal hash: "+path);
                        JsonNode record=JSON.readTree(body);
                        if(!previous.equals(record.path("previousSha256").asText())||record.path("eventSeq").asLong()!=++expectedSeq)throw new IllegalStateException("Broken journal chain: "+path);
                        previous=envelope.path("sha256").asText();
                        if("SETTLEMENT_CONFLICT".equals(record.path("kind").asText()))keys.add(record.path("payload").path("key").asText());
                    }
                }
            }
        }
        return keys;
    }
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
