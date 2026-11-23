from pathlib import Path
import csv,json,collections
root=Path(r'C:\Users\evgen\IdeaProjects\penalty-arena-collector')
for v in (11,12,13):
 for p in sorted(root.glob(f'*-v{v}.csv')):
  with p.open(encoding='utf-8-sig',newline='') as f:
   r=list(csv.DictReader(f))
  print(p.name,len(r),'columns',list(r[0]) if r else [])
  if any(x in p.name for x in ['joined','results','quality','ui-matches','keeper-links','targets']):
   for c in ['result','source','confidence','quality','selectedSector','actualBallSector','keeperSector','sector','evidence']:
    if r and c in r[0]: print(' ',c,dict(collections.Counter(x[c] for x in r).most_common(12)))
  if r: print(' time',r[0].get('timestamp',''),r[-1].get('timestamp',''))
