<#
    Instalacao do servidor WorldReset no Windows.

    Monta um servidor Paper completo e pronto para ligar. Nao exige nada
    instalado na maquina: o JDK 25 e baixado para dentro da pasta runtime\.

    Nao execute este arquivo diretamente com duplo clique - use o install.bat,
    que ja cuida da politica de execucao do PowerShell.
#>

$ErrorActionPreference = 'Stop'

# --------------------------------------------------------------- versoes fixas
#
# 26.1.2 e nao a 26.2 de proposito: e a versao mais nova que o EssentialsX 2.22.0
# declara suportar. Na 26.2 ele carrega mas avisa "unsupported server version".
#
$PaperVersion     = '26.1.2'
$PaperApiBuild    = '26.1.2.build.74-stable'
$EssentialsVersion = '2.22.0'
$JdkMajor         = '25'
$PluginVersion    = '1.0.0'

$Root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$Runtime = Join-Path $Root 'runtime'
$Server  = Join-Path $Root 'server'
$JdkHome = Join-Path $Runtime "jdk-$JdkMajor"

# O PowerShell 5.1 do Windows 10 ainda negocia TLS 1.0 por padrao, e os
# repositorios usados aqui so aceitam 1.2+. Sem esta linha os downloads falham
# com "Could not create SSL/TLS secure channel".
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# O JDK tem caminhos internos longos. Somados a uma pasta ja profunda, estouram
# o limite de 260 caracteres do Windows e a extracao falha no meio.
if ($Root.Length -gt 90) {
    Write-Host ''
    Write-Host "  AVISO: esta pasta tem um caminho longo ($($Root.Length) caracteres):" -ForegroundColor Yellow
    Write-Host "  $Root" -ForegroundColor Yellow
    Write-Host '  Se a extracao do JDK falhar, mova tudo para algo curto como C:\mcreset' -ForegroundColor Yellow
    Write-Host ''
}

# ------------------------------------------------------------------- utilidades

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  ok $msg"  -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  !! $msg"  -ForegroundColor Yellow }
function Fail($msg)       { Write-Host "erro: $msg" -ForegroundColor Red; exit 1 }

# Baixa so se o destino ainda nao existir, para o script ser re-executavel.
function Get-File($url, $dest) {
    if ((Test-Path $dest) -and ((Get-Item $dest).Length -gt 0)) {
        Write-Ok "$(Split-Path -Leaf $dest) ja existe, pulando download"
        return
    }
    $parent = Split-Path -Parent $dest
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

    $temp = "$dest.part"
    try {
        # ProgressPreference silencioso deixa o Invoke-WebRequest MUITO mais rapido
        # em arquivos grandes; a barra de progresso do PowerShell e o gargalo.
        $previous = $ProgressPreference
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $url -OutFile $temp -UseBasicParsing
        $ProgressPreference = $previous
    } catch {
        Fail "falha ao baixar $url`n$($_.Exception.Message)"
    }
    Move-Item -Force $temp $dest
    Write-Ok (Split-Path -Leaf $dest)
}

# ------------------------------------------------------------------------ JDK

