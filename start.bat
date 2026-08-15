@echo off
REM Liga o servidor. Pode ser executado com duplo clique.
REM
REM Nao precisa de Java instalado no sistema: usa o JDK 25 que o install.bat
REM colocou em runtime\.

cd /d "%~dp0"

set "JAVA=%~dp0runtime\jdk-25\bin\java.exe"
set "SERVER=%~dp0server"

REM Heap do servidor. Para mudar: set MEMORY=4G antes de chamar este arquivo.
if "%MEMORY%"=="" set "MEMORY=2G"

if not exist "%JAVA%" (
    echo.
    echo  ERRO: o JDK 25 nao foi encontrado em:
    echo    %JAVA%
    echo.
    echo  Execute o install.bat primeiro.
    echo.
    pause
    exit /b 1
)

if not exist "%SERVER%\plugins\WorldReset-1.0.0.jar" (
    echo.
    echo  ERRO: o plugin WorldReset nao esta instalado.
    echo.
    echo  Execute o install.bat primeiro.
    echo.
    pause
    exit /b 1
)

if not exist "%SERVER%\paper.jar" (
    echo.
    echo  ERRO: o paper.jar nao foi encontrado em %SERVER%.
    echo.
    echo  Execute o install.bat primeiro.
    echo.
    pause
    exit /b 1
)

cd /d "%SERVER%"

echo.
echo  Iniciando Paper 26.1.2 com %MEMORY% de heap...
echo  Conecte o Minecraft (versao 26.1.2) em: localhost:25565
echo.
echo  Para desligar, digite:  stop
echo.

"%JAVA%" -Xms%MEMORY% -Xmx%MEMORY% ^
    -XX:+UseG1GC ^
    -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC ^
    -XX:+AlwaysPreTouch ^
    -XX:G1HeapWastePercent=5 ^
    -XX:G1MixedGCCountTarget=4 ^
    -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:G1RSetUpdatingPauseTimePercent=5 ^
    -XX:SurvivorRatio=32 ^
    -XX:+PerfDisableSharedMem ^
    -XX:MaxTenuringThreshold=1 ^
    -jar paper.jar --nogui

echo.
echo  O servidor foi encerrado.
echo.
REM Sem este pause a janela fecha sozinha e voce nao consegue ler o motivo.
pause
