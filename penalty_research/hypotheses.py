from pathlib import Path
import sys,json,math,hashlib
sys.path.insert(0,str(Path(__file__).parent/'_libs'))
import numpy as np,pandas as pd
from scipy import stats
from scipy.special import logit,expit
O=Path(__file__).parent/'output'; R=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector');rng=np.random.default_rng(1820261104)
d=pd.read_csv(O/'clean_rounds.csv',dtype={'matchId':str,'round':str,'version':str}); d['result_timestamp']=pd.to_datetime(d.result_timestamp,utc=True,format='mixed');d['first_odds_timestamp']=pd.to_datetime(d.first_odds_timestamp,utc=True,format='mixed')
d['split']=np.where(d.version.isin(['11','12']),'historical',np.where(d.day.ge('2026-10-29'),'validation','v13_boundary'))
session=pd.concat([pd.read_csv(R/f'penalty-sessions-v{v}.csv').assign(version=str(v)) for v in [11,12,13]])
breaks={v:np.sort(pd.to_datetime(g.timestamp,utc=True,format='mixed').astype('int64').to_numpy()) for v,g in session.groupby('version')}
d['fav_goal']=d.goalOdds<d.saveOdds;d['fav_ok']=d.goalOdds.ne(d.saveOdds)&d.goalOdds.notna();d['fav_win']=np.where(d.fav_goal,d.y,1-d.y);d['p_fav']=np.maximum(d.p_goal,1-d.p_goal);d['fav_odds']=np.minimum(d.goalOdds,d.saveOdds);d['fav_resid']=d.fav_win-d.p_fav;d['fav_roi']=d.fav_win*d.fav_odds-1
d['error_run']=0;d['hit_run']=0;d['low_run']=0;d['prev_p']=np.nan;d['prev_y']=np.nan;d['prev_fav_goal']=False;d['prev_error']=False;d['continuous_prev']=False
for mid,g in d.groupby('matchId'):
 g=g.sort_values('round_num');prev=None;er=hr=lr=0
 for ix,row in g.iterrows():
  continuous=False
  if prev is not None:
   delta=(row.result_timestamp-prev.result_timestamp).total_seconds();bb=breaks[row.version];a=prev.result_timestamp.value;b=row.result_timestamp.value
   continuous=(row.round_num==prev.round_num+1 and 0<delta<=180 and row.version==prev.version and row.strict and prev.strict and np.searchsorted(bb,b,side='right')==np.searchsorted(bb,a,side='right'))
  if not continuous:er=hr=lr=0
  else:
   if prev.fav_ok:er=er+1 if prev.fav_win==0 else 0;hr=hr+1 if prev.fav_win==1 else 0
   else:er=hr=0
   lr=lr+1 if prev.fav_odds<1.2 else 0
   d.loc[ix,['prev_p','prev_y','prev_fav_goal','prev_error']]=[prev.p_goal,prev.y,prev.fav_goal,prev.fav_ok and prev.fav_win==0]
  d.loc[ix,['error_run','hit_run','low_run','continuous_prev']]=[er,hr,lr,continuous];prev=row
def ci(g,col):
 if len(g)==0:return [None,None]
 a=g.assign(val=g[col]).groupby('matchId').val.agg(['sum','count']).to_numpy();out=[]
 for i in range(0,1999,100):
  z=a[rng.integers(0,len(a),size=(min(100,1999-i),len(a)))].sum(1);out.extend(z[:,0]/z[:,1])
 return np.quantile(out,[.025,.975]).tolist()
def wc(y,n):
 if not n:return (np.nan,np.nan)
 z=1.95996398454;p=y/n;den=1+z*z/n;c=(p+z*z/(2*n))/den;h=z*np.sqrt(p*(1-p)/n+z*z/(4*n*n))/den;return c-h,c+h
rows=[];tests=[]
def summary(hyp,split,group,g,ycol='fav_win',pcol='p_fav',ocol='fav_odds',primary=False):
 n=len(g)
 if n==0:
  rows.append(dict(hypothesis=hyp,split=split,group=group,n=0));return
 y=g[ycol].to_numpy(float);p=g[pcol].to_numpy(float);od=g[ocol].to_numpy(float);gg=g.assign(resid=y-p,roi=y*od-1,win=y)
 a=gg.groupby('matchId').resid.agg(['sum','count']).to_numpy();m=len(a);effect=np.mean(y-p);se=np.sqrt(m/(m-1)*np.sum((a[:,0]-a[:,1]*effect)**2))/n if m>1 else np.nan;pv=2*stats.t.sf(abs(effect/se),m-1) if se>0 else np.nan
 lo,hi=wc(y.sum(),n)
 rows.append(dict(hypothesis=hyp,split=split,group=group,n=n,matches=m,wins=int(y.sum()),win_rate=y.mean(),wilson_low=lo,wilson_high=hi,expected_wins=p.sum(),mean_p=p.mean(),gap=effect,gap_ci=ci(gg,'resid'),roi=np.mean(y*od-1),roi_ci=ci(gg,'roi'),cluster_p=pv))
 if primary:tests.append(dict(hypothesis=hyp,split=split,test=group,p=float(pv),statistic=effect/se if se>0 else np.nan))
