"""Reproducible Penalty Arena audit. Raw exports are read-only; seed=20261104."""
from pathlib import Path
import sys, json, hashlib, math, itertools, collections, html, datetime, warnings
sys.path.insert(0,str(Path(__file__).parent/'_libs'))
import numpy as np
import pandas as pd
from scipy import stats
from scipy.special import expit, logit
ROOT=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector')
OUT=Path(__file__).parent/'output'; OUT.mkdir(exist_ok=True)
RNG=np.random.default_rng(20261104)
KEY=['matchId','round']; inventory=[]; frames={}
for version in [11,12,13]:
 for path in sorted(ROOT.glob(f'*-v{version}.csv')):
  before=path.read_bytes(); df=pd.read_csv(path,dtype=str,keep_default_na=False)
  inventory.append(dict(file=path.name,bytes=len(before),sha256=hashlib.sha256(before).hexdigest(),rows=len(df),exact_duplicates=int(df.duplicated().sum()),version=version))
  df['version']=str(version); df['source_file']=path.name; df['source_line']=np.arange(len(df))+2
  kind=path.name.removeprefix('penalty-').removesuffix(f'-v{version}.csv')
  frames.setdefault(kind,[]).append(df)
D={k:pd.concat(v,ignore_index=True) for k,v in frames.items()}
pd.DataFrame(inventory).to_csv(OUT/'source_manifest.csv',index=False,encoding='utf-8-sig')
def dt(x): return pd.to_datetime(x,format='mixed',utc=True,errors='coerce')
def save(df,name): df.to_csv(OUT/(name+'.csv'),index=False,encoding='utf-8-sig')
def wilson(y,n):
 if not n:return (np.nan,np.nan)
 p=y/n; z=1.95996398454; d=1+z*z/n; c=(p+z*z/(2*n))/d; h=z*math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d
 return c-h,c+h
def boot_ci(d,values,B=2999):
 a=d.assign(_v=values).groupby('matchId')._v.agg(['sum','count']).to_numpy(); vals=[]
 for start in range(0,B,150):
  ii=RNG.integers(0,len(a),size=(min(150,B-start),len(a))); z=a[ii].sum(axis=1); vals.extend(z[:,0]/z[:,1])
 return np.quantile(vals,[.025,.975]).tolist()
def residual_test(d,p):
 a=d.assign(_r=d.y.to_numpy()-np.asarray(p)).groupby('matchId')._r.agg(['sum','count']).to_numpy(); n=len(d); m=len(a)
 effect=float(np.mean(d.y.to_numpy()-np.asarray(p)))
 se=np.sqrt(m/(m-1)*np.sum((a[:,0]-a[:,1]*effect)**2))/n if m>1 else np.nan
 t=effect/se
 return effect,se,float(2*stats.t.sf(abs(t),m-1))