function Install-Jdk {
    Write-Step "JDK $JdkMajor (o Paper $PaperVersion exige; nao usamos o Java do sistema)"

    if (Test-Path (Join-Path $JdkHome 'bin\java.exe')) {
        Write-Ok 'ja instalado'
        return
    }

    $arch = if ([Environment]::Is64BitOperatingSystem) { 'x64' } else { Fail 'Windows 32 bits nao e suportado' }
    $zip  = Join-Path $Runtime 'jdk.zip'

    Get-File "https://api.adoptium.net/v3/binary/latest/$JdkMajor/ga/windows/$arch/jdk/hotspot/normal/eclipse" $zip

    Write-Host '  extraindo o JDK (demora um pouco)...'
    # ExtractToDirectory em vez de Expand-Archive: o cmdlet nativo leva varios
    # minutos num JDK de 135 MB com milhares de arquivos pequenos.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $Runtime)

    $extracted = Get-ChildItem -Path $Runtime -Directory | Where-Object { $_.Name -like "jdk-$JdkMajor*" -and $_.Name -ne "jdk-$JdkMajor" } | Select-Object -First 1
    if ($extracted) {
        if (Test-Path $JdkHome) { Remove-Item -Recurse -Force $JdkHome }
        Move-Item $extracted.FullName $JdkHome
    }
    Remove-Item -Force $zip

    if (-not (Test-Path (Join-Path $JdkHome 'bin\java.exe'))) { Fail 'o JDK nao foi extraido corretamente' }
    Write-Ok (& (Join-Path $JdkHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1)
}

# ------------------------------------------------- dependencias de compilacao

function Install-BuildDeps {
    Write-Step 'Dependencias de compilacao do plugin'

    Get-File "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/$PaperApiBuild/paper-api-$PaperApiBuild.jar" (Join-Path $Runtime 'paper-api.jar')

    $central = 'https://repo1.maven.org/maven2'
    $libs    = Join-Path $Runtime 'libs'

    Get-File "$central/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"                           (Join-Path $libs 'adventure-api.jar')
    Get-File "$central/net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar"                           (Join-Path $libs 'adventure-key.jar')
    Get-File "$central/net/kyori/adventure-text-minimessage/5.2.0/adventure-text-minimessage-5.2.0.jar" (Join-Path $libs 'adventure-minimessage.jar')
    Get-File "$central/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"                       (Join-Path $libs 'examination-api.jar')
    Get-File "$central/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar"                         (Join-Path $libs 'annotations.jar')
    Get-File "$central/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"                                  (Join-Path $libs 'jspecify.jar')
    Get-File "$central/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar"                          (Join-Path $libs 'guava.jar')
    Get-File "$central/net/md-5/bungeecord-chat/1.20-R0.2/bungeecord-chat-1.20-R0.2.jar"                (Join-Path $libs 'bungeecord-chat.jar')
}

# --------------------------------------------------------------------- servidor

function Install-Paper {
    Write-Step "Paper $PaperVersion"

    $jar = Join-Path $Server 'paper.jar'
    if ((Test-Path $jar) -and ((Get-Item $jar).Length -gt 0)) {
        Write-Ok 'ja instalado'
        return
    }

    try {
        $builds = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/$PaperVersion/builds" -Headers @{ 'accept' = 'application/json' } -UseBasicParsing
    } catch {
        Fail "nao consegui consultar as builds do Paper $PaperVersion`n$($_.Exception.Message)"
    }

    $stable = @($builds | Where-Object { $_.channel -eq 'STABLE' })
    if ($stable.Count -eq 0) { $stable = @($builds) }
    $url = $stable[0].downloads.'server:default'.url
    if (-not $url) { Fail "nao encontrei o download da build do Paper $PaperVersion" }

    Get-File $url $jar
}

function Install-Essentials {
    Write-Step "EssentialsX $EssentialsVersion"
    $base    = "https://github.com/EssentialsX/Essentials/releases/download/$EssentialsVersion"
    $plugins = Join-Path $Server 'plugins'

    Get-File "$base/EssentialsX-$EssentialsVersion.jar"      (Join-Path $plugins "EssentialsX-$EssentialsVersion.jar")
    Get-File "$base/EssentialsXSpawn-$EssentialsVersion.jar" (Join-Path $plugins "EssentialsXSpawn-$EssentialsVersion.jar")
}

# ------------------------------------------------------------------- configuracao

function Write-Configs {
    Write-Step 'Configuracao do servidor'

    if (-not (Test-Path $Server)) { New-Item -ItemType Directory -Path $Server -Force | Out-Null }

    Set-Content -Path (Join-Path $Server 'eula.txt') -Value 'eula=true' -Encoding ASCII
    Write-Ok 'eula.txt (aceito em seu nome - veja https://aka.ms/MinecraftEULA)'

    # So escreve se ainda nao existir: um server.properties editado por voce nao
    # pode ser sobrescrito por uma reexecucao do instalador.
    $properties = Join-Path $Server 'server.properties'
    if (Test-Path $properties) {
        Write-Ok 'server.properties ja existe, preservado'
    } else {
        @'
# O mundo principal PRECISA ser o lobby. O WorldReset o cria vazio e nunca o
# apaga; os mundos jogaveis vivem em outros nomes e sao destruidos a cada morte.
level-name=lobby

# Somente contas originais (premium). A Mojang autentica cada login.
online-mode=true

motd=WorldReset - morreu, mundo novo
max-players=10
server-port=25565
spawn-protection=0
view-distance=8
simulation-distance=6
difficulty=normal
gamemode=survival
pvp=true
allow-nether=true
allow-flight=false
enable-command-block=false
sync-chunk-writes=false
'@ | Set-Content -Path $properties -Encoding ASCII
        Write-Ok 'server.properties'
    }

    $bukkit = Join-Path $Server 'bukkit.yml'
    if ((Test-Path $bukkit) -and (Select-String -Path $bukkit -Pattern 'generator: WorldReset' -Quiet)) {
        Write-Ok 'bukkit.yml ja configurado'
    } else {
        @'
# Faz o lobby nascer vazio em vez de gerar um mundo inteiro que ninguem usa.
worlds:
  lobby:
    generator: WorldReset

settings:
  allow-end: true
  shutdown-message: Servidor encerrado
'@ | Set-Content -Path $bukkit -Encoding ASCII
        Write-Ok 'bukkit.yml'
    }
}

# ------------------------------------------------------------------------ build

function Build-Plugin {
    Write-Step 'Compilando o WorldReset'

    $javac  = Join-Path $JdkHome 'bin\javac.exe'
    $jarExe = Join-Path $JdkHome 'bin\jar.exe'
    $build  = Join-Path $Root 'target'
    $classes = Join-Path $build 'classes'

    if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
    New-Item -ItemType Directory -Path $classes -Force | Out-Null

    # ---------------------------------------------------------------------
    #  Todas as opcoes vao para dentro do argfile, inclusive -cp e -d.
    #
    #  Motivo: se o caminho tiver espaco - e "C:\Users\Joao Silva\..." tem -
    #  o javac quebra o argumento no espaco e reclama de "invalid flag". No
    #  argfile cada valor fica entre aspas, o que resolve, e de quebra tira do
    #  caminho o quoting do PowerShell para processos nativos, que e uma
    #  segunda fonte do mesmo problema.
    #
    #  As barras invertidas viram barras normais porque, dentro de aspas no
    #  argfile, o javac trata '\' como caractere de escape - "C:\Users" seria
    #  lido errado. O Java aceita '/' como separador no Windows.
    # ---------------------------------------------------------------------
    function ConvertTo-ArgPath($path) { '"' + ($path -replace '\\', '/') + '"' }

    # No Windows o separador de classpath e ';', nao ':'
    $classpath = @(Join-Path $Runtime 'paper-api.jar')
    $classpath += (Get-ChildItem -Path (Join-Path $Runtime 'libs') -Filter '*.jar' | ForEach-Object { $_.FullName })
    $classpathString = ($classpath | ForEach-Object { $_ -replace '\\', '/' }) -join ';'

    $lines = @(
        '-nowarn'
        '-encoding UTF-8'
        "-cp `"$classpathString`""
        "-d $(ConvertTo-ArgPath $classes)"
    )
    $lines += Get-ChildItem -Path (Join-Path $Root 'src\main\java') -Filter '*.java' -Recurse |
        ForEach-Object { ConvertTo-ArgPath $_.FullName }

    # UTF-8 sem BOM: do Java 18 em diante o charset padrao e UTF-8 (JEP 400),
    # e um BOM no inicio do argfile viraria lixo na primeira opcao.
    # O cast para [string[]] e necessario: WriteAllLines nao aceita o Object[]
    # que o PowerShell produz por padrao.
    $sourcesFile = Join-Path $build 'sources.txt'
    [System.IO.File]::WriteAllLines($sourcesFile, [string[]]$lines, (New-Object System.Text.UTF8Encoding $false))

    # Entra na pasta do build para referenciar o argfile por nome relativo. Assim
    # nem o proprio "@arquivo" precisa carregar um caminho com espaco.
    Push-Location $build
    try {
        & $javac '@sources.txt'
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { Fail 'a compilacao falhou' }

    # Substitui os placeholders que o Maven normalmente resolveria.
    #
    # A gravacao usa UTF8Encoding($false) em vez de "Set-Content -Encoding UTF8"
    # porque o PowerShell 5.1 do Windows 10 escreve BOM nesse modo, e um BOM no
    # inicio do plugin.yml faz o parser YAML do Bukkit recusar o arquivo - o
    # plugin simplesmente nao carrega, com uma mensagem que nao explica nada.
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    $description = 'Recria o mundo inteiro sempre que qualquer jogador online morre.'

    Get-ChildItem -Path (Join-Path $Root 'src\main\resources') -File | ForEach-Object {
        $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8).
            Replace('${project.version}', $PluginVersion).
            Replace('${project.description}', $description)
        [System.IO.File]::WriteAllText((Join-Path $classes $_.Name), $content, $utf8NoBom)
    }

    $jarPath = Join-Path $build "WorldReset-$PluginVersion.jar"
    if (Test-Path $jarPath) { Remove-Item -Force $jarPath }
    & $jarExe --create --file $jarPath -C $classes .
    if ($LASTEXITCODE -ne 0) { Fail 'o empacotamento falhou' }

    $plugins = Join-Path $Server 'plugins'
    if (-not (Test-Path $plugins)) { New-Item -ItemType Directory -Path $plugins -Force | Out-Null }
    Copy-Item -Force $jarPath $plugins

    Write-Ok "server\plugins\WorldReset-$PluginVersion.jar"
}

# -------------------------------------------------------------------------- main

Write-Host ''
Write-Host '  WorldReset - instalacao do servidor'
Write-Host "  Paper $PaperVersion | EssentialsX $EssentialsVersion | JDK $JdkMajor"
Write-Host ''

Install-Jdk
Install-BuildDeps
Install-Paper
Install-Essentials
Write-Configs
Build-Plugin

Write-Host ''
Write-Step 'Pronto.'
Write-Host ''
Write-Host '  Ligar o servidor:   start.bat  (duplo clique)'
Write-Host '  Recompilar plugin:  build.bat'
Write-Host ''
Write-Host '  O primeiro boot baixa o jar da Mojang (~50 MB) e pre-gera o proximo'
Write-Host '  mundo; leva alguns minutos. Do segundo em diante sobe em ~15s.'
Write-Host ''