def cluster_ols(g,y,x):
 if len(g)<5:return np.nan,np.nan
 x=np.column_stack([np.ones(len(g))]+[np.asarray(z,float) for z in x]);y=np.asarray(y,float);b=np.linalg.pinv(x)@y;r=y-x@b;bread=np.linalg.pinv(x.T@x);score=pd.DataFrame(x*r[:,None]).assign(mid=g.matchId.to_numpy()).groupby('mid').sum().to_numpy();m=len(score);cov=bread@score.T@score@bread*m/(m-1)*(len(y)-1)/(len(y)-x.shape[1]);st=b[1:]@np.linalg.pinv(cov[1:,1:])@b[1:];return float(st),float(stats.chi2.sf(st,len(b)-1))
def addtest(h,sp,label,st,p):tests.append(dict(hypothesis=h,split=sp,test=label,p=p,statistic=st))
for sp,allg in d[d.strict].groupby('split'):
 g=allg[allg.continuous_prev&allg.fav_ok].copy();one=g[(g.error_run>=1)&g.prev_fav_goal];two=g[(g.error_run>=1)&~g.prev_fav_goal]
 summary('H01',sp,'after_unexpected_SAVE',one,primary=True);summary('H02',sp,'after_unexpected_GOAL',two,primary=True)
 contrast=pd.concat([one.assign(group=1),two.assign(group=0)]);st,p=cluster_ols(contrast,contrast.fav_resid,[contrast.group]);addtest('H02',sp,'H01_vs_H02_residual_difference',st,p)
 g3=g[g.error_run.ge(1)|g.hit_run.ge(1)]
 for flag,label in [(True,'after_error'),(False,'after_hit')]:summary('H03',sp,label,g3[g3.prev_error==flag])
 st,p=cluster_ols(g3,g3.fav_resid,[g3.prev_error.astype(int)]);addtest('H03',sp,'error_vs_hit_adjusted_gap',st,p)
 for h,col,maximum in [('H04','error_run',3),('H05','hit_run',6),('H07','low_run',3)]:
  for length in range(1,maximum+1):summary(h,sp,f'preceding_run_at_least_{length}',g[g[col]>=length])
  # Omnibus comparison of residuals by exact preceding run length, capped at 3 or 4.
  run=np.minimum(g[col].astype(int),min(maximum,4));cats=sorted(run.unique());st,p=cluster_ols(g,g.fav_resid,[(run==v).astype(int) for v in cats[1:]]);addtest(h,sp,'run_length_omnibus',st,p)
 for lo,hi in [(1,1.2),(1.2,1.4),(1.4,1.6),(1.6,1.8),(1.8,2)]:
  summary('H06',sp,f'favorite_odds_{lo:.2f}_to_{hi-.01:.2f}',allg[allg.fav_ok&allg.fav_odds.ge(lo)&allg.fav_odds.lt(hi)],primary=lo==1)
 summary('H12',sp,'GOAL_1.10_to_1.15_ALL_sectors_proxy',allg[allg.goalOdds.between(1.10,1.15)],'y','p_goal','goalOdds')
 ct=pd.crosstab(allg.sector,allg.result);st,p,dd,e=stats.chi2_contingency(ct);addtest('H09',sp,'sector_outcome_unadjusted',st,p)
 for sec,sg in allg.groupby('sector'):summary('H09',sp,f'sector_{sec}',sg,'y','p_goal','goalOdds')
 # H18 initial odds exactly 1.22, never closing odds; later validation date separately.
 for label,mask,yc,pc,oc in [('GOAL',np.isclose(allg.firstGoalOdds,1.22,atol=1e-10), 'y','p_first_goal','firstGoalOdds'),('SAVE',np.isclose(allg.firstSaveOdds,1.22,atol=1e-10),'save_y','p_first_save','firstSaveOdds')]:
  z=allg[mask].copy();z['p_first_goal']=(1/z.firstGoalOdds)/(1/z.firstGoalOdds+1/z.firstSaveOdds);z['p_first_save']=1-z.p_first_goal;z['save_y']=1-z.y
  summary('H18',sp,label,z,yc,pc,oc)