audit={}
res=D['results'].copy(); res['t']=dt(res.timestamp)
valid=res.result.isin(['GOAL','SAVE'])&res['round'].isin(list('12345'))&res.t.notna()&res.matchId.str.fullmatch(r'\d+')
save(res[~valid],'invalid_results');res=res[valid]
conf=res.groupby(KEY).result.nunique(); bad=set(conf[conf>1].index)
audit['raw_result_rows']=len(res); audit['result_duplicate_key_rows']=int(res.duplicated(KEY).sum());audit['conflicting_result_keys']=len(bad)
save(res[res.set_index(KEY).index.isin(bad)],'conflicting_results')
res=res[~res.set_index(KEY).index.isin(bad)].sort_values(['t','version']).drop_duplicates(KEY)
# Independently settled WS states are stronger than a UI inference. Preserve provenance.
ms=D['market-states'].copy(); ms['t']=dt(ms.timestamp)
ms['settlement']=np.where((ms.goalStatus=='WON')&(ms.saveStatus=='LOST'),'GOAL',np.where((ms.goalStatus=='LOST')&(ms.saveStatus=='WON'),'SAVE',''))
ws=ms[ms.settlement!=''].copy(); wc=ws.groupby(KEY).settlement.nunique(); wsbad=set(wc[wc>1].index)
audit['conflicting_ws_keys']=len(wsbad)
ws=ws[~ws.set_index(KEY).index.isin(wsbad)].sort_values('t').drop_duplicates(KEY)
check=res.merge(ws[KEY+['settlement']],on=KEY,how='inner'); audit['results_vs_market_disagreements']=int((check.result!=check.settlement).sum());audit['independent_settlement_overlap']=len(check)
bad|=wsbad;bad|=set(check[check.result!=check.settlement].set_index(KEY).index)
res=res[~res.set_index(KEY).index.isin(bad)]
res['result_source']=res.source; res['result_timestamp']=res.t;res['result_file']=res.source_file;res['result_line']=res.source_line
odds=D['odds'].copy();odds['t']=dt(odds.timestamp)
for c in ['goalOdds','saveOdds']: odds[c]=pd.to_numeric(odds[c],errors='coerce')
ov=odds.t.notna()&odds.goalOdds.gt(1)&odds.saveOdds.gt(1)&np.isfinite(odds.goalOdds)&np.isfinite(odds.saveOdds)&odds['round'].isin(list('12345'))
audit['invalid_odds_rows']=int((~ov).sum()); save(odds[~ov],'invalid_odds'); odds=odds[ov]
audit['duplicate_odds_events']=int(odds.duplicated(KEY+['timestamp','goalOdds','saveOdds']).sum())
odds=odds.drop_duplicates(KEY+['timestamp','goalOdds','saveOdds'])
o=odds.merge(res[KEY+['result_timestamp']],on=KEY,how='left');o['lead']=(o.result_timestamp-o.t).dt.total_seconds()
audit['odds_events_after_result']=int(o.lead.lt(0).sum()); save(o[o.lead.lt(0)],'post_result_odds')
pre=o[o.lead.ge(0)].sort_values('t'); last=pre.drop_duplicates(KEY,keep='last'); first=pre.drop_duplicates(KEY)
pick=last[KEY+['goalOdds','saveOdds','t','lead','source_file','source_line']].rename(columns={'t':'odds_timestamp','source_file':'odds_file','source_line':'odds_line'})
clean=res.merge(pick,on=KEY,how='left')
clean=clean.merge(first[KEY+['goalOdds','saveOdds','t']].rename(columns={'goalOdds':'firstGoalOdds','saveOdds':'firstSaveOdds','t':'first_odds_timestamp'}),on=KEY,how='left')
ui=D['ui-matches'].copy();ui['t']=dt(ui.timestamp)
uc=ui.groupby(KEY).agg(result_count=('result','nunique'),sector_count=('sector','nunique'))
u_bad=set(uc[(uc.result_count>1)|(uc.sector_count>1)].index);audit['conflicting_ui_keys']=len(u_bad)
ui=ui[~ui.set_index(KEY).index.isin(u_bad)].sort_values('t').drop_duplicates(KEY)
clean=clean.merge(ui[KEY+['sector','result','confidence','timestamp']].rename(columns={'result':'ui_result','confidence':'sector_confidence','timestamp':'ui_timestamp'}),on=KEY,how='left')
audit['ui_result_disagreements']=int((clean.ui_result.notna()&(clean.result!=clean.ui_result)).sum())
clean.loc[clean.ui_result.notna()&(clean.result!=clean.ui_result),'sector']=np.nan
clean['ws_verified']=clean.set_index(KEY).index.isin(set(ws.set_index(KEY).index))
clean['y']=(clean.result=='GOAL').astype(int);clean['round_num']=clean['round'].astype(int)
clean['local_time']=clean.result_timestamp.dt.tz_convert('Europe/Prague');clean['day']=clean.local_time.dt.strftime('%Y-%m-%d')
clean['split']=np.where(clean.version.isin(['11','12']),'historical',np.where(clean.day.ge('2026-10-29'),'validation','v13_boundary'))
clean['strict']=clean.goalOdds.notna()&clean.lead.ge(5)
clean['p_goal']=(1/clean.goalOdds)/(1/clean.goalOdds+1/clean.saveOdds)
clean['p_additive']=(1/clean.goalOdds)+(.5*(1-1/clean.goalOdds-1/clean.saveOdds))
clean['overround']=1/clean.goalOdds+1/clean.saveOdds-1
clean['return_goal']=clean.y*clean.goalOdds-1;clean['return_save']=(1-clean.y)*clean.saveOdds-1
links=D['keeper-links'].copy();links=links.drop_duplicates('websocketMatchId').rename(columns={'websocketMatchId':'matchId'})
clean=clean.merge(links[['matchId','goalkeeper','scheduleMatchNumber','secondsFromSchedule','confidence']].rename(columns={'confidence':'keeper_confidence'}),on='matchId',how='left')
clean=clean.sort_values(['result_timestamp','matchId','round_num']).reset_index(drop=True)
audit['unique_valid_results']=len(clean);audit['no_pre_result_odds']=int(clean.goalOdds.isna().sum());audit['strict_rows']=int(clean.strict.sum());audit['all_keys']=len(pd.concat([D['joined'][KEY],odds[KEY],res[KEY]]).drop_duplicates());audit['known_results_only']=len(res)
save(clean,'clean_rounds'); save(clean[~clean.strict],'excluded_from_primary')
# Do not count derived joined / quality / targets as new events.
q=D['quality']; audit['quality_export_counts']=q.groupby(['version','quality']).size().to_dict().__str__()
joined=D['joined']; j=joined.merge(clean[KEY+['result']],on=KEY,how='inner',suffixes=('_export','_rebuilt'))
audit['joined_result_disagreements']=int((j.result_export!=j.result_rebuilt).sum())
audit['results_without_any_odds']=int((~res.set_index(KEY).index.isin(odds.set_index(KEY).index)).sum())
unknown=odds[~odds.set_index(KEY).index.isin(res.set_index(KEY).index)].sort_values('t').drop_duplicates(KEY);save(unknown,'unresolved_rounds');audit['odds_without_results']=len(unknown)
unby=unknown.assign(day=unknown.t.dt.tz_convert('Europe/Prague').dt.strftime('%Y-%m-%d')).groupby('day').size()
daily=[]
for day,g in clean.groupby('day'):
 s=g[g.strict]; lo,hi=wilson(g.y.sum(),len(g));daily.append(dict(day=day,n=len(g),matches=g.matchId.nunique(),goals=int(g.y.sum()),goal_rate=g.y.mean(),wilson_low=lo,wilson_high=hi,strict_n=len(s),ws_n=int(g.ws_verified.sum()),mean_p=s.p_goal.mean(),mean_overround=s.overround.mean(),unresolved_odds_keys=int(unby.get(day,0))))
