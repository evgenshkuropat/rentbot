from pathlib import Path
import zipfile,hashlib,json,shutil
P=Path(__file__).parent;D=P/'distribution';D.mkdir(exist_ok=True)
java=D/'src/main/java/com/ucl';java.mkdir(parents=True,exist_ok=True)
for name in ['Main.java','Audit14.java']:shutil.copy2(P/name,java/name)
shutil.copy2(P/'PROTOCOL_V14_RU.md',D/'PROTOCOL_V14_RU.md')
lib=D/'lib';lib.mkdir(exist_ok=True)
repo=Path(r'C:\Users\evgen\.m2\repository\com\fasterxml\jackson\core')
deps=['jackson-databind/2.20.1/jackson-databind-2.20.1.jar','jackson-core/2.20.1/jackson-core-2.20.1.jar','jackson-annotations/2.20/jackson-annotations-2.20.jar']
for dep in deps:shutil.copy2(repo/dep,lib/Path(dep).name)
manifest='Manifest-Version: 1.0\r\nMain-Class: Main\r\nClass-Path: lib/jackson-databind-2.20.1.jar\r\n  lib/jackson-core-2.20.1.jar lib/jackson-annotations-2.20.jar\r\n\r\n'
with zipfile.ZipFile(D/'penalty-collector-v14.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/MANIFEST.MF',manifest)
 for p in (P/'build').glob('*.class'):
  if not p.name.startswith('Audit14Test'):z.write(p,p.name)
(D/'START_V14.cmd').write_text('@echo off\r\ncd /d "%~dp0"\r\nset "V14_JAVA=%USERPROFILE%\\.jdks\\ms-21.0.9\\bin\\java.exe"\r\nif not exist "%V14_JAVA%" (echo Java 21 is required. Edit V14_JAVA in this launcher. & pause & exit /b 1)\r\n"%V14_JAVA%" -Dfile.encoding=UTF-8 -jar penalty-collector-v14.jar\r\npause\r\n',encoding='ascii')
(D/'README_RU.txt').write_text('V14 — отдельная сборка Java 21. Запуск: START_V14.cmd. Данные пишутся только в эту папку. Остановка: Ctrl+C; не выключайте компьютер принудительно. V13 и его файлы не изменены.\n\nСначала техническая калибровка по PROTOCOL_V14_RU.md. UI-кандидаты не считаются WS-исходами. Вратарь по времени не подтверждён. Автоматическая запись видео и определение момента вылета мяча НЕ реализованы.\n\nОсновной источник доказательств: penalty-evidence-v14/events-<runId>.jsonl. Results CSV — проекция первых бинарных расчётов; исключайте все ключи из penalty-conflicts-v14.csv и SETTLEMENT_CONFLICT в журнале, включая случаи пересмотра. Небинарные состояния сохранены в журнале без догадок об их значении.\n\nЖурнал синхронно сохраняет каждый опрос и сообщение; это может увеличивать объём данных и задерживать обработку. Received timestamps относятся к получению callback приложением, не к серверу и не к кадру видео. Контролируйте место на диске. При ошибке записи программа завершается с кодом 74, чтобы не создавать ложное впечатление исправного сбора. После аварийного обрыва повреждённый журнал требует проверки, автоматического удаления нет.\n',encoding='utf-8-sig')
files={str(p.relative_to(D)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in D.rglob('*') if p.is_file() and p.name!='BUILD_SHA256.json'}
(D/'BUILD_SHA256.json').write_text(json.dumps(files,indent=2),encoding='utf-8')
with zipfile.ZipFile(P/'Penalty_Collector_V14.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in D.rglob('*'):
  if p.is_file():z.write(p,p.relative_to(D))
print('Packaged',len(files),'files; V14 zip bytes',(P/'Penalty_Collector_V14.zip').stat().st_size)
