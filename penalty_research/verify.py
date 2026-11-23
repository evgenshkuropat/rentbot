from pathlib import Path
import csv,json,hashlib,collections,sys
P=Path(__file__).parent;O=P/'output';R=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector')
def read(p):
 with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
manifest=read(O/'source_manifest.csv');changed=[]
for r in manifest:
 if hashlib.sha256((R/r['file']).read_bytes()).hexdigest()!=r['sha256']:changed.append(r['file'])
assert not changed,changed
d=read(O/'clean_rounds.csv');keys=[(x['matchId'],x['round']) for x in d];assert len(keys)==len(set(keys))==3734
strict=[x for x in d if x['strict']=='True'];assert len(strict)==3713
assert sum(x['version'] in ['11','12'] for x in strict)==1435
assert all(float(x['lead'])>=5 and float(x['goalOdds'])>1 and float(x['saveOdds'])>1 for x in strict)
for row in strict:
 p=(1/float(row['goalOdds']))/(1/float(row['goalOdds'])+1/float(row['saveOdds']))
 assert abs(p-float(row['p_goal']))<1e-12
raw={}
for v in [11,12,13]:
 for row in read(R/f'penalty-results-v{v}.csv'):raw[(row['matchId'],row['round'])]=row['result']
assert all(raw[k]==x['result'] for k,x in zip(keys,d))
h=read(O/'hypothesis_results.csv')
def one(hyp,sp,group):return next(x for x in h if x['hypothesis']==hyp and x['split']==sp and x['group']==group)
assert one('H01','historical','after_unexpected_SAVE')['n']=='171'
assert float(one('H01','historical','after_unexpected_SAVE')['wins'])==130
assert one('H01','validation','after_unexpected_SAVE')['n']=='269'
assert float(one('H01','validation','after_unexpected_SAVE')['wins'])==191
assert one('H18','validation_post_H18','GOAL')['n']=='25'
assert one('H18','validation_post_H18','SAVE')['n']=='17'
hc=read(O/'rounds_with_chain_flags.csv')
assert all(int(x['hit_run'])<=4 and int(x['error_run'])<=4 for x in hc)
ss=read(O/'sequence_examples.csv');assert all(x['day_a']!=x['day_b'] for x in ss)
aux=[]
for path in [R/'src/main/java/com/ucl/Main.java']+[R/f'penalty-summary-v{v}.txt' for v in [11,12,13]]+[Path(r'C:\Users\evgen\Desktop\Penalty_Arena_Hypotheses_updated_H18_2026-10-29.md')]:
 b=path.read_bytes();aux.append(dict(path=str(path),bytes=len(b),sha256=hashlib.sha256(b).hexdigest()))
(O/'auxiliary_source_manifest.json').write_text(json.dumps(aux,ensure_ascii=False,indent=2),encoding='utf-8')
check=dict(source_csv_files=len(manifest),raw_sources_hash_unchanged=True,unique_clean_rounds=len(d),primary_rows=len(strict),historical_control_count=1435,formulas_checked=len(strict),raw_result_reconciliation=True,hypothesis_controls=True)
(O/'verification.json').write_text(json.dumps(check,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(check,ensure_ascii=False,indent=2))