daily=pd.DataFrame(daily);save(daily,'daily_summary')
primary=clean[clean.strict].copy(); hist=primary[primary.split=='historical']; hold=primary[primary.split=='validation']
cohorts=[]
for name,g in [('all_pre_result',clean[clean.goalOdds.notna()]),('strict_5sec',primary),('historical',hist),('retrospective_holdout',hold),('ws_verified',primary[primary.ws_verified]),('ui_heuristic_only',primary[~primary.ws_verified]),('strict_15sec',clean[clean.lead.ge(15)&clean.goalOdds.notna()])]:
 lo,hi=wilson(g.y.sum(),len(g));gap,se,p=residual_test(g,g.p_goal)
 cohorts.append(dict(cohort=name,n=len(g),matches=g.matchId.nunique(),goal_rate=g.y.mean(),wilson_low=lo,wilson_high=hi,p_goal_mean=g.p_goal.mean(),calibration_gap=gap,cluster_se=se,cluster_p=p,mean_overround=g.overround.mean(),theoretical_loss=(g.overround/(1+g.overround)).mean(),brier=np.mean((g.y-g.p_goal)**2),goal_return=g.return_goal.mean(),save_return=g.return_save.mean(),goal_ci=boot_ci(g,g.return_goal),save_ci=boot_ci(g,g.return_save),calibration_ci=boot_ci(g,g.y-g.p_goal)))
