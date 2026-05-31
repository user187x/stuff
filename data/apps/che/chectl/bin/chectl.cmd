@echo off
setlocal enableextensions

if not "%CHECTL_REDIRECTED%"=="1" if exist "%LOCALAPPDATA%\chectl\client\bin\chectl.cmd" (
  set CHECTL_REDIRECTED=1
  "%LOCALAPPDATA%\chectl\client\bin\chectl.cmd" %*
  goto:EOF
)

if not defined CHECTL_BINPATH set CHECTL_BINPATH="%~dp0chectl.cmd"
if exist "%~dp0..\bin\node.exe" (
  "%~dp0..\bin\node.exe" "%~dp0..\bin\run" %*
) else if exist "%LOCALAPPDATA%\oclif\node\node-22.22.3.exe" (
  "%LOCALAPPDATA%\oclif\node\node-22.22.3.exe" "%~dp0..\bin\run" %*
) else (
  node "%~dp0..\bin\run" %*
)
