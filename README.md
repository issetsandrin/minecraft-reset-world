# WorldReset

Recria o mundo inteiro — nova seed, inventário zerado, XP zerado, avanços revogados —
sempre que **qualquer jogador online morre**.

Feito para **Paper 26.1.2** (o esquema `ano.drop` substituiu o antigo `1.21.x`). Compila e
roda em **Java 25**.

> **Por que 26.1.2 e não a 26.2?** Porque o servidor roda com EssentialsX, e a última
> release dele (2.22.0) declara suporte até 26.1.2 — na 26.2 ele carrega mas imprime
> `unsupported server version`. Ficar na versão que o EssentialsX suporta vale mais do que
> estar no drop mais novo. Testado: na 26.1.2 o aviso não aparece.

---

## Como funciona

O problema central: o servidor não deixa apagar um mundo carregado, e não deixa descarregar
um mundo que ainda tenha jogador dentro. Gerar um mundo novo do zero no momento da morte
travaria a thread principal por vários segundos.

A solução é não gerar nada na hora da morte:

```
                   ┌──────────────────────────────┐
                   │   lobby (void, permanente)   │  ← level-name do servidor
                   └──────────────────────────────┘
                                  ▲
                  evacuação │     │     │ swap
                            │     │     ▼
   ┌────────────────────────┴─┐   ┌─────┴────────────────────┐
   │  slot A  — EM USO        │   │  slot B  — PRONTO        │
   │  wr_arena_a              │   │  wr_arena_b              │
   │  wr_arena_a_nether       │   │  wr_arena_b_nether       │
   │  wr_arena_a_the_end      │   │  wr_arena_b_the_end      │
   └──────────────────────────┘   └──────────────────────────┘
              │                              ▲
              │ apagado após o swap          │ pré-gerado em background
              └──────────────────────────────┘   logo após o reset anterior
```

Enquanto o slot A está em jogo, o slot B **já existe e já foi gerado** em background. Na
morte, o servidor só troca o ponteiro entre eles — por isso o swap leva menos de um
segundo em vez de dezenas.

### Sequência exata de um reset

1. `PlayerDeathEvent` dispara. Drops e XP são descartados.
2. Contagem regressiva de 5s com título na tela e som a cada segundo.
3. Todo jogador ainda na tela de morte é **respawnado à força** — sem isso ele continua
   contando como presente no mundo e o Bukkit se recusa a descarregá-lo.
4. Todos são teleportados ao lobby.
5. Busca de spawn seguro no mundo novo (detalhada abaixo).
6. Wipe de todos: inventário, ender chest, XP, efeitos, avanços, estatísticas, receitas,
   vida/fome, respawn point. Quem está **offline** tem o `.dat`/`.json` apagado.
7. Todos são teleportados ao mundo novo, invulneráveis por 10s.
8. O slot antigo é descarregado, a pasta é **renomeada na hora** e apagada fora da thread
   principal.
9. O próximo slot é criado e a pré-geração recomeça.

O passo 8 renomeia antes de apagar de propósito: uma remoção lenta de dezenas de milhares
de arquivos nunca pode colidir com a criação de um mundo de mesmo nome.

---

## Instalação — Windows