cohorts=pd.DataFrame(cohorts);save(cohorts,'cohorts')
tables={}
for name,cols in [('sector',['sector']),('odds_pair',['goalOdds','saveOdds']),('sector_odds',['sector','goalOdds','saveOdds']),('keeper',['goalkeeper']),('keeper_sector_odds',['goalkeeper','sector','goalOdds','saveOdds']),('round',['round_num']),('source',['result_source']),('split_sector',['split','sector'])]:
 rows=[]
 for keys,g in primary.groupby(cols,dropna=False):
  keys=keys if isinstance(keys,tuple) else (keys,); lo,hi=wilson(g.y.sum(),len(g)); rows.append(dict(zip(cols,keys))|dict(n=len(g),matches=g.matchId.nunique(),goals=int(g.y.sum()),saves=int(len(g)-g.y.sum()),goal_rate=g.y.mean(),ci_low=lo,ci_high=hi,mean_p=g.p_goal.mean(),gap=(g.y-g.p_goal).mean(),mean_margin=g.overround.mean(),ws_n=int(g.ws_verified.sum())))
 table=pd.DataFrame(rows).sort_values('n',ascending=False);save(table,name+'_summary');tables[name]=table
# calibration bins fixed independently of outcomes.
primary['p_bin']=pd.cut(primary.p_goal,[0,.2,.3,.4,.5,.6,.7,.8,1],include_lowest=True)
cal=[]
for name,g in [('historical',hist),('holdout',hold),('all',primary),('ws',primary[primary.ws_verified])]:
 for b,h in g.groupby(pd.cut(g.p_goal,[0,.2,.3,.4,.5,.6,.7,.8,1],include_lowest=True),observed=True):
  lo,hi=wilson(h.y.sum(),len(h));cal.append(dict(cohort=name,bin=str(b),n=len(h),predicted=h.p_goal.mean(),observed=h.y.mean(),low=lo,high=hi))
cal=pd.DataFrame(cal);save(cal,'calibration_bins')
tests=[]
def test(label,cohort,stat,p,detail):tests.append(dict(test=label,cohort=cohort,statistic=float(stat),p=float(p),detail=detail))
for name,g in [('historical',hist),('holdout',hold),('all',primary),('ws',primary[primary.ws_verified])]:
 for col in ['sector','round_num','day']:
  ct=pd.crosstab(g[col],g.result)
  if min(ct.shape)>1:
   st,p,df,e=stats.chi2_contingency(ct);test('association_'+col,name,st,p,f'Pearson df={df}; min expected={e.min():.2f}; unadjusted for odds and clustering')
 gap,se,p=residual_test(g,g.p_goal);test('market_calibration',name,gap/se,p,'match-cluster t test; y - margin-normalized probability')
 gap,se,p=residual_test(g,g.p_additive);test('market_calibration_additive',name,gap/se,p,'sensitivity: additive de-vig')
# Cluster-robust logistic model with fixed market logit offset. Additional sector effects adjusted for price.
def fit_offset(g,cols,offset=True):
 x=np.column_stack([np.ones(len(g))]+[np.asarray(c,float) for c in cols]);y=g.y.to_numpy();o=logit(g.p_goal.to_numpy()) if offset else np.zeros(len(g));b=np.zeros(x.shape[1])
 for k in range(80):
  mu=expit(o+x@b);w=np.maximum(mu*(1-mu),1e-7);h=(x.T*w)@x;step=np.linalg.pinv(h)@x.T@(y-mu);b+=step
  if np.max(abs(step))<1e-8:break
 mu=expit(o+x@b);h=(x.T*np.maximum(mu*(1-mu),1e-7))@x;bread=np.linalg.pinv(h)
 scores=pd.DataFrame(x*(y-mu)[:,None]).assign(match=g.matchId.to_numpy()).groupby('match').sum().to_numpy();m=len(scores);n=len(y);p=x.shape[1]
 cov=bread@scores.T@scores@bread*m/(m-1)*(n-1)/(n-p)
 return b,cov,expit(o+x@b)
