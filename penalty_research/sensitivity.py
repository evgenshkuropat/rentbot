from pathlib import Path
import sys,json
sys.path.insert(0,str(Path(__file__).parent/'_libs'))
import pandas as pd,numpy as np
from scipy.optimize import brentq
from scipy import stats
O=Path(__file__).parent/'output';d=pd.read_csv(O/'rounds_with_chain_flags.csv',dtype={'matchId':str,'version':str})
g=d[d.strict&d.split.eq('validation')&d.fav_ok&d.fav_odds.lt(1.2)].copy()
g['p_proportional']=g.p_fav;g['p_additive_fav']=np.where(g.fav_goal,g.p_additive,1-g.p_additive)
def power(r):
 a=1/r.goalOdds;b=1/r.saveOdds;k=brentq(lambda k:a**k+b**k-1,.05,10);return max(a**k,b**k)
g['p_power']=g.apply(power,axis=1)
rows=[]
for method in ['p_proportional','p_additive_fav','p_power']:
 residual=g.fav_win-g[method]
 for cluster in ['matchId','day']:
  a=g.assign(r=residual).groupby(cluster).r.agg(['sum','count']).to_numpy();n=len(g);m=len(a);se=np.sqrt(m/(m-1)*np.sum((a[:,0]-a[:,1]*residual.mean())**2))/n;t=residual.mean()/se;p=2*stats.t.sf(abs(t),m-1)
  rows.append(dict(hypothesis='H06',method=method,cluster=cluster,n=n,clusters=m,observed=g.fav_win.mean(),expected=g[method].mean(),gap=residual.mean(),se=se,p=p))
pd.DataFrame(rows).to_csv(O/'h06_margin_sensitivity.csv',index=False,encoding='utf-8-sig')
g.groupby('day').agg(n=('y','size'),wins=('fav_win','sum'),observed=('fav_win','mean'),expected=('p_fav','mean'),roi=('fav_roi','mean')).reset_index().to_csv(O/'h06_by_day.csv',index=False,encoding='utf-8-sig')
# Raw exact 1.22 counts compare first vs closing across historical records (explains differing registry totals).
hh=d[d.version.isin(['11','12'])];rows=[]
for strict in [False,True]:
 z=hh[hh.strict] if strict else hh
 for mode in ['first','closing']:
  for side in ['GOAL','SAVE']:
   c=('firstGoalOdds' if side=='GOAL' else 'firstSaveOdds') if mode=='first' else ('goalOdds' if side=='GOAL' else 'saveOdds');zz=z[np.isclose(z[c],1.22,atol=1e-10)];win=zz.y if side=='GOAL' else 1-zz.y
   rows.append(dict(strict=strict,price=mode,side=side,n=len(zz),wins=int(win.sum())))
pd.DataFrame(rows).to_csv(O/'h18_historical_reconciliation.csv',index=False,encoding='utf-8-sig')
# Observed odds coverage and gaps; not all-site availability.
unknown=pd.read_csv(O/'unresolved_rounds.csv');unknown['t']=pd.to_datetime(unknown.t,utc=True,format='mixed');unknown['day']=unknown.t.dt.tz_convert('Europe/Prague').dt.strftime('%Y-%m-%d');unknown['p_goal']=(1/unknown.goalOdds)/(1/unknown.goalOdds+1/unknown.saveOdds)
known=d[d.goalOdds.notna()];r=unknown.groupby('day').agg(unresolved=('matchId','size'),expected_p=('p_goal','mean')).reset_index();r.to_csv(O/'unresolved_by_day.csv',index=False,encoding='utf-8-sig')
print(pd.DataFrame(rows).to_string(index=False));print(pd.read_csv(O/'h06_margin_sensitivity.csv').to_string(index=False))
# Exploratory approximate contiguous sequences: one arbitrary mismatch, no ID/keeper match.
from itertools import combinations
R=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector')
sessions=pd.concat([pd.read_csv(R/f'penalty-sessions-v{v}.csv').assign(version=str(v)) for v in [11,12,13]])
breaks={v:np.sort(pd.to_datetime(g.timestamp,utc=True,format='mixed').astype('int64').to_numpy()) for v,g in sessions.groupby('version')}
z=d.copy();z['result_timestamp']=pd.to_datetime(z.result_timestamp,utc=True,format='mixed');blocks=[]
for day,g in z.groupby('day'):
 block=[];prev=None
 for row in g.sort_values(['result_timestamp','matchId','round_num']).itertuples():
  ok=pd.notna(row.sector) and pd.notna(row.goalOdds) and row.lead>=0
  follows=prev is not None and row.version==prev.version and ((row.matchId==prev.matchId and row.round_num==prev.round_num+1) or (row.matchId!=prev.matchId and prev.round_num==5 and row.round_num==1)) and 0<=(row.result_timestamp-prev.result_timestamp).total_seconds()<=180
  if follows:
   bb=breaks[row.version];follows=np.searchsorted(bb,row.result_timestamp.value,side='right')==np.searchsorted(bb,prev.result_timestamp.value,side='right')
  if not follows or not ok:
   if block:blocks.append((day,block))
   block=[]
  if ok:block.append(row)
  prev=row
 if block:blocks.append((day,block))
out=[]
for mode in ['sector_outcome','sector_both_odds_tol_0.05_outcome']:
 best=0;candidates=[]
 for (da,aa),(db,bb) in combinations(blocks,2):
  if da==db or min(len(aa),len(bb))<best:continue
  ax=np.array([[r.sector,r.y,r.goalOdds,r.saveOdds] for r in aa],float);bx=np.array([[r.sector,r.y,r.goalOdds,r.saveOdds] for r in bb],float);prev0=np.zeros(len(bb)+1,int);prev1=prev0.copy()
  for i,a in enumerate(ax):
   eq=(a[0]==bx[:,0])&(a[1]==bx[:,1])
   if mode!='sector_outcome':eq&=(abs(a[2]-bx[:,2])<=.0500001)&(abs(a[3]-bx[:,3])<=.0500001)
   now0=np.r_[0,np.where(eq,prev0[:-1]+1,0)];now1=np.r_[0,np.where(eq,prev1[:-1]+1,prev0[:-1]+1)];length=int(now1.max())
   if length>=best:
    if length>best:best=length;candidates=[]
    for j in np.where(now1==length)[0][:3]:
     if len(candidates)<20:
      A=aa[i-length+1:i+1];B=bb[j-length:j];candidates.append(dict(mode=mode,length=length,day_a=da,day_b=db,start_a=f'{A[0].matchId}/{A[0].round}',start_b=f'{B[0].matchId}/{B[0].round}',note='at most one mismatched shot; exploratory, no p-value; fixed odds tolerance 0.05 where applicable'))
   prev0,prev1=now0,now1
 out.extend(candidates)
pd.DataFrame(out).to_csv(O/'approximate_sequence_examples.csv',index=False,encoding='utf-8-sig')
print(pd.DataFrame(out).to_string(index=False))
