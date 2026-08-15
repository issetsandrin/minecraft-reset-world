@echo off
REM Instalacao do servidor WorldReset no Windows.
REM Pode ser executado com duplo clique.

cd /d "%~dp0"

echo.
echo  Instalando o servidor WorldReset...
echo  Isso baixa cerca de 400 MB e pode levar alguns minutos.
echo.

REM -ExecutionPolicy Bypass evita ter que mexer na politica do PowerShell,
REM que por padrao no Windows 10 recusa rodar scripts .ps1 baixados.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"

if errorlevel 1 (
    echo.
    echo  ======================================================
    echo   A INSTALACAO FALHOU. A mensagem de erro esta acima.
    echo  ======================================================
) else (
    echo.
    echo  Instalacao concluida. Agora execute o start.bat.
)

echo.
REM O pause e o que impede a janela de fechar sozinha e voce perder o erro.
pause