models=[]
for name,g in [('historical',hist),('holdout',hold),('all',primary)]:
 g=g[g.sector.notna()].copy();labels=sorted(g.sector.unique(),key=int);cols=[(g.sector==z).astype(int) for z in labels[1:]]
 b,cov,mu=fit_offset(g,cols);se=np.sqrt(np.diag(cov));st=b[1:]@np.linalg.pinv(cov[1:,1:])@b[1:];p=stats.chi2.sf(st,len(b)-1)
 test('sector_after_odds_cluster',name,st,p,'logistic with fixed logit(p_market) offset, cluster sandwich by match; sector 1 reference')
 for lab,bb,ss in zip(['intercept']+labels[1:],b,se):models.append(dict(cohort=name,term=lab,coef=bb,se=ss,p=2*stats.norm.sf(abs(bb/ss))))
save(pd.DataFrame(models),'sector_model')
# Chronological predictive check: no keeper features, no model choice using holdout outcomes.
hg=hist[hist.sector.notna()];vg=hold[hold.sector.notna()]; labs=sorted(hg.sector.unique(),key=int)
b,cov,_=fit_offset(hg,[(hg.sector==z).astype(int) for z in labs[1:]])
x=np.column_stack([np.ones(len(vg))]+[(vg.sector==z).astype(int).to_numpy() for z in labs[1:]])
pred=expit(logit(vg.p_goal.to_numpy())+x@b); delta=(vg.y.to_numpy()-pred)**2-(vg.y.to_numpy()-vg.p_goal.to_numpy())**2
predictive=dict(n=len(vg),market_brier=float(np.mean((vg.y-vg.p_goal)**2)),sector_model_brier=float(np.mean((vg.y-pred)**2)),delta_ci=boot_ci(vg,delta),note='retrospective temporal validation; post-result UI sector is not proven available pre-shot; diagnostic, not deployable predictor')
# Residual time dependence only within match, avoiding gaps and day boundaries.
adj=primary.merge(primary,on='matchId',suffixes=('_a','_b'));adj=adj[adj.round_num_b.eq(adj.round_num_a+1)]
ra=(adj.y_a-adj.p_goal_a).to_numpy();rb=(adj.y_b-adj.p_goal_b).to_numpy();st=float(np.corrcoef(ra,rb)[0,1]);null=[]
for _ in range(1999):null.append(float(np.corrcoef(ra,RNG.permutation(rb))[0,1]))
test('adjacent_residual_correlation','all',st,(1+np.sum(np.abs(null)>=abs(st)))/2000,'within-match adjacent rounds; permutation exploratory, overlap makes iid null approximate')
# Multiple tests controlled across every reported test in this exploratory family.
t=pd.DataFrame(tests);order=np.argsort(t.p.to_numpy());adjust=np.maximum.accumulate((len(t)-np.arange(len(t)))*t.p.to_numpy()[order]);out=np.empty(len(t));out[order]=np.minimum(adjust,1);t['p_holm']=out;save(t,'statistical_tests')
# Capture quality and synchronization.
health=D['health'].copy();health['t']=dt(health.timestamp)
for c in ['secondsSinceUI','secondsSinceWebSocket']:health[c]=pd.to_numeric(health[c],errors='coerce')
health_summary=dict(samples=len(health),disconnected_samples=int(health.cdpConnected.ne('true').sum()),ui_over60=int(health.secondsSinceUI.gt(60).sum()),ws_over180=int(health.secondsSinceWebSocket.gt(180).sum()),ui_max=health.secondsSinceUI.max(),ws_max=health.secondsSinceWebSocket.max(),note='periodic observations, not time-weighted uptime; missing intervals not healthy intervals')
session_summary=D['sessions'].groupby(['version','event']).size().reset_index(name='count');save(session_summary,'sessions_summary')
gaps=health.sort_values('t').copy();gaps['gap_seconds']=gaps.t.diff().dt.total_seconds();save(gaps[gaps.gap_seconds.gt(120)],'health_recording_gaps')
health_summary['gaps_over120']=int(gaps.gap_seconds.gt(120).sum());health_summary['max_recording_gap_sec']=gaps.gap_seconds.max()
save(primary[['matchId','round','lead','result_source','day']],'timing_leads')
lead_summary=primary.groupby(['result_source','round_num']).lead.describe(percentiles=[.05,.5,.95]).reset_index();save(lead_summary,'lead_summary')
audit['targets_selected_nonempty']=int(D['targets'].selectedSector.ne('').sum());audit['targets_actual_nonempty']=int(D['targets'].actualBallSector.ne('').sum());audit['targets_keeper_nonempty']=int(D['targets'].keeperSector.ne('').sum())
voting=D['voting'];audit['voting_rows']=len(voting);audit['voting_nonempty_hlasy']=int(voting.hlasy.ne('').sum());audit['voting_nonempty_options']=int(~voting.optionsJson.isin(['','[]']).sum()) if False else int((~voting.optionsJson.isin(['','[]'])).sum());audit['voting_nonempty_selected']=int((~voting.selectedJson.isin(['','[]'])).sum())
audit['schedule_diagnostic_rows']=len(D['schedule-diagnostics']);audit['schedule_extracted_positive']=int(pd.to_numeric(D['schedule-diagnostics'].extractedCount,errors='coerce').gt(0).sum())
save(ms.groupby(['version','marketState','goalStatus','saveStatus']).size().reset_index(name='count'),'market_status_counts')
# Keeper windows: count reuse of one schedule card for many internal matches.
keeper_usage=links[links.goalkeeper.ne('')].groupby(['scheduleMatchNumber','goalkeeper','scheduledLocal']).agg(internal_matches=('matchId','nunique'),min_delay=('secondsFromSchedule',lambda x:pd.to_numeric(x).min()),max_delay=('secondsFromSchedule',lambda x:pd.to_numeric(x).max())).reset_index();save(keeper_usage,'keeper_window_usage')
audit['keeper_links']=len(links);audit['keeper_timeonly']=int(links.confidence.eq('TIME_WINDOW_ONLY').sum());audit['schedule_cards_reused_multiple_matches']=int(keeper_usage.internal_matches.gt(1).sum());audit['max_internal_matches_per_keeper_card']=int(keeper_usage.internal_matches.max())
# Exact cross-day matching on complete five-round matches. IDs/keepers are metadata only.
complete=[]
for mid,g in clean.groupby('matchId'):
 g=g.sort_values('round_num')
 if len(g)==5 and g.round_num.tolist()==[1,2,3,4,5] and g.day.nunique()==1 and g.sector.notna().all() and g.goalOdds.notna().all() and g.lead.ge(0).all():
  complete.append(dict(matchId=mid,day=g.day.iloc[0],time=str(g.result_timestamp.min()),keeper=str(g.goalkeeper.iloc[0]),outcome=tuple(g.y.astype(int)),sector=tuple(g.sector.astype(int)),goal=tuple(g.goalOdds),save=tuple(g.saveOdds),joint=tuple(zip(g.sector.astype(int),g.goalOdds,g.saveOdds,g.result))))