Baixe o repositório ([Code → Download ZIP](https://github.com/issetsandrin/minecraft-reset-world/archive/refs/heads/main.zip))
e extraia numa pasta de caminho curto, por exemplo `C:\mcreset`.

Depois, com **duplo clique**, nesta ordem:

1. **`install.bat`** — baixa tudo e compila. Demora alguns minutos (~400 MB).
2. **`start.bat`** — liga o servidor.

Conecte o Minecraft **versão 26.1.2** em `localhost:25565`.

> **Por que uma pasta de caminho curto?** O JDK tem arquivos com caminhos internos longos.
> Numa pasta já profunda (`C:\Users\...\Downloads\...\minecraft-reset-world-main\`) o total
> passa do limite de 260 caracteres do Windows e a extração falha no meio. O instalador
> avisa quando detecta esse risco.

Os `.bat` terminam com `pause`, então a janela **não fecha sozinha** — se algo falhar, a
mensagem de erro fica na tela.

Para recompilar depois de mexer no código: **`build.bat`**.

## Abrir para os amigos jogarem

O servidor roda no seu PC; o `playit.gg` cria um endereço público que aponta para ele, sem
mexer no roteador — o que também contorna CGNAT, comum em fibra no Brasil.

### 1. Whitelist primeiro

Faça isto **antes** de expor o servidor. Na janela do servidor (`start.bat`), nesta ordem:

```
whitelist add SeuNick
whitelist add NickDoAmigo
whitelist on
```

> **A ordem importa.** Se você ligar a whitelist antes de se adicionar, você mesmo fica de
> fora e vai precisar voltar ao console para entrar.

Um servidor exposto recebe varredura de bot em questão de horas. Sem whitelist, qualquer
estranho entra — e como **o mundo inteiro é apagado quando alguém morre**, basta um
desconhecido morrer de propósito para zerar o progresso de todos.

Para adicionar mais gente depois: `whitelist add <nick>`. Para ver a lista:
`whitelist list`.

### 2. Túnel do playit.gg

1. Crie uma conta em [playit.gg](https://playit.gg).
2. Baixe o agente para Windows e execute.
3. O programa mostra um **link de ativação** — abra no navegador, faça login e confirme o
   agente.
4. Em [playit.gg/account/tunnels](https://playit.gg/account/tunnels), clique em **Add
   Tunnel** e escolha **Minecraft Java**. O endereço local já vem preenchido como
   `127.0.0.1:25565`, que é exatamente onde este servidor escuta.
5. O painel mostra um domínio parecido com `algo.gl.at.ply.gg`. **É esse endereço que seus
   amigos usam** no Minecraft.

### 3. Para jogar

Precisam estar rodando ao mesmo tempo:

- o servidor (`start.bat`);
- o agente do playit.gg.

E cada amigo precisa de:

- conta **original** da Mojang/Microsoft (`online-mode=true`);
- Minecraft na versão **26.1.2**;
- estar na whitelist.

### Limites

O servidor só existe enquanto seu PC estiver ligado com as duas janelas abertas. Se quiser
algo no ar 24/7, o caminho é um VPS Linux — os scripts `.sh` deste repositório cobrem esse
caso.

### Memória

O heap padrão é **6 GB**, reservados na inicialização (`-Xms` e `-Xmx` iguais, com
`AlwaysPreTouch`). Isso significa que o processo toma 6 GB de imediato, não conforme
precisa.

Só use esse valor se a máquina tiver folga real: **16 GB de RAM** é confortável, 8 GB fica
apertado — o Windows e o próprio Minecraft do jogador também precisam de espaço. Numa
máquina de 8 GB, prefira 4 GB.

Para mudar sem editar arquivo, no `cmd`:

```
set MEMORY=4G
start.bat
```

No Linux: `MEMORY=4G ./start.sh`.

## Instalação — Linux

```bash
git clone git@github.com:issetsandrin/minecraft-reset-world.git
cd minecraft-reset-world
./install.sh
./start.sh
```

O `install.sh` não pede nada instalado além de `curl`, `tar`, `unzip` e `python3` — nem
Java, nem Maven. Ele baixa e monta:

| | |
|---|---|
| JDK 25 portátil | em `runtime/`, porque o Paper 26.1.2 exige Java 25 e o do sistema costuma ser mais antigo |
| Paper 26.1.2 | build estável mais recente, resolvida pela API do PaperMC |
| EssentialsX 2.22.0 | core + Spawn |
| `server.properties`, `bukkit.yml`, `eula.txt` | já com `level-name=lobby` e o gerador void |
| WorldReset | compilado a partir do código deste repositório |

É re-executável: o que já foi baixado é pulado, e um `server.properties` que você tenha
editado **não** é sobrescrito.

O primeiro boot baixa o jar da Mojang (~50 MB) e pré-gera o próximo mundo — leva alguns
minutos. Do segundo em diante o servidor sobe em ~15s.

Heap padrão **6 GB**; para mudar: `MEMORY=4G ./start.sh` (Linux) ou `set MEMORY=4G` antes do `start.bat` (Windows).

### Contas originais apenas

`online-mode=true`. Cada login é autenticado pela Mojang, então ninguém entra com o nick de
outra pessoa e os UUIDs são os reais — o que também significa que o wipe de jogador offline
casa com a conta certa.

### Alterando o plugin

```bash
./build.sh && ./start.sh
```

O `build.sh` usa o JDK de `runtime/` e as libs já baixadas — compila sem Maven e sem rede.
Se preferir Maven, o `pom.xml` faz o mesmo com `mvn clean package`.

### O que o repositório não guarda

`runtime/`, `server/` e `target/` estão no `.gitignore`: são ~575 MB de binários baixáveis
(JDK, Paper, EssentialsX) e mundos gerados. O repositório tem só o código-fonte e os
scripts; o `install.sh` reconstrói o resto.

## Requisitos (se for montar em outro servidor)

| Item | Versão |
|---|---|
| Servidor | Paper 26.1.2 (não funciona em Spigot — usa `getChunkAtAsync`) |
| Java | 25 |

Num servidor já existente, copie o jar para `plugins/` e ajuste duas coisas:

1. **`server.properties`** — o mundo principal precisa ser o lobby:

   ```properties
   level-name=lobby
   ```

2. **`bukkit.yml`** — faz o lobby nascer vazio:

   ```yaml
   worlds:
     lobby:
       generator: WorldReset
   ```

Sem o passo 2 o plugin ainda funciona — cria o lobby em runtime — mas o servidor gera um
mundo vanilla inútil a cada boot. O plugin avisa no console quando detecta isso.

---

## Painel de vida

Cada jogador vê, no canto superior direito, a vida de **todos os outros** em tempo real:

```
                           ❤ VIDAS   RESET #3
                           12:34 de pe
                           recorde 45:12
                           Joao        5
                            ❤❤♡♡♡♡♡♡♡♡   3 fome
                           Maria       ☠1
                            ❤❤❤❤❤❤❤♡♡♡  18 fome  8 arm
                           Pedro
                            ❤❤❤❤❤❤❤❤❤❤  20 fome  5 arm
```

- **Corações mudam de cor** conforme o perigo: verde acima de 70%, amarelo acima de 30%,
  vermelho abaixo. Quem está vivo nunca aparece com zero corações, mesmo com 1 de vida.
- **`☠` conta quantas vezes cada um já morreu** — ou seja, quantas vezes já apagou o mundo
  de todos. Quem nunca morreu não exibe o marcador. A contagem é acumulada e vive em
  `deaths.yml`, fora do alcance do wipe que zera as estatísticas nativas.
- **O título mostra o ciclo atual** (`RESET #7`): quantas tentativas já foram perdidas.
- **O cronômetro** conta desde a chegada ao mundo novo até a morte que o destrói, e o
  recorde fica logo abaixo. Ele só corre **enquanto há alguém conectado** — servidor ligado
  sozinho de madrugada não infla o tempo, nem as horas em que fica desligado.
- Fome e armadura aparecem como `ícone valor`, com o ícone em cinza.

> **Sobre os ícones:** o Minecraft só desenha caracteres do BMP (até U+FFFF). Emojis como a
> coxinha 🍗 e o escudo 🛡 estão **fora** dessa faixa e viram quadradinho vazio no cliente
> vanilla — usar os ícones reais do jogo exigiria um resource pack instalado por cada
> jogador. Os três ícones são configuráveis em `health-display.icons`, então trocar leva
> segundos se algum não ficar bom na sua fonte.
- **Quem está ferido sobe para o topo.** Neste servidor a morte de qualquer um apaga o
  mundo de todos, então quem está prestes a morrer é a informação urgente, não um detalhe.
- **Você não aparece na sua própria lista** — sua vida já está na HUD normal do jogo.
- Atualiza a cada meio segundo, sem piscar: as linhas são times cujo prefixo é reescrito,
  em vez de entradas removidas e readicionadas.

Ajustes em `health-display` no `config.yml`: `enabled`, `update-ticks`,
`show-hunger-armor` e `highlight-danger`.

> **Sobre a posição:** o canto superior *esquerdo* não é alcançável por plugin — o
> Minecraft não expõe HUD naquela área. Chegar lá exigiria um resource pack ou mod
> instalado em cada cliente. A barra lateral é o equivalente nativo mais próximo.

Com `show-hunger-armor` ligado cada jogador ocupa duas linhas, e a barra lateral desenha no
máximo 15 — cabem 7 jogadores. Desligando, cabem 15. Se alguém não couber, o painel diz
quantos ficaram de fora em vez de omitir em silêncio.

## Portais entre dimensões

O Minecraft só liga automaticamente o overworld **principal** do servidor ao nether e ao end
dele. Dimensões criadas por plugin ficam de fora dessa conta: ao voltar por um portal, o
servidor não encontra o mundo de origem e deposita o jogador no ponto de nascimento — é por
isso que existem plugins dedicados só a resolver isso.

O `PortalListener` faz essa ligação pelo nome, nos dois sentidos:

```
wr_arena_a  ⇄  wr_arena_a_nether     (escala 8:1, como no vanilla)
wr_arena_a  ⇄  wr_arena_a_the_end    (plataforma de obsidiana na ida)
```

Como a regra é por sufixo, vale para qualquer slot sem precisar saber qual está ativo.

## Comandos

| Comando | Efeito |
|---|---|
| `/worldreset status` | Slot ativo, slot de espera, progresso da pré-geração, cooldown |
| `/worldreset force` | Dispara um reset sem precisar de morte |
| `/worldreset pregen` | Recria e repre-gera o slot de espera |
| `/worldreset tp` | Teleporta você ao spawn do mundo ativo |
| `/worldreset reload` | Recarrega o `config.yml` |

Permissões: `worldreset.admin` (padrão: op) e `worldreset.exempt` — a morte de quem tem
essa permissão **não** dispara o reset (útil para staff em modo espectador).

---

## As duas armadilhas que o plugin trata

### Loop de morte em cascata

Se o jogador nascer dentro de lava ou cair no void no mundo novo, a morte seguinte
dispararia outro reset, e assim por diante até o servidor morrer. Três camadas impedem
isso:

- **Busca de spawn seguro** (`SafeSpawnFinder`): varre em anéis crescentes a partir do
  spawn procurando chão sólido, sem lava/cacto/gelo/fogo, com 2 blocos de ar acima e sem
  perigo num raio de 2 blocos. Se nada servir, **constrói uma plataforma de pedra** — um
  mundo onde nada foi considerado seguro ainda precisa ser jogável.
- **Invulnerabilidade de 10s** ao chegar (`reset.grace-seconds`).
- **Cooldown de 30s** (`reset.min-seconds-between-resets`): mortes nesse intervalo são
  ignoradas e registradas no console.

A busca nunca sai do raio pré-gerado. Sair dele obrigaria o servidor a gerar terreno na
thread principal exatamente no pior momento possível.

### Lobby precisa ser inofensivo

Qualquer forma de morrer no lobby dispararia um reset durante o próprio reset. Por isso o
lobby tem dano cancelado, fome cancelada, mobs desligados, dano de queda desligado, ciclo
de dia/clima parado e uma plataforma de `BARRIER` sob o spawn.

---

## Integração com EssentialsX

O EssentialsX 2.22.0 é instalado junto e é o motivo de o alvo ser 26.1.2: é a versão mais
nova que ele declara suportar. Verificado nas duas — na 26.2 o boot imprime
`You are running an unsupported server version!`; na 26.1.2 esse aviso não aparece.

### Jogador novo ficava preso no lobby

O EssentialsX tem `newbies.spawnpoint` no `config.yml` dele: no **primeiro acesso** de cada
jogador, ele o teleporta para o spawn do Essentials — que, nunca tendo sido definido, é o
do mundo principal, ou seja, o lobby. E faz isso *depois* do nosso teleporte. Resultado:
todo jogador novo caía no mundo vazio e não saía mais.

O plugin resolve sozinho, sem depender da configuração de terceiros: depois de entrar, ele
reconfere algumas vezes se o jogador chegou mesmo ao mundo ativo (`join.verify-attempts`) e
o traz de volta se algo o puxou.

Se quiser eliminar a disputa na origem, edite `plugins/Essentials/config.yml`:

```yaml
newbies:
  spawnpoint: none
```

### Dados presos ao mundo destruído

A integração é detectada automaticamente. O EssentialsX guarda posições **com o nome do mundo** — e o
mundo troca de nome a cada reset (A → B → A). Sem tratamento, depois do primeiro reset
`/home` e `/back` apontam para um mundo apagado e o console enche de erro.

Conforme configurado, o plugin remove **apenas** o que referencia mundo:

- `homes`, `lastlocation` e `logoutlocation` de cada `userdata/*.yml`;
- reescreve `spawn.yml` apontando para o spawn do mundo novo (todos os grupos, não só o
  `default`);
- executa `ess reload` em seguida — sem isso o EssentialsX reescreveria os arquivos com o
  cache que ainda tem em memória e desfaria tudo.

**Preservados entre runs:** saldo, nickname, kits, ignores, mute/ban.

Para zerar também esses, apague o `userdata/` inteiro — ou peça e eu adiciono a opção.

Recomendo ainda desligar `spawn-on-join` no `config.yml` do EssentialsX, ou configurá-lo
para o lobby: o plugin já decide o destino de quem entra, um tick depois do join,
justamente para não disputar com o EssentialsX Spawn.

---

## Configuração

Tudo em `plugins/WorldReset/config.yml`. Os valores que mais importam:

| Chave | Padrão | O que faz |
|---|---|---|
| `reset.countdown-seconds` | 5 | Delay entre a morte e o reset |
| `reset.min-seconds-between-resets` | 30 | Trava anti-cascata |
| `reset.grace-seconds` | 10 | Invulnerabilidade ao chegar no mundo novo |
| `pregeneration.radius-blocks` | 250 | Raio gerado em background (~1090 chunks) |
| `pregeneration.max-concurrent-chunks` | 8 | Teto de chunks em voo |
| `wipe.statistics` | true | Caro: percorre todo material e entidade por jogador |
| `wipe.offline-players` | true | Apaga `.dat` de quem não estava conectado |

### Custo de disco

Cada slot guarda overworld + nether + end. Com raio 250, o overworld pré-gerado ocupa
algo entre 150 e 400 MB dependendo do bioma. Como existem dois slots, conte o dobro. Para
economizar, desligue `arena.create-the-end` ou reduza o raio.

---

## Estrutura

```
dev/versotech/worldreset/
├── WorldResetPlugin.java          ciclo de vida, wiring, gerador do lobby
├── config/
│   ├── ResetSettings.java         config.yml tipado
│   └── Messages.java              MiniMessage + placeholders
├── world/
│   ├── Arena.java                 os 3 nomes de um slot
│   ├── SlotState.java             qual slot está ativo (state.yml)
│   ├── WorldLifecycle.java        criar / descarregar / apagar
│   ├── ChunkPregenerator.java     geração assíncrona com teto de concorrência
│   ├── SafeSpawnFinder.java       busca em anéis + plataforma de emergência
│   └── VoidChunkGenerator.java    lobby vazio
├── player/PlayerWiper.java        wipe online (API) e offline (arquivos)
├── reset/ResetCoordinator.java    orquestra o ciclo e as travas
├── listener/
│   ├── DeathListener.java         o gatilho
│   └── GuardListener.java         lobby seguro + destino de join/respawn
├── integration/EssentialsHook.java
└── command/WorldResetCommand.java
```

---

## Nota sobre o layout de mundos do 26.x

O Minecraft 26.x reorganizou o disco, e isso afeta diretamente um plugin que apaga mundos:

```
lobby/                                  ← raiz (level.dat, players/, dimensions/)
├── level.dat
├── players/                            ← era playerdata/ + stats/ + advancements/
│   └── data/
└── dimensions/minecraft/
    ├── overworld/                      ← o lobby em si
    ├── wr_arena_a/                     ← mundos de plugin moram AQUI
    └── wr_arena_b/
```

Duas consequências que o plugin trata:

- `world.getWorldFolder()` do mundo principal devolve `lobby/dimensions/minecraft/overworld`,
  **não** `lobby/`. `WorldPaths` resolve a raiz real subindo até o ancestral de `dimensions/`.
- `keepSpawnLoaded` virou no-op nesta versão — o servidor não segura mais spawn chunks. O
  plugin não depende disso: os chunks do próximo mundo já estão gravados em disco pela
  pré-geração.

O plugin também funciona no layout antigo (pastas irmãs no diretório do servidor); os dois
caminhos são tratados.

## O que foi testado

> **Os scripts Windows (`.bat` / `.ps1`) não foram executados em Windows.** Foram escritos
> a partir do que os scripts Linux — esses sim, testados de ponta a ponta — fazem, com a
> URL e a estrutura interna do JDK Windows verificadas. O plugin em si é idêntico nos dois
> sistemas: o que muda é só o instalador. Se algo falhar no Windows, a mensagem aparece na
> janela (os `.bat` têm `pause`) e é rápido de corrigir.

Validado em servidor real (Linux) — primeiro na 26.2, depois na **26.1.2**, que é o alvo
final. Quatro resets completos no total:

- Plugin carrega e habilita; lobby nasce void com plataforma de barrier confirmada por
  sonda de bloco (`y=64` é ar, `y=99` é barrier)
- Os 6 mundos (2 slots × 3 dimensões) são criados
- Pré-geração: 1089 chunks em ~25s sem travar o servidor
- Ciclo de reset repetido, alternando A→B→A, com `reset-count` persistido
- Mundos antigos renomeados e removidos fisicamente do disco, sem sobras `.deleting-*`
- EssentialsX + EssentialsXSpawn 2.22.0 habilitam junto, **sem** o aviso de versão não
  suportada; o hook roda no reset e reporta corretamente quando não há o que alterar
- Boot completo em ~16s
- Zero erros ou exceções no console

O código compilou sem uma única alteração ao trocar o alvo de 26.2 para 26.1.2.

**Não testado** (exige jogador conectado, o que o teste headless não cobre): a morte real
disparando o gatilho, o wipe de inventário/XP/avanços, o respawn forçado da tela de morte,
os teleportes, e o efeito prático do hook do EssentialsX sobre `homes`/`spawn.yml` — esses
arquivos só passam a existir depois que alguém joga.

## Limitações conhecidas

- **Não suporta Folia.** O scheduler regionalizado do Folia exige uma reescrita das
  chamadas de agendamento.
- **Não suporta Spigot.** A pré-geração depende de `getChunkAtAsync`, que é API do Paper.
- Se um jogador travar num mundo que se recusa a descarregar, o slot antigo não é apagado
  naquele momento. O plugin registra `SEVERE` e tenta de novo ao preparar o próximo slot.
- O nether e o end **não** são pré-gerados, só criados. A primeira entrada no nether tem
  o lag de geração normal.
