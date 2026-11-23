import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final Set<String> quarantined = new HashSet<>();
    private static final Path CONFLICTS = Path.of("penalty-conflicts-v14.csv");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final class HttpSupport { private static final HttpClient CLIENT = HttpClient.newHttpClient(); }
    private static final Path ODDS = Path.of("penalty-odds-v14.csv");
    private static final Path RESULTS = Path.of("penalty-results-v14.csv");
    private static final Path JOINED = Path.of("penalty-joined-v14.csv");
    private static final Path UI = Path.of("penalty-ui-v14.csv");
    private static final Map<Integer, String> pending = new ConcurrentHashMap<>();
    private static final Set<String> uiSeen = ConcurrentHashMap.newKeySet();
    private static volatile WebSocket cdpSocket;
    private static volatile boolean connected = false;
    private static volatile Instant connectedAt = Instant.EPOCH;
    private static volatile Instant lastReconnectAttempt = Instant.EPOCH;
    private static volatile int sessionId = 0;
    private static final Path SESSIONS = Path.of("penalty-sessions-v14.csv");
    private static final Path MOVEMENTS = Path.of("penalty-movements-v14.csv");
    private static final Map<String, OddsRow> firstOdds = new LinkedHashMap<>();
    private static final Map<String, Integer> changeCounts = new LinkedHashMap<>();
    private static final Map<String, OddsRow> latestOdds = new LinkedHashMap<>();
    private static final Map<String, ResultRow> results = new LinkedHashMap<>();
    private static final AtomicInteger nextCommandId = new AtomicInteger(1);
    private static final Path UI_MATCHES = Path.of("penalty-ui-matches-v14.csv");
    private static final Path QUALITY = Path.of("penalty-quality-v14.csv");
    private static final Path SUMMARY = Path.of("penalty-summary-v14.txt");
    private static final AtomicInteger uiPollOutstanding = new AtomicInteger();
    private static volatile Instant lastUiResponse = Instant.EPOCH;
    private static volatile Instant uiPollStarted = Instant.EPOCH;
    private static volatile Instant lastWsFrame = Instant.EPOCH;
    private static int uiSnapshots = 0;
    private static int uiLinkCount = 0;
    private static int uiConflicts = 0;
    private static final Map<String, String> uiMatchResults = new LinkedHashMap<>();
    private static String uiAnchorMatch = null;
    private static String uiAnchorFirst = null;
    private static String uiAnchorSector = null;
    private static String uiLastSignature = "";
    private static boolean uiSawOne = false;
    private static int uiPreviousCount = 0;
    private static Instant uiFirstSeen = null;
    private static final Path MARKET_STATES = Path.of("penalty-market-states-v14.csv");
    private static final Path VOTING = Path.of("penalty-voting-v14.csv");
    private static final Path TARGETS = Path.of("penalty-targets-v14.csv");
    private static final Map<String, String> marketStatesSeen = new ConcurrentHashMap<>();
    private static String previousVoteSignature = "";
    private static final Path SCHEDULE = Path.of("penalty-schedule-v14.csv");
    private static final Path SCHEDULE_DEBUG = Path.of("penalty-schedule-diagnostics-v14.csv");
    private static final Set<String> scheduleSeen = ConcurrentHashMap.newKeySet();
    private static String previousScheduleDiagnostic = "";
    private static int scheduleWritten = 0;
    private static final Path KEEPER_LINKS = Path.of("penalty-keeper-links-v14.csv");
    private static final Path HEALTH = Path.of("penalty-health-v14.csv");
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d.M.yy H:mm");
    private static volatile Process sleepGuard;
    private static int oddsWritten = 0;
    private static int resultsWritten = 0;

    private record OddsRow(String timestamp, String match, String round, String goal, String save, String source) {}
    private record ResultRow(String timestamp, String match, String round, String result, String source) {}

    public static void main(String[] args) throws Exception {
        Audit14.initialize();
        ensureHeader(CONFLICTS, "timestamp,matchId,round,previousResult,newResult,source\n");
        for(String line:Files.readAllLines(CONFLICTS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
            List<String> fields=csvFields(line);if(fields.size()>=3)quarantined.add(key(fields.get(1),fields.get(2)));
        }
        quarantined.addAll(Audit14.priorQuarantineKeys());
        loadExisting();
        results.keySet().removeAll(quarantined);
        rebuildJoined();
        launchSleepGuard();
        Runtime.getRuntime().addShutdownHook(new Thread(Main::writeQualityReport, "quality-on-exit"));
        ensureHeader(KEEPER_LINKS, "websocketMatchId,firstOddsUtc,scheduleMatchNumber,goalkeeper,scheduledLocal,secondsFromSchedule,confidence,note\n");
        ensureHeader(HEALTH, "timestamp,sessionId,cdpConnected,secondsSinceUI,secondsSinceWebSocket,oddsRows,resultRows,scheduleCards\n");
        ensureHeader(SCHEDULE, "detectedAt,sessionId,scheduledLocal,matchNumber,goalkeeper,source,rawText\n");
        ensureHeader(SCHEDULE_DEBUG, "detectedAt,sessionId,hasScheduleText,extractedCount,rawCandidatesJson\n");
        if (Files.exists(SCHEDULE)) {
            for (String line : Files.readAllLines(SCHEDULE, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                List<String> f = csvFields(line);
                if (f.size() >= 5) scheduleSeen.add(f.get(2) + "|" + f.get(3) + "|" + f.get(4));
            }
        }
        ensureHeader(UI, "timestamp,round,result,sector,visibleCount,snapshotKey\n");
        ensureHeader(UI_MATCHES, "timestamp,matchId,round,result,sector,confidence,anchor\n");
        ensureHeader(QUALITY, "matchId,round,hasOdds,hasResult,result,source,oddsBeforeResult,secondsLead,quality\n");
        ensureHeader(SESSIONS, "timestamp,sessionId,event,details\n");
        ensureHeader(MARKET_STATES, "timestamp,sessionId,matchId,round,marketState,goalStatus,saveStatus,oddsAvailable,source\n");
        ensureHeader(VOTING, "timestamp,sessionId,matchIdHint,roundHint,hlasy,uiText,optionsJson,selectedJson,rawCandidatesJson\n");
        ensureHeader(TARGETS, "timestamp,sessionId,matchIdHint,roundHint,selectedSector,actualBallSector,keeperSector,evidence,detailsJson\n");
        ensureHeader(MOVEMENTS, "timestamp,sessionId,matchId,round,oldGoal,oldSave,newGoal,newSave,goalDelta,saveDelta,changeNumber,source\n");
        if (Files.exists(MOVEMENTS)) {
            for (String line : Files.readAllLines(MOVEMENTS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] f = line.split(",", -1);
                if (f.length >= 12) changeCounts.merge(key(f[2], f[3]), 1, Integer::sum);
            }
        }
        if (Files.exists(SESSIONS)) {
            for (String line : Files.readAllLines(SESSIONS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] f = line.split(",", -1);
                if (f.length >= 2) try { sessionId = Math.max(sessionId, Integer.parseInt(f[1])); }
                catch (NumberFormatException ignored) {}
            }
        }
        try { connect(); } catch (Exception e) {
            System.out.println("Ожидание Chrome: " + e.getMessage());
        }
        ScheduledExecutorService reconnector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "penalty-reconnector"); t.setDaemon(true); return t;
        });
        reconnector.scheduleWithFixedDelay(Main::checkConnection, 5, 5, TimeUnit.SECONDS);
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "penalty-ui-poller"); t.setDaemon(true); return t;
        });
        poller.scheduleAtFixedRate(Main::pollUi, 1, 750, TimeUnit.MILLISECONDS);
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "penalty-quality-reporter"); t.setDaemon(true); return t;
        });
        reporter.scheduleAtFixedRate(Main::writeQualityReport, 10, 30, TimeUnit.SECONDS);
        reporter.scheduleAtFixedRate(Main::writeHealth, 15, 60, TimeUnit.SECONDS);
        System.out.println("=== PENALTY ARENA COLLECTOR V14 ===");
        System.out.println("Коэффициенты: " + ODDS.toAbsolutePath());
        System.out.println("Результаты:   " + RESULTS.toAbsolutePath());
        System.out.println("Общая база:  " + JOINED.toAbsolutePath());
        System.out.println("Виджет UI:    " + UI.toAbsolutePath());
        System.out.println("UI + матч:     " + UI_MATCHES.toAbsolutePath());
        System.out.println("Отчёт качества: " + QUALITY.toAbsolutePath());
        System.out.println("Сводка:        " + SUMMARY.toAbsolutePath());
        System.out.println("Сессии:       " + SESSIONS.toAbsolutePath());
        System.out.println("Изменения:    " + MOVEMENTS.toAbsolutePath());
        System.out.println("Рынки:        " + MARKET_STATES.toAbsolutePath());
        System.out.println("Голосование:  " + VOTING.toAbsolutePath());
        System.out.println("Цели:         " + TARGETS.toAbsolutePath());
        System.out.println("Расписание:   " + SCHEDULE.toAbsolutePath());
        System.out.println("Диагностика расписания: " + SCHEDULE_DEBUG.toAbsolutePath());
        System.out.println("Привязка вратарей: " + KEEPER_LINKS.toAbsolutePath());
        System.out.println("Состояние сборщика: " + HEALTH.toAbsolutePath());
        System.out.println("UI проверяется каждые 750 мс; ставки НЕ размещаются.");
        System.out.println("UI-сопоставления — только непроверенные кандидаты; в results идут только расчёты WS.");
        System.out.println("Сбор запущен. Для остановки нажми Stop в IntelliJ.");
        new CountDownLatch(1).await();
    }


    private static void launchSleepGuard() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return;
        try {
            Path guard = Path.of("penalty-sleep-guard-v14.ps1").toAbsolutePath();
            if (!Files.isRegularFile(guard)) {
                System.out.println("SLEEP GUARD | файл не найден: " + guard + "; запускай защиту вручную");
                return;
            }
            sleepGuard = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", guard.toString(),
                    "-ParentPid", Long.toString(ProcessHandle.current().pid()))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(
                            Path.of("penalty-sleep-guard-v14.log").toFile())).start();
            System.out.println("SLEEP GUARD | запущен процесс " + sleepGuard.pid()
                    + " (проверить журнал penalty-sleep-guard-v14.log)");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (sleepGuard != null && sleepGuard.isAlive()) sleepGuard.destroy();
            }, "stop-sleep-guard"));
        } catch (Exception e) { System.out.println("SLEEP GUARD | не удалось запустить: " + e.getMessage()); }
    }

    private static synchronized void writeHealth() {
        try {
            Instant now = Instant.now();
            String uiAge = lastUiResponse.equals(Instant.EPOCH) ? "" : Long.toString(Duration.between(lastUiResponse, now).toSeconds());
            String wsAge = lastWsFrame.equals(Instant.EPOCH) ? "" : Long.toString(Duration.between(lastWsFrame, now).toSeconds());
            appendCsv(HEALTH, now.toString(), Integer.toString(sessionId), Boolean.toString(connected),
                    uiAge, wsAge, Integer.toString(latestOdds.size()), Integer.toString(results.size()), Integer.toString(scheduleSeen.size()));
        } catch (Exception e) { System.out.println("HEALTH | " + e.getMessage()); }
    }

    private static synchronized void rebuildKeeperLinks() {
        try {
            record Card(Instant start, String displayedId, String keeper, String local) {}
            List<Card> cards = new ArrayList<>();
            if (Files.exists(SCHEDULE)) {
                for (String line : Files.readAllLines(SCHEDULE, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                    // Generated CSV fields are escaped. Use a compact CSV parser for quoted values.
                    List<String> fields = csvFields(line);
                    if (fields.size() < 5) continue;
                    try {
                        Instant start = LocalDateTime.parse(fields.get(2), DATE_FMT).atZone(PRAGUE).toInstant();
                        cards.add(new Card(start, fields.get(3), fields.get(4), fields.get(2)));
                    } catch (Exception ignored) {}
                }
            }
            cards.sort(Comparator.comparing(Card::start));
            Map<String, Instant> matchFirst = new TreeMap<>();
            for (OddsRow o : firstOdds.values()) {
                try {
                    Instant t = Instant.parse(o.timestamp);
                    matchFirst.merge(o.match, t, (a,b) -> a.isBefore(b) ? a : b);
                } catch (Exception ignored) {}
            }
            StringBuilder out = new StringBuilder("websocketMatchId,firstOddsUtc,scheduleMatchNumber,goalkeeper,scheduledLocal,secondsFromSchedule,confidence,note\n");
            int assigned = 0;
            for (Map.Entry<String,Instant> row : matchFirst.entrySet()) {
                Instant when = row.getValue();
                Card chosen = null;
                for (Card card : cards) {
                    if (!card.start().isAfter(when)) chosen = card; else break;
                }
                String id="", keeper="", local="", elapsed="", confidence="UNMATCHED", note="No reliable schedule window";
                if (chosen != null) {
                    long sec = Duration.between(chosen.start(), when).toSeconds();
                    // Never attribute a keeper if the schedule is too old or capture starts far into a gap.
                    // These are temporal hypotheses, NOT verified mappings of displayed and internal IDs.
                    if (sec >= 0 && sec <= 21*60) {
                        id=chosen.displayedId(); keeper=chosen.keeper(); local=chosen.local();
                        elapsed=Long.toString(sec); confidence="TIME_WINDOW_ONLY";
                        note="Unverified: bookmaker match number differs from WebSocket ID";
                        assigned++;
                    }
                }
                out.append(String.join(",", csv(row.getKey()),csv(when.toString()),csv(id),csv(keeper),
                        csv(local),csv(elapsed),csv(confidence),csv(note))).append('\n');
            }
            atomicWrite(KEEPER_LINKS, out.toString());
            // Report is regenerated because a later schedule capture can refine previous windows.
        } catch (Exception e) { System.out.println("KEEPER LINKS | " + e.getMessage()); }
    }

    private static List<String> csvFields(String row) {
        List<String> fields = new ArrayList<>();
        StringBuilder cell = new StringBuilder(); boolean quoted=false;
        for (int i=0;i<row.length();i++) {
            char c=row.charAt(i);
            if(c=='"') { if(quoted && i+1<row.length() && row.charAt(i+1)=='"') {cell.append('"');i++;} else quoted=!quoted; }
            else if(c==',' && !quoted) { fields.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        fields.add(cell.toString()); return fields;
    }

    private static synchronized void sessionEvent(String event, String details) {
        try {
            String clean = details.replace(',', ';').replace('\n', ' ');
            Files.writeString(SESSIONS, String.join(",", Instant.now().toString(),
                            Integer.toString(sessionId), event, clean) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) { System.out.println("Session log: " + e.getMessage()); }
    }

    private static synchronized void markDisconnected(String reason) {
        if (!connected) return;
        connected = false;
        cdpSocket = null;
        pending.clear();
        uiPollOutstanding.set(0);
        resetUiAnchor();
        marketStatesSeen.clear();
        Audit14.connection("DISCONNECTED", reason, sessionId);
        sessionEvent("DISCONNECTED", reason);
        System.out.println("CDP отключён: " + reason + ". Переподключение каждые 5 секунд.");
    }

    private static void checkConnection() {
        try {
            if (connected && cdpSocket != null && !cdpSocket.isOutputClosed()) {
                // A sleeping computer can leave a stale socket apparently open.
                if (Instant.now().isBefore(lastUiResponse.plusSeconds(20))
                        || Instant.now().isBefore(connectedAt.plusSeconds(25))) return;
                markDisconnected("UI response missing >20 seconds");
            }
            if (!connected) connect();
        } catch (Exception e) { System.out.println("RECONNECT | " + e.getMessage()); }
    }

    private static synchronized void connect() throws Exception {
        if (connected) return;
        if (Instant.now().isBefore(lastReconnectAttempt.plusSeconds(4))) return;
        lastReconnectAttempt = Instant.now();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9222/json"))
                .timeout(java.time.Duration.ofSeconds(4)).GET().build();
        HttpResponse<String> response = HttpSupport.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Chrome CDP HTTP " + response.statusCode());
        String debuggerUrl = null;
        for (JsonNode target : JSON.readTree(response.body())) {
            String url = target.path("url").asText();
            if (url.contains("bifrost.oddin.gg") && url.contains("penalty-arena")
                    && target.hasNonNull("webSocketDebuggerUrl")) {
                debuggerUrl = target.path("webSocketDebuggerUrl").asText(); break;
            }
        }
        if (debuggerUrl == null) throw new IllegalStateException("Iframe игры не найден; открой Penalty Arena в Chrome");
        WebSocket ws = HttpSupport.CLIENT.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(5))
                .buildAsync(URI.create(debuggerUrl), new Listener()).join();
        cdpSocket = ws;
        connected = true;
        connectedAt = Instant.now();
        lastUiResponse = connectedAt;
        pending.clear();
        uiPollOutstanding.set(0);
        resetUiAnchor();
        sessionId++;
        marketStatesSeen.clear();
        previousVoteSignature = "";
        previousScheduleDiagnostic = "";
        Audit14.connection("CONNECTED", "New connection; continuity broken", sessionId);
        sessionEvent("CONNECTED", "New CDP connection; UI anchor reset");
        sendCommand("Network.enable", JSON.createObjectNode());
        sendCommand("Runtime.enable", JSON.createObjectNode());
        System.out.println("SESSION #" + sessionId + " | CDP подключён; новые данные собираются");
    }

    private static void sendCommand(String method, ObjectNode params) {
        WebSocket ws = cdpSocket;
        if (ws == null || !connected) return;
        int id = nextCommandId.getAndIncrement();
        ObjectNode cmd = JSON.createObjectNode();
        cmd.put("id", id); cmd.put("method", method); cmd.set("params", params);
        if ("Runtime.evaluate".equals(method)) pending.put(id, "ui");
        if ("Runtime.evaluate".equals(method)) Audit14.record("UI_POLL_SENT", JSON.createObjectNode().put("commandId", id), sessionId);
        ws.sendText(cmd.toString(), true);
    }

    // PASSIVE ONLY. DOM metadata is evidence, not video-derived ball trajectory.
    // Coordinates are deliberately left blank when no authoritative DOM field exists.
    private static final String UI_EXPRESSION = """
        (() => {
          const roots=[], visited=new Set(), matches=[];
          function walk(root,depth) {
            if (!root||visited.has(root)||depth>7) return;
            visited.add(root);roots.push(root);
            for(const el of root.querySelectorAll?.('*')||[]) {
              if(el.shadowRoot) walk(el.shadowRoot,depth+1);
            }
          }
          walk(document,0);
          const sel='[class*="round-status--goal"], [class*="round-status--save"]';
          const active=roots.map(root=>({root,hits:[...root.querySelectorAll(sel)]})).sort((a,b)=>b.hits.length-a.hits.length)[0];
          const results=(active?.hits||[]).map((el,i)=>({
            index:i+1, result:String(el.className).includes('round-status--goal')?'GOAL':'SAVE',
            sector:(el.textContent||'').trim().slice(0,40)
          }));
          const details=[];
          for(const root of roots) {
            for(const el of root.querySelectorAll('[class],[data-sector],[data-target],[data-position],[aria-label]')) {
              if(details.length>=70) break;
              const hint=[String(el.className||''),el.getAttribute('aria-label')||'',
                el.getAttribute('data-sector')||'',el.getAttribute('data-target')||'',
                el.getAttribute('data-position')||''].join(' ');
              if(!/(vote|hlas|target|aim|sector|goal-area|shot-position|trajectory|kick-choice)/i.test(hint))continue;
              const r=el.getBoundingClientRect();
              if(!r.width||!r.height)continue;
              details.push({tag:el.tagName,cls:String(el.className||'').slice(0,110),
                text:(el.textContent||'').trim().slice(0,65),
                label:(el.getAttribute('aria-label')||'').slice(0,65),
                sector:el.getAttribute('data-sector')||'',
                target:el.getAttribute('data-target')||'',
                position:el.getAttribute('data-position')||'',
                pressed:el.getAttribute('aria-pressed')||'',selected:el.getAttribute('data-selected')||'',
                x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)});
            }
          }
          let hlasy='';
          for(const root of roots){
            const els=root.querySelectorAll('[class],button,span,div');
            for(const el of els){
              if(el.children.length>3)continue;
              const t=(el.textContent||'').trim().replace(/\\s+/g,' ');
              const match=t.match(/^HLASY\\s*[:-]?\\s*(\\d{1,4})$/i);
              if(match){hlasy=match[1];break;}
            }
            if(hlasy)break;
          }
          // Calendar cards are accessible between matches. Read-only: never click/scroll/place bets.
          const schedule=[], scheduleRaw=[], cardKeys=new Set();
          const gameRe=/(?:ZÁPAS|ZAPAS|MATCH)\\s*#?\\s*(\\d{3,9})/i;
          const dateRe=/\\b(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})\\s+(\\d{1,2}:\\d{2})\\b/;
          const fallbackTime=/\\b(\\d{1,2}:\\d{2})\\b/;
          for(const root of roots){
            const iter=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
            let node, seen=0;
            while((node=iter.nextNode()) && seen++<12000){
              const txt=(node.nodeValue||'').trim();
              if(!gameRe.test(txt))continue;
              let el=node.parentElement;
              for(let level=0;level<6 && el;level++,el=el.parentElement){
                const t=(el.innerText||el.textContent||'').replace(/\\s+/g,' ').trim();
                if(t.length>420)continue;
                const match=t.match(gameRe),date=t.match(dateRe);
                if(!match||!date)continue;
                const after=t.slice(date.index+date[0].length).replace(/[◀▶❮❯]/g,' ').trim();
                const before=t.slice(0,match.index).trim();
                let name=after.match(/\\b([A-ZÀ-Ž][A-ZÀ-Ž\\- ]{1,28})\\b/);
                // Prefer a leaf inside the card containing the short uppercase goalkeeper name.
                let names=[...el.querySelectorAll('div,span,strong,b,h2,h3,h4,p')].filter(x=>x.children.length===0)
                  .map(x=>(x.textContent||'').trim()).filter(x=>/^[A-ZÀ-Ž][A-ZÀ-Ž -]{2,24}$/.test(x) && !/^(ZÁPAS|MATCH|LIVE|VSADIT)$/.test(x));
                const goalkeeper=names.find(x=>!/^ZÁPAS/.test(x)) || (name?name[1].trim():'');
                if(!goalkeeper) continue;
                const key=date[1]+' '+date[2]+'|'+match[1]+'|'+goalkeeper;
                if(!cardKeys.has(key)) {
                  cardKeys.add(key);
                  schedule.push({matchNumber:match[1],scheduledLocal:date[1]+' '+date[2],goalkeeper,rawText:t.slice(0,320)});
                }
                break;
              }
              if(schedule.length>=80)break;
            }
            if(schedule.length>=80)break;
            const allText=(root.body?.innerText||root.textContent||'').slice(0,150000);
            if(gameRe.test(allText))scheduleRaw.push(allText.match(/.{0,70}(?:ZÁPAS|ZAPAS|MATCH)\\s*#?\\s*\\d{3,9}.{0,110}/gi)?.slice(0,12).join(' | ')||'');
          }
          const info=roots.some(r=>(r.textContent||'').includes('VSAĎTE PRO HLASY'));

          // Mutation delivery time, not physical kick time. No clicks, votes or video capture.
          const audit=window.__penaltyAuditV14 ||= {epoch:String(performance.timeOrigin),sequence:0,events:[],roots:new Set(),signature:null,bufferEvictions:0};
          const capture=(kind)=>{
            const choices=[...audit.roots].map(root=>[...root.querySelectorAll(sel)]).sort((a,b)=>b.length-a.length);
            const hits=(choices[0]||[]).map((el,i)=>({index:i+1,result:String(el.className).includes('round-status--goal')?'GOAL':'SAVE',sector:(el.textContent||'').trim().slice(0,40)}));
            const signature=JSON.stringify(hits);
            if(signature===audit.signature)return;
            audit.signature=signature;
            audit.events.push({sequence:++audit.sequence,kind,utc:new Date().toISOString(),performanceNow:performance.now(),results:hits});
            if(audit.events.length>500){audit.events.shift();audit.bufferEvictions++;}
          };
          for(const root of roots)if(!audit.roots.has(root)){
            audit.roots.add(root);
            const observer=new MutationObserver(()=>capture('MUTATION_DELIVERY'));
            observer.observe(root,{subtree:true,childList:true,attributes:true,attributeFilter:['class'],characterData:true});
          }
          capture('BASELINE_OR_NEW_ROOT');
          const domEvidence={browserEpoch:audit.epoch,latestSequence:audit.sequence,bufferEvictions:audit.bufferEvictions,events:audit.events.slice(-50)};
          const videos=[...document.querySelectorAll('video')].map(v=>({currentTime:v.currentTime,readyState:v.readyState,paused:v.paused,ended:v.ended,videoWidth:v.videoWidth,videoHeight:v.videoHeight}));
          return {domEvidence,domObservedUtc:new Date().toISOString(),performanceTimeOrigin:performance.timeOrigin,performanceNow:performance.now(),videos,found:results.length>0,results,hlasy,needBetForVotes:info,candidates:details,
            schedule,scheduleRaw:scheduleRaw.slice(0,4)};
        })()
        """;

    private static void pollUi() {
        try {
            if (uiPollOutstanding.get() != 0 && Instant.now().isAfter(uiPollStarted.plusSeconds(8))) {
                uiPollOutstanding.set(0);
                pending.clear();
                System.out.println("UI WARNING | CDP response timeout; retrying");
            }
            if (!connected || cdpSocket == null) return;
            if (!uiPollOutstanding.compareAndSet(0, 1)) return;
            uiPollStarted = Instant.now();
            ObjectNode params = JSON.createObjectNode();
            params.put("expression", UI_EXPRESSION);
            params.put("returnByValue", true);
            params.put("awaitPromise", false);
            sendCommand("Runtime.evaluate", params);
        } catch (Exception e) {
            uiPollOutstanding.set(0);
            System.out.println("UI polling: " + e.getMessage());
        }
    }

    private static synchronized void processUiResponse(JsonNode response) {
        try {
            JsonNode value = response.path("result").path("result").path("value");
            lastUiResponse = Instant.now();
            Audit14.record("UI_POLL", response, sessionId);
            processVotingProbe(value);
            processScheduleProbe(value);
            if (!value.path("found").asBoolean(false)) return;
            JsonNode hits = value.path("results");
            if (!hits.isArray()) return;
            if (hits.isEmpty()) return;
            StringBuilder signature = new StringBuilder();
            for (JsonNode hit : hits) signature.append(hit.path("result").asText()).append(':')
                    .append(hit.path("sector").asText()).append('|');
            String snapshotKey = Integer.toHexString(signature.toString().hashCode());
            linkUiIfSafe(hits, signature.toString());
            if (!signature.toString().equals(uiLastSignature)) {
                uiLastSignature = signature.toString();
                uiSnapshots++;
                System.out.printf("UI | видимых результатов: %d | снимок %s%n", hits.size(), snapshotKey);
                for (JsonNode hit : hits) {
                    String sector = hit.path("sector").asText().replace(',', ' ').replace('\n', ' ');
                    String line = String.join(",", Instant.now().toString(),
                            hit.path("index").asText(), hit.path("result").asText(),
                            sector, Integer.toString(hits.size()), snapshotKey) + "\n";
                    Files.writeString(UI, line, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка UI: " + e.getMessage());
        }
    }

    // The bookmaker's displayed #7242 is NOT necessarily its internal WebSocket matchId.
    // We deliberately do not merge these IDs without independent verification.
    private static synchronized void processScheduleProbe(JsonNode value) {
        try {
            if (!value.isObject()) return;
            JsonNode cards = value.path("schedule");
            int extracted = 0;
            if (cards.isArray()) for (JsonNode card : cards) {
                String number = card.path("matchNumber").asText("");
                String at = card.path("scheduledLocal").asText("");
                String name = card.path("goalkeeper").asText("");
                if (!number.matches("\\d{3,9}") || at.isBlank() || name.isBlank()) continue;
                extracted++;
                if (!scheduleSeen.add(at + "|" + number + "|" + name)) continue;
                appendCsv(SCHEDULE, Instant.now().toString(), Integer.toString(sessionId),
                        at, number, name, "DOM_SCHEDULE_CARD", card.path("rawText").asText(""));
                scheduleWritten++;
                System.out.printf("SCHEDULE | #%s | %s | %s%n", number, at, name);
            }
            JsonNode raw = value.path("scheduleRaw");
            String diagnostic = raw.isArray() ? raw.toString() : "[]";
            String fingerprint = extracted + "|" + diagnostic;
            if (!"[]".equals(diagnostic) && !fingerprint.equals(previousScheduleDiagnostic)) {
                previousScheduleDiagnostic = fingerprint;
                appendCsv(SCHEDULE_DEBUG, Instant.now().toString(), Integer.toString(sessionId),
                        "true", Integer.toString(extracted), diagnostic);
            }
        } catch (Exception e) { System.out.println("SCHEDULE ERROR | " + e.getMessage()); }
    }

    private static synchronized void processVotingProbe(JsonNode value) {
        try {
            if (!value.isObject()) return;
            String hlas = value.path("hlasy").asText("");
            JsonNode candidates = value.path("candidates");
            String candidateJson = candidates.isArray() ? candidates.toString() : "[]";
            // Only log a transition. Repeated UI polls cannot flood the file.
            String signature = hlas + "|" + value.path("needBetForVotes").asBoolean(false) + "|" + candidateJson;
            if (signature.equals(previousVoteSignature)) return;
            previousVoteSignature = signature;
            if (hlas.isEmpty() && "[]".equals(candidateJson)) return;
            String now = Instant.now().toString();
            String contextMatch = uiAnchorMatch == null ? "" : uiAnchorMatch;
            String contextRound = ""; // No reliable round for arbitrary live voting UI.
            appendCsv(VOTING, now, Integer.toString(sessionId), contextMatch, contextRound,
                    hlas, Boolean.toString(value.path("needBetForVotes").asBoolean(false)),
                    "[]", "[]", candidateJson);
            // No selected/actual ball/keeper assertions without verified source fields.
            if (!hlas.isEmpty()) System.out.println("HLASY | remaining=" + hlas);
        } catch (Exception e) { System.out.println("Ошибка голосования: " + e.getMessage()); }
    }

    private static String csv(String s) {
        return "\"" + (s == null ? "" : s).replace("\"", "\"\"")
                .replace("\r", " ").replace("\n", " ") + "\"";
    }
    private static void appendCsv(Path file, String... fields) throws Exception {
        StringJoiner joiner = new StringJoiner(",");
        for (String f : fields) joiner.add(csv(f));
        Files.writeString(file, joiner + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void recordMarketState(String now, String matchId, String round,
                                          String market, String goal, String save, String source) throws Exception {
        String key = key(matchId, round);
        Audit14.market(now, matchId, round, market, goal, save, source, sessionId);
        String status = market + "|" + goal + "|" + save;
        if (status.equals(marketStatesSeen.put(key, status))) return;
        boolean available = "OPEN".equals(market) && "OPEN".equals(goal) && "OPEN".equals(save);
        appendCsv(MARKET_STATES, now, Integer.toString(sessionId), matchId, round,
                market, goal, save, Boolean.toString(available), source);
        if (!available && !"WON".equals(goal) && !"LOST".equals(goal)) {
            System.out.printf("MARKET | %s/%s | %s %s %s%n",matchId,round,market,goal,save);
        }
    }

    // UI не содержит идентификатор матча. Привязка допускается только если
    // первый UI-удар был увиден отдельно, затем совпал с единственным
    // подтверждённым первым раундом из WebSocket в коротком временном окне.
    private static synchronized void resetUiAnchor() {
        uiAnchorMatch = null;
        uiAnchorFirst = null;
        uiAnchorSector = null;
        uiLastSignature = "";
        uiSawOne = false;
        uiFirstSeen = null;
        uiPreviousCount = 0;
    }

    private static synchronized void linkUiIfSafe(JsonNode hits, String signature) {
        try {
            if (hits.size() < 1 || hits.size() > 5) return;
            if (hits.size() < uiPreviousCount) resetUiAnchor();
            uiPreviousCount = hits.size();
            String first = hits.get(0).path("result").asText();
            String firstSector = hits.get(0).path("sector").asText();
            if (!first.equals("GOAL") && !first.equals("SAVE")) return;
            if (uiAnchorFirst != null && (!first.equals(uiAnchorFirst)
                    || !firstSector.equals(uiAnchorSector))) resetUiAnchor();
            if (hits.size() == 1 && !uiSawOne) {
                resetUiAnchor();
                uiSawOne = true;
                uiFirstSeen = Instant.now();
                uiAnchorFirst = first;
                uiAnchorSector = firstSector;
            }
            // Подключение посреди матча: только диагностические UI-записи.
            if (!uiSawOne || uiFirstSeen == null) return;
            if (Instant.now().isAfter(uiFirstSeen.plusSeconds(360))) {
                resetUiAnchor();
                return;
            }
            if (uiAnchorMatch == null) {
                List<String> candidates = new ArrayList<>();
                for (ResultRow r : results.values()) {
                    if (!"1".equals(r.round) || !first.equals(r.result)) continue;
                    Instant t = Instant.parse(r.timestamp);
                    long delta = Math.abs(java.time.Duration.between(t, uiFirstSeen).toSeconds());
                    if (delta > 100) continue;
                    // Require a captured first-round price too; excludes old settled matches.
                    if (!latestOdds.containsKey(key(r.match, "1"))) continue;
                    candidates.add(r.match);
                }
                if (candidates.size() != 1) return;
                uiAnchorMatch = candidates.get(0);
                System.out.println("UI ANCHOR | матч " + uiAnchorMatch + " | первый раунд " + first);
            }
            // If already recorded, do not overwrite a contradictory outcome.
            for (JsonNode hit : hits) {
                String round = hit.path("index").asText();
                String outcome = hit.path("result").asText();
                if (!round.matches("[1-5]") || !(outcome.equals("GOAL") || outcome.equals("SAVE"))) continue;
                String k = key(uiAnchorMatch, round);
                ResultRow ws = results.get(k);
                if (ws != null && !ws.result.equals(outcome)) {
                    uiConflicts++;
                    System.out.println("UI CONFLICT | матч " + uiAnchorMatch + " раунд " + round);
                    resetUiAnchor();
                    return;
                }
            }
            for (JsonNode hit : hits) {
                String round = hit.path("index").asText();
                String outcome = hit.path("result").asText();
                if (!round.matches("[1-5]") || !(outcome.equals("GOAL") || outcome.equals("SAVE"))) continue;
                String k = key(uiAnchorMatch, round);
                if (uiMatchResults.containsKey(k)) continue;
                // Do not claim an outcome when no corresponding odds were observed.
                if (!latestOdds.containsKey(k)) continue;
                String now = Instant.now().toString();
                String sector = hit.path("sector").asText().replace(',', ' ').replace('\n', ' ');
                String line = String.join(",", now, uiAnchorMatch, round, outcome, sector,
                        "HEURISTIC_UNVERIFIED", "WS_ROUND1_OUTCOME_ONLY") + "\n";
                Files.writeString(UI_MATCHES, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                uiMatchResults.put(k, outcome);
                uiLinkCount++;
                // This is a sector reported by the result UI, NOT proven video flight location.
                appendCsv(TARGETS, now, Integer.toString(sessionId), uiAnchorMatch, round,
                        "", "", "", "UI_RESULT_SECTOR_ONLY",
                        "{\"uiReportedSector\":\"" + sector.replace("\"", "") + "\",\"result\":\"" + outcome + "\"}");
                // V14: UI_ANCHORED is a candidate only; never a trusted settlement.
                System.out.println("UI MATCH | матч " + uiAnchorMatch + " | раунд " + round + " | " + outcome);
            }
        } catch (Exception e) {
            System.out.println("UI linking: " + e.getMessage());
        }
    }

    private static void loadExisting() throws Exception {
        ensureHeader(ODDS, "timestamp,matchId,round,goalOdds,saveOdds,source\n");
        ensureHeader(RESULTS, "timestamp,matchId,round,result,source\n");
        if (Files.exists(ODDS)) {
            for (String line : Files.readAllLines(ODDS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] f = line.split(",", -1);
                if (f.length >= 6) {
                    String k = key(f[1], f[2]);
                    OddsRow row = new OddsRow(f[0], f[1], f[2], f[3], f[4], f[5]);
                    firstOdds.putIfAbsent(k, row);
                    latestOdds.put(k, row);
                }
            }
        }
        if (Files.exists(RESULTS)) {
            for (String line : Files.readAllLines(RESULTS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] f = line.split(",", -1);
                if (f.length >= 5 && !"UI_ANCHORED".equals(f[4])) results.put(key(f[1], f[2]), new ResultRow(f[0], f[1], f[2], f[3], f[4]));
            }
        }
        if (Files.exists(UI_MATCHES)) {
            for (String line : Files.readAllLines(UI_MATCHES, StandardCharsets.UTF_8).stream().skip(1).toList()) {
                String[] f = line.split(",", -1);
                if (f.length >= 4) uiMatchResults.put(key(f[1], f[2]), f[3]);
            }
        }
        rebuildJoined();
        System.out.printf("Восстановлено: %d рынков, %d результатов.%n", latestOdds.size(), results.size());
    }

    private static void ensureHeader(Path path, String header) throws Exception {
        if (!Files.exists(path) || Files.size(path) == 0) {
            Files.writeString(path, header, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static String key(String match, String round) { return match + "|" + round; }

    private static String decode(String id) {
        try { return new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return id; }
    }

    private static String matchNumber(String id) {
        String decoded = decode(id);
        int at = decoded.lastIndexOf("od:match:");
        if (at < 0) return "";
        String tail = decoded.substring(at + "od:match:".length());
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) end++;
        return end > 0 ? tail.substring(0, end) : "";
    }

    // ID имеет вид .../826; дополнительно проверяем совпадение идентификатора матча и раунда.
    private static String outcomeCode(String id) {
        String decoded = decode(id);
        int slash = decoded.lastIndexOf('/');
        return slash >= 0 ? decoded.substring(slash + 1) : "";
    }

    private static JsonNode findOutcome(JsonNode selections, JsonNode outcomes, String name,
                                        String matchId, String round) {
        if (!selections.isArray() || !outcomes.isArray()) return null;
        for (JsonNode selection : selections) {
            if (!name.equals(selection.path("name").asText())) continue;
            String sid = decode(selection.path("id").asText());
            String code = outcomeCode(selection.path("id").asText());
            if (code.isEmpty() || !sid.contains("od:match:" + matchId + "/")
                    || !sid.contains("round=" + round + "/")) continue;
            for (JsonNode outcome : outcomes) {
                String oid = decode(outcome.path("id").asText());
                if (code.equals(outcomeCode(outcome.path("id").asText()))
                        && oid.contains("od:match:" + matchId + "/")
                        && oid.contains("round=" + round + "/")) return outcome;
            }
        }
        return null;
    }

    private static String validOdds(JsonNode outcome) {
        if (outcome == null || !outcome.hasNonNull("odds")) return null;
        String raw = outcome.path("odds").asText();
        try {
            double number = Double.parseDouble(raw);
            return Double.isFinite(number) && number > 1.0 ? raw : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static synchronized void processFrame(String payload) {
        try {
            JsonNode message = JSON.readTree(payload);
            if (!"next".equals(message.path("type").asText())) return;
            JsonNode match = message.path("payload").path("data").path("onUpdateMatchLive");
            if (!match.isObject()) return;
            ObjectNode evidenceFrame=JSON.createObjectNode().put("rawPayload",payload);
            evidenceFrame.set("parsedMessage",message);
            Audit14.record("MATCH_FRAME", evidenceFrame, sessionId);
            lastWsFrame = Instant.now();
            String matchId = matchNumber(match.path("id").asText());
            if (matchId.isEmpty()) return;
            boolean changed = false;
            String now = Audit14.receivedUtc();
            for (String source : new String[]{"marketGroups", "mainMarketGroups"}) {
                JsonNode groups = match.path(source);
                if (!groups.isArray()) continue;
                for (JsonNode group : groups) {
                    if (!"Výsledek kola".equals(group.path("name").asText())) continue;
                    JsonNode selections = group.path("selections");
                    JsonNode markets = group.path("markets");
                    if (!markets.isArray()) continue;
                    for (JsonNode market : markets) {
                        String round = market.path("info").asText();
                        if (!round.matches("[1-5]")) continue;
                        JsonNode outcomes = market.path("outcomes");
                        JsonNode goal = findOutcome(selections, outcomes, "Gól", matchId, round);
                        JsonNode save = findOutcome(selections, outcomes, "Zákrok", matchId, round);
                        if (goal == null || save == null) continue;
                        String k = key(matchId, round);
                        String marketState = market.path("state").asText();
                        recordMarketState(now, matchId, round, marketState,
                                goal.path("status").asText(), save.path("status").asText(), source);
                        if ("OPEN".equals(marketState)
                                && "OPEN".equals(goal.path("status").asText())
                                && "OPEN".equals(save.path("status").asText())) {
                            String goalOdds = validOdds(goal);
                            String saveOdds = validOdds(save);
                            if (goalOdds != null && saveOdds != null) {
                                OddsRow previous = latestOdds.get(k);
                                if (previous == null || !previous.goal.equals(goalOdds) || !previous.save.equals(saveOdds)) {
                                    OddsRow row = new OddsRow(now, matchId, round, goalOdds, saveOdds, source);
                                    Files.writeString(ODDS, String.join(",", now, matchId, round, goalOdds, saveOdds, source) + "\n",
                                            StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                                    if (previous != null) {
                                        int change = changeCounts.merge(k, 1, Integer::sum);
                                        double gd = Double.parseDouble(goalOdds) - Double.parseDouble(previous.goal);
                                        double sd = Double.parseDouble(saveOdds) - Double.parseDouble(previous.save);
                                        String movement = String.join(",", now, Integer.toString(sessionId), matchId, round,
                                                previous.goal, previous.save, goalOdds, saveOdds,
                                                Double.toString(gd), Double.toString(sd), Integer.toString(change), source) + "\n";
                                        Files.writeString(MOVEMENTS, movement, StandardCharsets.UTF_8,
                                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                        System.out.printf("MOVE | матч %s | раунд %s | гол %s -> %s | сейв %s -> %s%n",
                                                matchId, round, previous.goal, goalOdds, previous.save, saveOdds);
                                    }
                                    firstOdds.putIfAbsent(k, row);
                                    latestOdds.put(k, row);
                                    changed = true;
                                    System.out.printf("ODDS #%d | матч %s | раунд %s | гол %s | сейв %s%n",
                                            ++oddsWritten, matchId, round, goalOdds, saveOdds);
                                }
                            }
                        }
                        String goalStatus = goal.path("status").asText();
                        String saveStatus = save.path("status").asText();
                        Set<String> returnedStates=Set.of("VOID","VOIDED","CANCELLED","CANCELED","REFUNDED","RETURNED");
                        ResultRow priorSettlement=results.get(k);
                        if(priorSettlement!=null && (returnedStates.contains(goalStatus)||returnedStates.contains(saveStatus))) {
                            Audit14.conflict(k,priorSettlement.result,goalStatus+"/"+saveStatus,source,sessionId);
                            quarantined.add(k);results.remove(k);
                            appendCsv(CONFLICTS,now,matchId,round,priorSettlement.result,goalStatus+"/"+saveStatus,source);
                            changed=true;
                        }
                        String winner = null;
                        if ("WON".equals(goalStatus) && "LOST".equals(saveStatus)) winner = "GOAL";
                        if ("LOST".equals(goalStatus) && "WON".equals(saveStatus)) winner = "SAVE";
                        if (winner != null && !quarantined.contains(k)) {
                            ResultRow previous = results.get(k);
                            if (previous == null) {
                                ResultRow row = new ResultRow(now, matchId, round, winner, source);
                                Files.writeString(RESULTS, String.join(",", now, matchId, round, winner, source) + "\n",
                                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                                results.put(k, row);
                                changed = true;
                                System.out.printf("RESULT #%d | матч %s | раунд %s | %s%n",
                                        ++resultsWritten, matchId, round, winner);
                            } else if (!previous.result.equals(winner)) {
                                Audit14.conflict(k, previous.result, winner, source, sessionId);
                                quarantined.add(k);
                                results.remove(k);
                                appendCsv(CONFLICTS, now, matchId, round, previous.result, winner, source);
                                changed = true;
                                System.out.printf("ВНИМАНИЕ: противоречивый результат матч %s раунд %s: %s / %s%n",
                                        matchId, round, previous.result, winner);
                            }
                        }
                    }
                }
            }
            if (changed) rebuildJoined();
        } catch (Exception e) {
            System.out.println("Ошибка обработки сообщения: " + e.getMessage());
        }
    }

    private static void rebuildJoined() throws Exception {
        StringBuilder csv = new StringBuilder("matchId,round,goalOdds,saveOdds,result,oddsTimestamp,resultTimestamp,firstGoalOdds,firstSaveOdds,firstOddsTimestamp,changes\n");
        Set<String> keys = new LinkedHashSet<>(latestOdds.keySet());
        keys.addAll(results.keySet());
        for (String k : keys) {
            OddsRow o = latestOdds.get(k);
            ResultRow r = results.get(k);
            OddsRow first = firstOdds.get(k);
            String match = o != null ? o.match : r.match;
            String round = o != null ? o.round : r.round;
            csv.append(String.join(",", match, round,
                    o != null ? o.goal : "", o != null ? o.save : "",
                    r != null ? r.result : "UNKNOWN",
                    o != null ? o.timestamp : "", r != null ? r.timestamp : "",
                    first != null ? first.goal : "", first != null ? first.save : "",
                    first != null ? first.timestamp : "", Integer.toString(changeCounts.getOrDefault(k, 0)))).append('\n');
        }
        Path tmp = JOINED.resolveSibling(JOINED.getFileName().toString() + ".tmp");
        Files.writeString(tmp, csv.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, JOINED, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, JOINED, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static synchronized void writeQualityReport() {
        try {
            StringBuilder rows = new StringBuilder("matchId,round,hasOdds,hasResult,result,source,oddsBeforeResult,secondsLead,quality\n");
            Set<String> keys = new TreeSet<>(latestOdds.keySet());
            keys.addAll(results.keySet());
            int paired = 0, unknown = 0, noOdds = 0, suspicious = 0, ws = 0, ui = 0;
            Set<String> matches = new HashSet<>();
            for (String k : keys) {
                OddsRow o = latestOdds.get(k);
                ResultRow r = results.get(k);
                String match = o != null ? o.match : r.match;
                String round = o != null ? o.round : r.round;
                matches.add(match);
                String lead = "";
                String before = "UNKNOWN";
                String quality;
                if (quarantined.contains(k)) { unknown++; quality = "CONFLICT_QUARANTINE"; }
                else if (o == null) { noOdds++; quality = "NO_ODDS"; }
                else if (r == null) { unknown++; quality = "UNKNOWN_RESULT"; }
                else {
                    double seconds = java.time.Duration.between(Instant.parse(o.timestamp), Instant.parse(r.timestamp)).toNanos() / 1_000_000_000.0;
                    lead = Double.toString(seconds);
                    before = Boolean.toString(seconds >= 0);
                    if (seconds < 0) { suspicious++; quality = "ODDS_AFTER_RESULT"; }
                    else { paired++; quality = "PAIRED"; }
                }
                if (r != null) {
                    if (r.source.equals("UI_ANCHORED")) ui++;
                    else ws++;
                }
                rows.append(String.join(",", match, round, Boolean.toString(o != null),
                        Boolean.toString(r != null), r == null ? "UNKNOWN" : r.result,
                        r == null ? "" : r.source, before, lead, quality)).append('\n');
            }
            atomicWrite(QUALITY, rows.toString());
            rebuildKeeperLinks();
            String summary = "Penalty Arena Collector V14 - " + Instant.now() + "\n"
                    + "Matches: " + matches.size() + "\n"
                    + "Rounds with odds: " + latestOdds.size() + "\n"
                    + "Paired with nonnegative lead: " + paired + "\n"
                    + "Unknown results: " + unknown + "\n"
                    + "Results without odds: " + noOdds + "\n"
                    + "Odds after result (CHECK): " + suspicious + "\n"
                    + "Results from UI: " + ui + "\n"
                    + "Results from WebSocket: " + ws + "\n"
                    + "UI snapshots observed: " + uiSnapshots + "\n"
                    + "UI matched this run: " + uiLinkCount + "\n"
                    + "UI conflicts: " + uiConflicts + "\n"
                    + "CDP connected: " + connected + "\n"
                    + "Current session: " + sessionId + "\n"
                    + "Odds changes this run: " + changeCounts.values().stream().mapToInt(Integer::intValue).sum() + "\n"
                    + "Schedule cards captured this run: " + scheduleWritten + "\n"
                    + "Keeper mapping: TIME_WINDOW_ONLY, unverified; see penalty-keeper-links-v14.csv\n"
                    + "Sleep guard process: " + (sleepGuard != null && sleepGuard.isAlive()) + "\n"
                    + "Last UI response: " + lastUiResponse + "\n"
                    + "Last WebSocket match: " + lastWsFrame + "\n"
                    + "NOTE: UI candidates NEVER enter trusted results. Video currentTime is NOT ball-launch time. Audit14 JSONL is authoritative for revisions, nonbinary states and timing.\n";
            atomicWrite(SUMMARY, summary);
            System.out.printf("QUALITY | matches %d | paired %d | unknown %d | no odds %d | suspicious %d%n",
                    matches.size(), paired, unknown, noOdds, suspicious);
        } catch (Exception e) {
            System.out.println("Ошибка отчёта качества: " + e.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws Exception {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) {
            System.out.println("Подключение к Chrome установлено.");
            webSocket.request(1);
        }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    Audit14.beginReceipt();
                    JsonNode event = JSON.readTree(text);
                    if (event.has("id")) {
                        int id = event.path("id").asInt();
                        if (pending.remove(id) != null) {
                            uiPollOutstanding.set(0);
                            processUiResponse(event);
                        }
                    }
                    if ("Network.webSocketFrameReceived".equals(event.path("method").asText())) {
                        JsonNode frame = event.path("params").path("response");
                        if (frame.path("opcode").asInt(-1) == 1) processFrame(frame.path("payloadData").asText());
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка CDP: " + e.getMessage());
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("Ошибка соединения CDP: " + error.getMessage());
            if (webSocket == cdpSocket) markDisconnected("CDP error: " + error.getMessage());
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("CDP закрыт: " + statusCode + " " + reason);
            if (webSocket == cdpSocket) markDisconnected("CDP closed: " + statusCode);
            return CompletableFuture.completedFuture(null);
        }
    }
}