sig=pd.DataFrame(complete);save(sig,'complete_match_signatures')
records=list(sig.itertuples(index=False))
features={'outcome':['outcome'],'sector_outcome':['sector','outcome'],'odds_outcome':['goal','save','outcome'],'full':['joint']}
rep=[]
for feature,cols in features.items():
 for a in range(len(sig)):
  for b in range(a+1,len(sig)):
   A=records[a];B=records[b]
   if A.day==B.day:continue
   if all(getattr(A,c)==getattr(B,c) for c in cols):rep.append(dict(feature=feature,match_a=A.matchId,match_b=B.matchId,day_a=A.day,day_b=B.day,signature=str(tuple(getattr(A,c) for c in cols))))
rep=pd.DataFrame(rep,columns=['feature','match_a','match_b','day_a','day_b','signature']);save(rep,'cross_day_match_repeats')
print('core analysis complete',len(clean),'strict',len(primary),'complete matches',len(sig),flush=True)
# Nearest matches use exact sector/result and <=0.05 absolute tolerance on BOTH odds per shot.
approx=[];near_count=0
for a in range(len(sig)):
 A=records[a]
 for b in range(a+1,len(sig)):
  B=records[b]
  if A.day==B.day:continue
  eq=(np.array(A.sector)==B.sector)&(np.array(A.outcome)==B.outcome)&(np.abs(np.array(A.goal)-B.goal)<=.0500001)&(np.abs(np.array(A.save)-B.save)<=.0500001)
  if eq.sum()>=4:
   near_count+=1;approx.append(dict(match_a=A.matchId,match_b=B.matchId,day_a=A.day,day_b=B.day,matching_rounds=int(eq.sum()),mismatched_rounds=str(np.where(~eq)[0]+1),keeper_a=A.keeper,keeper_b=B.keeper))
