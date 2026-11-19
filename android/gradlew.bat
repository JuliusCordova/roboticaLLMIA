@echo off
setlocal
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
 powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DIR%bootstrap-wrapper.ps1"
 if errorlevel 1 exit /b 1
)
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
