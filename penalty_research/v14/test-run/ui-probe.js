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
      const t=(el.textContent||'').trim().replace(/\s+/g,' ');
      const match=t.match(/^HLASY\s*[:-]?\s*(\d{1,4})$/i);
      if(match){hlasy=match[1];break;}
    }
    if(hlasy)break;
  }
  // Calendar cards are accessible between matches. Read-only: never click/scroll/place bets.
  const schedule=[], scheduleRaw=[], cardKeys=new Set();
  const gameRe=/(?:ZÁPAS|ZAPAS|MATCH)\s*#?\s*(\d{3,9})/i;
  const dateRe=/\b(\d{1,2}\.\d{1,2}\.\d{2,4})\s+(\d{1,2}:\d{2})\b/;
  const fallbackTime=/\b(\d{1,2}:\d{2})\b/;
  for(const root of roots){
    const iter=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
    let node, seen=0;
    while((node=iter.nextNode()) && seen++<12000){
      const txt=(node.nodeValue||'').trim();
      if(!gameRe.test(txt))continue;
      let el=node.parentElement;
      for(let level=0;level<6 && el;level++,el=el.parentElement){
        const t=(el.innerText||el.textContent||'').replace(/\s+/g,' ').trim();
        if(t.length>420)continue;
        const match=t.match(gameRe),date=t.match(dateRe);
        if(!match||!date)continue;
        const after=t.slice(date.index+date[0].length).replace(/[◀▶❮❯]/g,' ').trim();
        const before=t.slice(0,match.index).trim();
        let name=after.match(/\b([A-ZÀ-Ž][A-ZÀ-Ž\- ]{1,28})\b/);
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
    if(gameRe.test(allText))scheduleRaw.push(allText.match(/.{0,70}(?:ZÁPAS|ZAPAS|MATCH)\s*#?\s*\d{3,9}.{0,110}/gi)?.slice(0,12).join(' | ')||'');
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