save(pd.DataFrame(approx,columns=['match_a','match_b','day_a','day_b','matching_rounds','mismatched_rounds','keeper_a','keeper_b']),'approximate_match_repeats')
# Contiguous-shot search: break at missing round, same day change, or >180s receive-time gap.
seq=[]
session_breaks={v:np.sort(dt(g.timestamp).astype('int64').to_numpy()) for v,g in D['sessions'].groupby('version')}
for day,g in clean.groupby('day'):
 g=g.sort_values(['result_timestamp','matchId','round_num']);block=[];prev=None
 for r in g.itertuples():
  ok=pd.notna(r.sector) and pd.notna(r.goalOdds) and r.lead>=0
  follows=prev is not None and ((r.matchId==prev.matchId and r.round_num==prev.round_num+1) or (r.matchId!=prev.matchId and prev.round_num==5 and r.round_num==1)) and 0<=(r.result_timestamp-prev.result_timestamp).total_seconds()<=180
  if follows:
   bb=session_breaks[r.version]
   follows=(r.version==prev.version and np.searchsorted(bb,r.result_timestamp.value,side='right')==np.searchsorted(bb,prev.result_timestamp.value,side='right'))
  if not follows or not ok:
   if block:seq.append((day,block))
   block=[]
  if ok:block.append(r)
  prev=r
 if block:seq.append((day,block))
def get_blocks(mode):
 return [(day,[((r.result,) if mode=='outcome' else (int(r.sector),r.result) if mode=='sector_outcome' else (int(r.sector),r.goalOdds,r.saveOdds,r.result)) for r in rows]) for day,rows in seq]
def repeated_windows(blocks,k,examples=False):
 seen={};count=0;hits=[]
 for bi,(day,tokens) in enumerate(blocks):
  for i in range(len(tokens)-k+1):
   key=tuple(tokens[i:i+k]);old=seen.setdefault(key,[])
   for d,b,j in old:
    if d!=day:
     count+=1
     if examples and len(hits)<40:hits.append((b,j,bi,i,k))
   old.append((day,bi,i))
 return count,hits
sequence_summary=[]; seq_examples=[]
for mode in ['outcome','sector_outcome','full']:
 blocks=get_blocks(mode);longest=0
 for k in range(1,81):
  count,hits=repeated_windows(blocks,k,True)
  if not count:break
  longest=k
  if k in [3,4,5,6,8,10,12,16,20,30,40,60,80]:sequence_summary.append(dict(mode=mode,k=k,pairs=count))
  besthits=hits
 if longest:
  for b,i,c,j,k in besthits[:10]:
   aa=seq[b][1][i:i+k];bb=seq[c][1][j:j+k]
   seq_examples.append(dict(mode=mode,length=k,day_a=seq[b][0],day_b=seq[c][0],start_a=f'{aa[0].matchId}/{aa[0].round}',start_b=f'{bb[0].matchId}/{bb[0].round}',tokens=str(blocks[b][1][i:i+k]),time_a=str(aa[0].result_timestamp),time_b=str(bb[0].result_timestamp)))
 # Familywise scan statistic: longest common contiguous pattern. Permute joint tokens within days.
 k=longest; observed=repeated_windows(blocks,k)[0]; exceed=0;block_exceed=0;B=499
 daytokens={}
 for d,toks in blocks:daytokens.setdefault(d,[]).extend(toks)
 def shuffled(blocksize=1):
  pools={}
  for d,toks in daytokens.items():
   chunks=[toks[i:i+blocksize] for i in range(0,len(toks),blocksize)];pools[d]=list(itertools.chain.from_iterable(chunks[i] for i in RNG.permutation(len(chunks))))
  pos=collections.defaultdict(int);out=[]
  for d,toks in blocks:
   s=pos[d];out.append((d,pools[d][s:s+len(toks)]));pos[d]+=len(toks)
  return out
 for z in range(B):
  exceed+=repeated_windows(shuffled(),k)[0]>0;block_exceed+=repeated_windows(shuffled(5),k)[0]>0
 sequence_summary.append(dict(mode=mode,k=longest,pairs=observed,metric='longest',permutation_p=(1+exceed)/(B+1),block5_p=(1+block_exceed)/(B+1),B=B))
