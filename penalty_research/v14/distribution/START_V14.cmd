@echo off
cd /d "%~dp0"
set "V14_JAVA=%USERPROFILE%\.jdks\ms-21.0.9\bin\java.exe"
if not exist "%V14_JAVA%" (echo Java 21 is required. Edit V14_JAVA in this launcher. & pause & exit /b 1)
"%V14_JAVA%" -Dfile.encoding=UTF-8 -jar penalty-collector-v14.jar
pause
