@echo off
REM Recompila o plugin e instala em server\plugins\.
REM Use depois de alterar qualquer arquivo em src\.

cd /d "%~dp0"

echo.
echo  Recompilando o WorldReset...
echo.

REM O install.ps1 ja tem toda a logica de compilacao; reaproveitamos ela em vez
REM de duplicar. As etapas de download sao puladas porque tudo ja existe.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"

echo.
pause
