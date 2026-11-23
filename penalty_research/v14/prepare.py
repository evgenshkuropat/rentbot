from pathlib import Path
import hashlib
src=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector\src\main\java\com\ucl\Main.java')
out=Path(__file__).parent
s=src.read_text(encoding='utf-8');(out/'original-v13.sha256').write_text(hashlib.sha256(src.read_bytes()).hexdigest())
s=s.replace('v13','v14').replace('V13','V14')
s=s.replace('    private static final HttpClient HTTP = HttpClient.newHttpClient();','    private static final class HttpSupport { private static final HttpClient CLIENT = HttpClient.newHttpClient(); }').replace('HTTP.send(', 'HttpSupport.CLIENT.send(').replace('HTTP.newWebSocketBuilder()', 'HttpSupport.CLIENT.newWebSocketBuilder()')
s=s.replace('        loadExisting();','        Audit14.initialize();\n        loadExisting();')
s=s.replace('''                try {
                    JsonNode event = JSON.readTree(text);''','''                try {
                    Audit14.beginReceipt();
                    JsonNode event = JSON.readTree(text);''')
s=s.replace('''        ws.sendText(cmd.toString(), true);''','''        if ("Runtime.evaluate".equals(method)) Audit14.record("UI_POLL_SENT", JSON.createObjectNode().put("commandId", id), sessionId);
        ws.sendText(cmd.toString(), true);''')
s=s.replace('            processVotingProbe(value);','            Audit14.record("UI_POLL", response, sessionId);\n            processVotingProbe(value);')
s=s.replace('            lastWsFrame = Instant.now();','            ObjectNode evidenceFrame=JSON.createObjectNode().put("rawPayload",payload);\n            evidenceFrame.set("parsedMessage",message);\n            Audit14.record("MATCH_FRAME", evidenceFrame, sessionId);\n            lastWsFrame = Instant.now();')
s=s.replace('            String now = Instant.now().toString();\n            for (String source', '            String now = Audit14.receivedUtc();\n            for (String source')
s=s.replace('            if (uiSeen.add(signature.toString())) {','            if (!signature.toString().equals(uiLastSignature)) {\n                uiLastSignature = signature.toString();')
old='''                if (!results.containsKey(k)) {
                    ResultRow row = new ResultRow(now, uiAnchorMatch, round, outcome, "UI_ANCHORED");
                    Files.writeString(RESULTS, String.join(",", now, uiAnchorMatch, round,
                                    outcome, "UI_ANCHORED") + "\\n", StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    results.put(k, row);
                    rebuildJoined();
                }'''
assert old in s;s=s.replace(old,'                // V14: UI_ANCHORED is a candidate only; never a trusted settlement.');s=s.replace('"FIRST_ROUND_ANCHORED", "WS_ROUND1"','"HEURISTIC_UNVERIFIED", "WS_ROUND1_OUTCOME_ONLY"')
s=s.replace('                    boolean available =', '                    boolean available =')
s=s.replace('        String status = market + "|" + goal + "|" + save;', '        Audit14.market(now, matchId, round, market, goal, save, source, sessionId);\n        String status = market + "|" + goal + "|" + save;')
s=s.replace('''                            } else if (!previous.result.equals(winner)) {
                                System.out.printf''','''                            } else if (!previous.result.equals(winner)) {
                                Audit14.conflict(k, previous.result, winner, source, sessionId);
                                quarantined.add(k);
                                results.remove(k);
                                appendCsv(CONFLICTS, now, matchId, round, previous.result, winner, source);
                                changed = true;
                                System.out.printf''')
s=s.replace('    private static final ObjectMapper JSON', '    private static final Set<String> quarantined = new HashSet<>();\n    private static final Path CONFLICTS = Path.of("penalty-conflicts-v14.csv");\n    private static final ObjectMapper JSON')
s=s.replace('        loadExisting();','''        ensureHeader(CONFLICTS, "timestamp,matchId,round,previousResult,newResult,source\\n");
        for(String line:Files.readAllLines(CONFLICTS, StandardCharsets.UTF_8).stream().skip(1).toList()) {
            List<String> fields=csvFields(line);if(fields.size()>=3)quarantined.add(key(fields.get(1),fields.get(2)));
        }
        quarantined.addAll(Audit14.priorQuarantineKeys());
        loadExisting();
        results.keySet().removeAll(quarantined);
        rebuildJoined();''')