# H18 conservative post-registration validation starts 30 October local.
h18=d[d.strict&d.version.eq('13')&d.day.ge('2026-10-30')].copy();h18['p_first_goal']=(1/h18.firstGoalOdds)/(1/h18.firstGoalOdds+1/h18.firstSaveOdds);h18['p_first_save']=1-h18.p_first_goal;h18['save_y']=1-h18.y;h18groups=[]
for lab,mask,yc,pc,oc in [('GOAL',np.isclose(h18.firstGoalOdds,1.22,atol=1e-10),'y','p_first_goal','firstGoalOdds'),('SAVE',np.isclose(h18.firstSaveOdds,1.22,atol=1e-10),'save_y','p_first_save','firstSaveOdds')]:
 z=h18[mask].copy();summary('H18','validation_post_H18',lab,z,yc,pc,oc);z['win']=z[yc];z['p']=z[pc];z['side_save']=int(lab=='SAVE');h18groups.append(z)
z=pd.concat(h18groups);st,p=cluster_ols(z,z.win-z.p,[z.side_save]);addtest('H18','validation_post_H18','SAVE_vs_GOAL_market_adjusted',st,p)
ct=pd.crosstab(z.side_save,z.win); oddsratio,p=stats.fisher_exact(ct);addtest('H18','validation_post_H18','SAVE_vs_GOAL_Fisher',oddsratio,p)
# H08: fixed 3 history features + market offset, trained historical, checked V13>=29 only.
train=d[d.strict&d.continuous_prev&d.split.eq('historical')].copy();val=d[d.strict&d.continuous_prev&d.split.eq('validation')].copy()
def design(g):return np.column_stack([np.ones(len(g)),logit(g.prev_p),g.prev_y,logit(g.prev_p)*g.prev_y])
x=design(train);off=logit(train.p_goal.to_numpy());y=train.y.to_numpy();b=np.zeros(x.shape[1])
for k in range(100):
 mu=expit(off+x@b);w=np.maximum(mu*(1-mu),1e-7);step=np.linalg.pinv((x.T*w)@x)@x.T@(y-mu);b+=step
 if max(abs(step))<1e-8:break
pr=expit(logit(val.p_goal)+design(val)@b);delta=(val.y-pr)**2-(val.y-val.p_goal)**2;gg=val.assign(delta=delta)
a=gg.groupby('matchId').delta.agg(['sum','count']).to_numpy();se=np.sqrt(len(a)/(len(a)-1)*np.sum((a[:,0]-a[:,1]*delta.mean())**2))/len(gg);p=2*stats.t.sf(abs(delta.mean()/se),len(a)-1);addtest('H08','validation','Brier_delta_model_minus_market',delta.mean()/se,p)
h08=dict(n_train=len(train),n_val=len(val),market_brier=float(np.mean((val.y-val.p_goal)**2)),model_brier=float(np.mean((val.y-pr)**2)),delta=float(delta.mean()),delta_ci=ci(gg,'delta'),p=float(p),coefficients=b.tolist())
# Daily and keeper H01/H02 diagnostics.
by=[]
for sp,allg in d[d.strict&d.continuous_prev&d.fav_ok].groupby('split'):
 for h,condition in [('H01',(allg.error_run>=1)&allg.prev_fav_goal),('H02',(allg.error_run>=1)&~allg.prev_fav_goal)]:
  for cols in [['day'],['goalkeeper']]:
   for label,g in allg[condition].groupby(cols,dropna=False):by.append(dict(hypothesis=h,split=sp,dimension=cols[0],group=str(label),n=len(g),wins=int(g.fav_win.sum()),observed=g.fav_win.mean(),expected=g.p_fav.mean(),gap=g.fav_resid.mean(),roi=g.fav_roi.mean()))
