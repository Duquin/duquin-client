@echo off
setlocal
set "VSVARS=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VSVARS%" (
  echo [!] vcvars64.bat not found
  exit /b 1
)
call "%VSVARS%" >nul
cd /d "%~dp0"
if exist DuquinLoader.exe del DuquinLoader.exe
if exist loader.res del loader.res
if exist loader.obj del loader.obj

rc /nologo loader.rc || exit /b 1
cl /nologo /O2 /EHsc /utf-8 loader.cpp loader.res /Fe:DuquinLoader.exe ^
   /link /SUBSYSTEM:CONSOLE winhttp.lib shell32.lib ole32.lib advapi32.lib user32.lib || exit /b 1

echo.
echo BUILD OK: %~dp0DuquinLoader.exe
endlocal