save(pd.DataFrame(sequence_summary),'sequence_scan');save(pd.DataFrame(seq_examples),'sequence_examples')
# Null for full 5-round signatures and near matches preserves round position, joint shot attributes, day.
days=sig.day.to_numpy();cross=days[:,None]!=days[None,:];upper=np.triu(cross,1)
attrs=np.array([[list(z) for z in s] for s in sig.joint],dtype=object)
sector=np.array(sig.sector.tolist());y=np.array(sig.outcome.tolist());go=np.array(sig.goal.tolist());sv=np.array(sig['save'].tolist())
def pair_counts(s,y,g,v):
 eq=(s[:,None,:]==s[None,:,:])&(y[:,None,:]==y[None,:,:])
 exact=(eq&(g[:,None,:]==g[None,:,:])&(v[:,None,:]==v[None,:,:])).all(axis=2)
 near=(eq&(np.abs(g[:,None,:]-g[None,:,:])<=.0500001)&(np.abs(v[:,None,:]-v[None,:,:])<=.0500001)).sum(axis=2)>=4
 return int(np.sum(exact&upper)),int(np.sum(near&upper))
observed=pair_counts(sector,y,go,sv);null=[]
for z in range(499):
 ss=sector.copy();yy=y.copy();gg=go.copy();vv=sv.copy()
 for day in np.unique(days):
  ix=np.where(days==day)[0]
  for r in range(5):
   perm=RNG.permutation(ix);ss[ix,r]=sector[perm,r];yy[ix,r]=y[perm,r];gg[ix,r]=go[perm,r];vv[ix,r]=sv[perm,r]
 null.append(pair_counts(ss,yy,gg,vv))
null=np.array(null);repeat_null=dict(complete_matches=len(sig),cross_day_pairs=int(upper.sum()),exact_pairs=observed[0],near_pairs=observed[1],B=499,exact_null_mean=float(null[:,0].mean()),near_null_mean=float(null[:,1].mean()),exact_p=float((1+(null[:,0]>=observed[0]).sum())/500),near_p=float((1+(null[:,1]>=observed[1]).sum())/500))
save(pd.DataFrame(null,columns=['exact_pairs','near_pairs']),'repeat_permutations')
metadata=dict(audit=audit,health=health_summary,predictive=predictive,repeats=repeat_null,seed=20261104,cutoff='V11+V12 historical; V13 local day >=2026-10-29 validation; earlier V13 boundary excluded from confirmation',versions=[11,12,13],created_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),lead_percentiles=primary.lead.quantile([0,.05,.5,.95,1]).to_dict(),combination_sparsity=dict(total=len(tables['keeper_sector_odds']),n_ge20=int(tables['keeper_sector_odds'].n.ge(20).sum()),n_eq1=int(tables['keeper_sector_odds'].n.eq(1).sum())))
(OUT/'metrics.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2,default=lambda o:float(o) if isinstance(o,np.number) else str(o)),encoding='utf-8')
print(json.dumps(metadata,ensure_ascii=False,indent=2,default=str),flush=True)
print(cohorts.to_string(index=False));print(t.to_string(index=False));print(pd.DataFrame(sequence_summary).to_string(index=False))