s=s.replace('''                        if (winner != null) {
                            ResultRow previous''','''                        if (winner != null && !quarantined.contains(k)) {
                            ResultRow previous''')
s=s.replace('''                        String winner = null;''','''                        Set<String> returnedStates=Set.of("VOID","VOIDED","CANCELLED","CANCELED","REFUNDED","RETURNED");
                        ResultRow priorSettlement=results.get(k);
                        if(priorSettlement!=null && (returnedStates.contains(goalStatus)||returnedStates.contains(saveStatus))) {
                            Audit14.conflict(k,priorSettlement.result,goalStatus+"/"+saveStatus,source,sessionId);
                            quarantined.add(k);results.remove(k);
                            appendCsv(CONFLICTS,now,matchId,round,priorSettlement.result,goalStatus+"/"+saveStatus,source);
                            changed=true;
                        }
                        String winner = null;''')
s=s.replace('''                if (o == null) { noOdds++; quality = "NO_ODDS"; }''','''                if (quarantined.contains(k)) { unknown++; quality = "CONFLICT_QUARANTINE"; }
                else if (o == null) { noOdds++; quality = "NO_ODDS"; }''')
s=s.replace('''                String[] f = line.split(",", -1);
                if (f.length >= 5) results.put(key(f[1], f[2]), new ResultRow(f[0], f[1], f[2], f[3], f[4]));''','''                String[] f = line.split(",", -1);
                if (f.length >= 5 && !"UI_ANCHORED".equals(f[4])) results.put(key(f[1], f[2]), new ResultRow(f[0], f[1], f[2], f[3], f[4]));''')
s=s.replace('                    long seconds = java.time.Duration.between(Instant.parse(o.timestamp), Instant.parse(r.timestamp)).toSeconds();','                    double seconds = java.time.Duration.between(Instant.parse(o.timestamp), Instant.parse(r.timestamp)).toNanos() / 1_000_000_000.0;')
s=s.replace('lead = Long.toString(seconds);','lead = Double.toString(seconds);')
s=s.replace('''        resetUiAnchor();
        sessionEvent("DISCONNECTED", reason);''','''        resetUiAnchor();
        marketStatesSeen.clear();
        Audit14.connection("DISCONNECTED", reason, sessionId);
        sessionEvent("DISCONNECTED", reason);''')
s=s.replace('''        sessionEvent("CONNECTED", "New CDP connection; UI anchor reset");''','''        marketStatesSeen.clear();
        previousVoteSignature = "";
        previousScheduleDiagnostic = "";
        Audit14.connection("CONNECTED", "New connection; continuity broken", sessionId);
        sessionEvent("CONNECTED", "New CDP connection; UI anchor reset");''')
s=s.replace('''          return {found:results.length>0,results,hlasy,needBetForVotes:info,candidates:details,''','''          const videos=[...document.querySelectorAll('video')].map(v=>({currentTime:v.currentTime,readyState:v.readyState,paused:v.paused,ended:v.ended,videoWidth:v.videoWidth,videoHeight:v.videoHeight}));
          return {domObservedUtc:new Date().toISOString(),performanceTimeOrigin:performance.timeOrigin,performanceNow:performance.now(),videos,found:results.length>0,results,hlasy,needBetForVotes:info,candidates:details,''')
assert 'domObservedUtc' in s
observer='''
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
'''
marker="          const videos=[...document.querySelectorAll('video')]"
assert marker in s;s=s.replace(marker,observer+marker)
s=s.replace('return {domObservedUtc:', 'return {domEvidence,domObservedUtc:')
s=s.replace('NOTE: UI first-round anchor is heuristic, not independent validation of later rounds.','NOTE: UI candidates NEVER enter trusted results. Video currentTime is NOT ball-launch time. Audit14 JSONL is authoritative for revisions, nonbinary states and timing.');s=s.replace('UI привязывается только при подтверждённом первом раунде WebSocket.','UI-сопоставления — только непроверенные кандидаты; в results идут только расчёты WS.')
(out/'Main.java').write_text(s,encoding='utf-8')
print('Prepared Main V14, V13 source unchanged')