pd.DataFrame(by).to_csv(O/'h01_h02_by_day_keeper.csv',index=False,encoding='utf-8-sig')
# H13: schedule is an independent source of names, not a verified join to shots.
s=pd.concat([pd.read_csv(R/f'penalty-schedule-v{v}.csv').assign(version=v) for v in [12,13]])
s['t']=pd.to_datetime(s.scheduledLocal,format='%d.%m.%y %H:%M');s=s.drop_duplicates(['scheduledLocal','matchNumber','goalkeeper']).sort_values('t');s['id']=pd.to_numeric(s.matchNumber)
s['next_keeper']=s.goalkeeper.shift(-1);s['next_id']=s.id.shift(-1);s['gap_min']=(s.t.shift(-1)-s.t).dt.total_seconds()/60;s['day']=s.t.dt.strftime('%Y-%m-%d');s['split']=np.where(s.version.eq(12),'historical',np.where(s.day.ge('2026-10-29'),'validation','v13_boundary'))
expected={'JOSE':'GIANLUIGI','GIANLUIGI':'EMIL','EMIL':'RUI','RUI':'JOSE'}
schedule_stats=[]
for sp,g in s.groupby('split'):
 eligible=g.next_id.eq(g.id+1)&g.gap_min.between(15,25)&g.goalkeeper.isin(expected);z=g[eligible];succ=(z.next_keeper==z.goalkeeper.map(expected)).sum();cnt=[]
 for j in range(1999):
  arr=g.goalkeeper.to_numpy().copy()
  for day,ix in g.reset_index(drop=True).groupby('day').groups.items():arr[np.array(list(ix))]=rng.permutation(arr[np.array(list(ix))])
  # Compare original eligible adjacent positions, retaining gaps and calendar selection.
  mask=eligible.to_numpy();ii=np.where(mask)[0];ii=ii[ii+1<len(arr)];cnt.append(sum(expected.get(arr[k],'?')==arr[k+1] for k in ii))
 pv=(1+sum(c>=succ for c in cnt))/2000;schedule_stats.append(dict(split=sp,schedule_cards=len(g),eligible_transitions=len(z),cycle_hits=int(succ),fraction=succ/len(z) if len(z) else None,permutation_p=pv));addtest('H13',sp,'predefined_four_keeper_cycle',succ,pv)
s.to_csv(O/'schedule_audit.csv',index=False,encoding='utf-8-sig')
# H17 candidate suspensions; only within same key/session and no >120s health sampling gap.
states=pd.concat([pd.read_csv(R/f'penalty-market-states-v{v}.csv',dtype=str).assign(version=v) for v in [11,12,13]])
states['t']=pd.to_datetime(states.timestamp,utc=True,format='mixed');intervals=[]
for key,g in states.groupby(['version','sessionId','matchId','round']):
 g=g.sort_values('t');g=g[g.marketState.ne(g.marketState.shift())]
 for a,b in zip(list(g.itertuples()),list(g.itertuples())[1:]):
  if a.marketState=='SUSPENDED':intervals.append(dict(version=key[0],session=key[1],matchId=key[2],round=key[3],start=str(a.t),end=str(b.t),next_state=b.marketState,seconds=(b.t-a.t).total_seconds()))
iv=pd.DataFrame(intervals);iv.to_csv(O/'suspension_intervals.csv',index=False,encoding='utf-8-sig')
pd.DataFrame(rows).to_csv(O/'hypothesis_results.csv',index=False,encoding='utf-8-sig')
tt=pd.DataFrame(tests);tt['p_holm_all_validation_tests']=np.nan;ix=tt.index[tt.split.isin(['validation','validation_post_H18'])&tt.p.notna()];order=tt.loc[ix].sort_values('p').index;vals=np.maximum.accumulate((len(order)-np.arange(len(order)))*tt.loc[order,'p']);tt.loc[order,'p_holm_all_validation_tests']=np.minimum(vals,1)
tt.to_csv(O/'hypothesis_tests.csv',index=False,encoding='utf-8-sig');d.to_csv(O/'rounds_with_chain_flags.csv',index=False,encoding='utf-8-sig')
reg=Path(r'C:\Users\evgen\Desktop\Penalty_Arena_Hypotheses_updated_H18_2026-10-29.md')
metadata=dict(registry_path=str(reg),registry_sha256=hashlib.sha256(reg.read_bytes()).hexdigest(),H08=h08,H13=schedule_stats,H17=dict(bounded_suspensions=len(iv),over120=int(iv.seconds.gt(120).sum()),max_seconds=iv.seconds.max(),median=iv.seconds.median(),note='durations between state transitions; not proof of continuously unavailable market or ongoing video'),continuous_pairs=int(d.continuous_prev.sum()),n_tests_validation=len(order))
(O/'hypothesis_metrics.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
print(json.dumps(metadata,ensure_ascii=False,indent=2,default=str));print(tt[tt.split.isin(['validation','validation_post_H18'])].to_string(index=False));print(pd.DataFrame(rows).query("hypothesis in ['H01','H02','H06','H18']").to_string(index=False))
