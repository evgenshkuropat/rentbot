import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Audit14Test {
    static final ObjectMapper J=new ObjectMapper();
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void frame(String state,String gs,String ss,String go,String so)throws Exception{
        ObjectNode goal=J.createObjectNode().put("id","od:match:123/round=1/826").put("status",gs).put("odds",go);
        ObjectNode save=J.createObjectNode().put("id","od:match:123/round=1/827").put("status",ss).put("odds",so);
        ObjectNode group=J.createObjectNode().put("name","Výsledek kola");
        group.putArray("selections").add(J.createObjectNode().put("name","Gól").put("id",goal.path("id").asText())).add(J.createObjectNode().put("name","Zákrok").put("id",save.path("id").asText()));
        ObjectNode market=J.createObjectNode().put("info","1").put("state",state);market.putArray("outcomes").add(goal).add(save);group.putArray("markets").add(market);
        ObjectNode match=J.createObjectNode().put("id","od:match:123");match.putArray("marketGroups").add(group);
        ObjectNode msg=J.createObjectNode().put("type","next");msg.putObject("payload").putObject("data").set("onUpdateMatchLive",match);
        Method m=Main.class.getDeclaredMethod("processFrame",String.class);m.setAccessible(true);Audit14.beginReceipt();m.invoke(null,msg.toString());
    }
    @SuppressWarnings("unchecked") static Map<String,Object> resultMap()throws Exception{
        Field f=Main.class.getDeclaredField("results");f.setAccessible(true);return (Map<String,Object>)f.get(null);
    }
    public static void main(String[] args)throws Exception{
        for(String file:List.of("penalty-odds-v14.csv","penalty-results-v14.csv","penalty-ui-matches-v14.csv"))Files.writeString(Path.of(file),"test_header\n");
        Audit14.initialize();
        frame("OPEN","OPEN","OPEN","1.22","3.8");require(resultMap().isEmpty(),"Open market is not a result");
        frame("CLOSED","WON","LOST","1.22","3.8");require(resultMap().size()==1,"WS settlement must be recorded");
        frame("CLOSED","WON","LOST","1.22","3.8");require(resultMap().size()==1,"Duplicate settlement must not add result");
        frame("CLOSED","LOST","WON","1.22","3.8");require(resultMap().isEmpty(),"Conflict must quarantine trusted result");
        frame("CLOSED","WON","LOST","1.22","3.8");require(resultMap().isEmpty(),"Quarantine must not auto-clear");
        frame("CLOSED","VOID","VOID","1.22","3.8");require(resultMap().isEmpty(),"Nonbinary status must not become GOAL/SAVE");
        // Seed one WS anchor and show that a later UI round is candidate-only.
        Class<?> rr=Class.forName("Main$ResultRow");Constructor<?> ctor=rr.getDeclaredConstructors()[0];ctor.setAccessible(true);
        resultMap().put("123|1",ctor.newInstance(java.time.Instant.now().toString(),"123","1","GOAL","marketGroups"));
        Class<?> oddsClass=Class.forName("Main$OddsRow");Constructor<?> oddsCtor=oddsClass.getDeclaredConstructors()[0];oddsCtor.setAccessible(true);
        Field oddsField=Main.class.getDeclaredField("latestOdds");oddsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Object> oddsMap=(Map<String,Object>)oddsField.get(null);
        oddsMap.put("123|2",oddsCtor.newInstance(java.time.Instant.now().toString(),"123","2","1.5","2.0","marketGroups"));
        Method link=Main.class.getDeclaredMethod("linkUiIfSafe",JsonNode.class,String.class);link.setAccessible(true);
        ArrayNode hits=J.createArrayNode().add(J.createObjectNode().put("index",1).put("result","GOAL").put("sector","5"));
        link.invoke(null,hits,"GOAL:5|");hits.add(J.createObjectNode().put("index",2).put("result","SAVE").put("sector","3"));link.invoke(null,hits,"GOAL:5|SAVE:3|");
        require(!resultMap().containsKey("123|2"),"UI candidate must not enter trusted results");
        require(Files.readString(Path.of("penalty-ui-matches-v14.csv")).contains(",2,SAVE,3,"),"Test must actually generate later-round UI candidate");
        require(Audit14.priorQuarantineKeys().contains("123|1"),"Durable quarantine must survive restart reconstruction");
        Field expression=Main.class.getDeclaredField("UI_EXPRESSION");expression.setAccessible(true);Files.writeString(Path.of("ui-probe.js"),(String)expression.get(null));
        Path journal=Audit14.journalPath();
        String previous="GENESIS";long seq=0;boolean nonbinary=false,conflict=false;
        for(String line:Files.readAllLines(journal,StandardCharsets.UTF_8)){
            JsonNode env=J.readTree(line);String body=env.path("bodyJson").asText();String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            require(sha.equals(env.path("sha256").asText()),"Journal digest mismatch");JsonNode record=J.readTree(body);
            require(record.path("previousSha256").asText().equals(previous),"Broken digest chain");require(record.path("eventSeq").asLong()==++seq,"Sequence discontinuity");previous=sha;
            nonbinary|=body.contains("VOID");conflict|=record.path("kind").asText().equals("SETTLEMENT_CONFLICT");
        }
        require(nonbinary&&conflict,"Raw nonbinary/conflict evidence missing");
        System.out.println("PASS: open/settled/duplicate/conflict/quarantine/nonbinary/UI-isolation/hash-chain; records="+seq);
    }
}
