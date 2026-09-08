#!/usr/bin/env bash
#
# Prepara a VM Ubuntu para receber a aplicacao. Roda uma vez, na mao.
#
#   sudo bash infra/provisionar.sh
#
# ATENCAO: esta VM e COMPARTILHADA com outra aplicacao. O script foi escrito
# para conviver com o que ja esta rodando:
#
#   - nao para, reinicia nem reconfigura servico algum que nao seja o proprio
#   - nao mexe em Nginx, Docker ou qualquer proxy existente
#   - so instala Java se nao houver, e nunca troca a versao de um Java ja
#     instalado (outra aplicacao pode depender dele)
#   - confere se a porta esta livre ANTES de instalar qualquer coisa, e para
#     com instrucao clara se estiver ocupada
#   - roda a aplicacao como usuario proprio, sem acesso ao resto da maquina
#
# E idempotente: rodar duas vezes nao quebra nada.

set -euo pipefail

DIRETORIO=/opt/rota-vital
USUARIO_APP=rotavital
VERSAO_JAVA=21
PORTA=8081   # 8080 costuma estar ocupada; ver application-prod.properties

# Cores so quando a saida e um terminal. Em log de CI, texto puro.
if [ -t 1 ]; then
    VERDE='\033[0;32m'; AMARELO='\033[0;33m'; VERMELHO='\033[0;31m'; FIM='\033[0m'
else
    VERDE=''; AMARELO=''; VERMELHO=''; FIM=''
fi

info()  { echo -e "${VERDE}==>${FIM} $1"; }
aviso() { echo -e "${AMARELO}==>${FIM} $1"; }
erro()  { echo -e "${VERMELHO}==>${FIM} $1" >&2; }

if [ "$EUID" -ne 0 ]; then
    erro "Rode com sudo: sudo bash infra/provisionar.sh"
    exit 1
fi

# -----------------------------------------------------------------------------
# 0. O que ja existe nesta maquina
#
# Antes de instalar qualquer coisa, mostra o cenario. Numa VM compartilhada,
# comecar sem olhar e como mexer no codigo sem ler.
# -----------------------------------------------------------------------------
info "Inspecionando a maquina..."
echo
echo "  Memoria:"
free -h | sed 's/^/    /'
echo
echo "  Portas em uso:"
if command -v ss >/dev/null 2>&1; then
    ss -tlnp 2>/dev/null | awk 'NR==1 || /LISTEN/' | sed 's/^/    /' | head -15
else
    aviso "  (ss indisponivel; pulando)"
fi
echo

# -----------------------------------------------------------------------------
# 1. A porta esta livre?
#
# Esta checagem vem antes de tudo. Instalar Java e criar servico numa maquina
# onde a porta esta ocupada so produz um servico que reinicia em loop.
# -----------------------------------------------------------------------------
if ss -tln 2>/dev/null | grep -qE "[:.]${PORTA}[[:space:]]"; then
    erro "A porta ${PORTA} ja esta em uso nesta VM."
    echo
    erro "Quem esta usando:"
    ss -tlnp 2>/dev/null | grep -E "[:.]${PORTA}[[:space:]]" | sed 's/^/    /' >&2
    echo
    erro "Escolha outra porta livre e ajuste nos TRES lugares:"
    erro "  1. PORTA, no topo deste script"
    erro "  2. server.port, em src/main/resources/application-prod.properties"
    erro "  3. PORTA_APP, em .github/workflows/ci.yml"
    erro "Os tres precisam bater, senao o deploy publica e a verificacao falha."
    exit 1
fi
info "Porta ${PORTA} livre."

# -----------------------------------------------------------------------------
# 2. Java
#
# Se ja houver Java instalado, NAO trocamos a versao: outra aplicacao pode
# depender dela. So instalamos quando nao ha Java nenhum, ou quando o
# instalado e antigo demais para rodar a aplicacao.
# -----------------------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
    JAVA_ATUAL=$(java -version 2>&1 | head -1)

    # Extrai o numero maior da versao. Tres formatos precisam funcionar:
    #   "21.0.12"  -> 21   (moderno)
    #   "25"       -> 25   (sem minor, acontece em release nova)
    #   "1.8.0_382" -> 8   (formato antigo: o 1 e prefixo, o real vem depois)
    VERSAO_DETECTADA=$(java -version 2>&1 | head -1 \
        | grep -oE 'version "[0-9]+(\.[0-9]+)?' \
        | grep -oE '[0-9]+(\.[0-9]+)?$' \
        | awk -F. '{ if ($1 == 1 && NF > 1) print $2; else print $1 }')

    # Se a extracao falhar por formato inesperado, tratamos como 0 e o script
    # instala o Java 21 ao lado. Melhor instalar de novo que assumir errado.
    if ! [[ "${VERSAO_DETECTADA:-}" =~ ^[0-9]+$ ]]; then
        aviso "Nao consegui interpretar a versao do Java: ${JAVA_ATUAL}"
        VERSAO_DETECTADA=0
    fi

    if [ "$VERSAO_DETECTADA" -ge "$VERSAO_JAVA" ]; then
        info "Java ja instalado e suficiente: ${JAVA_ATUAL}"
    else
        aviso "Java instalado e antigo: ${JAVA_ATUAL}"
        aviso "A aplicacao precisa da versao ${VERSAO_JAVA} ou superior."
        aviso "Instalando o ${VERSAO_JAVA} AO LADO, sem remover o atual."
        apt-get update -qq
        apt-get install -y -qq "openjdk-${VERSAO_JAVA}-jre-headless"
        aviso "Duas versoes convivem. O servico aponta para um caminho fixo,"
        aviso "entao a aplicacao antiga continua usando a versao dela."
    fi
else
    info "Instalando Java ${VERSAO_JAVA}..."
    apt-get update -qq
    # headless: sem bibliotecas graficas, que um servidor nao usa. Economiza
    # cerca de 100 MB e reduz a superficie de ataque.
    apt-get install -y -qq "openjdk-${VERSAO_JAVA}-jre-headless"
    info "Java instalado: $(java -version 2>&1 | head -1)"
fi

# Caminho absoluto do Java 21, para o servico nao depender do que estiver no
# PATH nem de uma eventual troca do java padrao da maquina.
JAVA_BIN=$(find /usr/lib/jvm -maxdepth 2 -type f -path "*${VERSAO_JAVA}*/bin/java" 2>/dev/null | head -1)
if [ -z "$JAVA_BIN" ]; then
    JAVA_BIN=$(command -v java)
    aviso "Nao localizei o binario especifico do Java ${VERSAO_JAVA}."
    aviso "Usando o java do PATH: ${JAVA_BIN}"
fi
info "O servico vai usar: ${JAVA_BIN}"

# -----------------------------------------------------------------------------
# 3. Usuario da aplicacao
#
# A aplicacao nao roda como root nem com o seu usuario. Se alguem explorar uma
# falha nela, fica limitado ao que este usuario pode fazer, que e quase nada:
# sem shell de login, sem home, dono apenas do proprio diretorio. Isso importa
# mais ainda aqui, porque a outra aplicacao da VM esta no mesmo disco.
# -----------------------------------------------------------------------------
if id "$USUARIO_APP" &>/dev/null; then
    info "Usuario '${USUARIO_APP}' ja existe."
else
    info "Criando usuario '${USUARIO_APP}'..."
    useradd --system --no-create-home --shell /usr/sbin/nologin "$USUARIO_APP"
fi

# -----------------------------------------------------------------------------
# 4. Diretorio
# -----------------------------------------------------------------------------
info "Preparando ${DIRETORIO}..."
mkdir -p "$DIRETORIO"
chown "$USUARIO_APP:$USUARIO_APP" "$DIRETORIO"
chmod 755 "$DIRETORIO"

# -----------------------------------------------------------------------------
# 5. Script de publicacao
#
# Fica na VM em vez de no workflow porque o deploy precisa de sudo para
# reiniciar o servico. Concentrando aqui, a regra do sudoers libera um script
# especifico em vez de dar sudo amplo ao usuario de deploy.
# -----------------------------------------------------------------------------
info "Instalando o script de publicacao..."
cat > "${DIRETORIO}/publicar.sh" <<'PUBLICAR'
#!/usr/bin/env bash
#
# Troca o jar em uso pelo recem-enviado e reinicia o servico.
# Chamado pelo workflow via ssh, com sudo.
#
# Mexe SOMENTE no servico rota-vital. Nenhuma outra aplicacao da VM e tocada.

set -euo pipefail

DIRETORIO=/opt/rota-vital
NOVO=/tmp/rota-vital-novo.jar
ATUAL="${DIRETORIO}/rota-vital.jar"
ANTERIOR="${DIRETORIO}/rota-vital-anterior.jar"

if [ ! -f "$NOVO" ]; then
    echo "Erro: ${NOVO} nao encontrado. O envio falhou?" >&2
    exit 1
fi

# Guarda a versao atual antes de sobrescrever. Se o deploy der errado, da
# para voltar com:
#   sudo cp /opt/rota-vital/rota-vital-anterior.jar /opt/rota-vital/rota-vital.jar
#   sudo systemctl restart rota-vital
if [ -f "$ATUAL" ]; then
    cp "$ATUAL" "$ANTERIOR"
fi

mv "$NOVO" "$ATUAL"
chown rotavital:rotavital "$ATUAL"
chmod 644 "$ATUAL"

echo "Reiniciando o servico rota-vital..."
systemctl restart rota-vital

# Da tempo do systemd registrar falha imediata (porta ocupada, jar
# corrompido) antes de reportar sucesso.
sleep 3

if systemctl is-active --quiet rota-vital; then
    echo "Servico ativo."
else
    echo "Erro: o servico nao subiu. Ultimas linhas do log:" >&2
    journalctl -u rota-vital -n 30 --no-pager >&2
    exit 1
fi
PUBLICAR

chmod 755 "${DIRETORIO}/publicar.sh"

# -----------------------------------------------------------------------------
# 6. Servico systemd
#
# systemd cuida de: subir a aplicacao no boot, reiniciar se ela cair e
# concentrar o log em um lugar (journalctl).
# -----------------------------------------------------------------------------
info "Instalando o servico systemd..."
cat > /etc/systemd/system/rota-vital.service <<SERVICO
[Unit]
Description=Rota Vital - plataforma de distribuicao de hemocomponentes
# So tenta subir depois que a rede esta pronta, senao a aplicacao pode
# falhar ao abrir a porta.
After=network.target

[Service]
Type=simple
User=${USUARIO_APP}
Group=${USUARIO_APP}
WorkingDirectory=${DIRETORIO}

# O perfil prod desliga o console do H2, restringe o actuator e usa a porta
# ${PORTA}. Ver src/main/resources/application-prod.properties.
Environment="SPRING_PROFILES_ACTIVE=prod"

# Limites de memoria, calculados para esta VM: 978 MB no total, ~577 MB
# realmente disponiveis (MemAvailable) com o Flux rodando.
#
# -Xmx256m e o heap. A conta que importa: a JVM consome MAIS que o heap.
# Metaspace, pilhas de thread e buffers ficam fora dele e somam 100-200 MB
# numa aplicacao Spring Boot. Com heap de 384m o total passaria de 500 MB e
# sobrariam ~40 MB para o resto do sistema, o que e pouco demais: o kernel
# comecaria a matar processo por falta de memoria, e o morto poderia ser o
# da OUTRA aplicacao.
#
# Com 256m, o consumo MEDIDO do processo foi de 331 MB de memoria residente,
# estavel apos 100 requisicoes aos cinco recursos da API. Sobram entao cerca
# de 246 MB de folga nesta VM.
#
# A aplicacao carrega ~100 bolsas e algumas dezenas de requisicoes: o que
# consome memoria aqui e o proprio Spring, nao os dados.
#
# -XX:MaxMetaspaceSize teto para as definicoes de classe. Sem ele o
# metaspace cresce sem limite ate o processo ser morto, e Spring carrega
# muita classe.
#
# -XX:+UseSerialGC coletor de uma thread so. Numa VM com 2 vCPU
# compartilhadas o coletor paralelo gasta mais em coordenacao do que ganha
# em paralelismo, alem de reservar mais memoria.
#
# Se aparecer OutOfMemoryError no log, aumente o -Xmx em passos de 64m e
# acompanhe o MemAvailable com: head -3 /proc/meminfo
ExecStart=${JAVA_BIN} -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -jar ${DIRETORIO}/rota-vital.jar

# Reinicia se cair, esperando 10s entre tentativas para nao entrar em
# ciclo rapido de falha.
Restart=always
RestartSec=10

# Log vai para o journal do sistema: sudo journalctl -u rota-vital -f
StandardOutput=journal
StandardError=journal
SyslogIdentifier=rota-vital

# --- Restricoes de seguranca ---
# Camada extra caso alguem consiga executar codigo dentro da aplicacao.
# Numa VM compartilhada isso protege tambem a outra aplicacao.
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=${DIRETORIO}

[Install]
WantedBy=multi-user.target
SERVICO

systemctl daemon-reload
systemctl enable rota-vital

# -----------------------------------------------------------------------------
# 7. Permissao de sudo para o script de publicacao
#
# O usuario que o GitHub Actions usa precisa reiniciar o servico, o que exige
# root. Em vez de dar sudo irrestrito, liberamos apenas este script.
# -----------------------------------------------------------------------------
USUARIO_DEPLOY="${SUDO_USER:-}"

if [ -n "$USUARIO_DEPLOY" ] && [ "$USUARIO_DEPLOY" != "root" ]; then
    info "Liberando publicar.sh sem senha para '${USUARIO_DEPLOY}'..."
    echo "${USUARIO_DEPLOY} ALL=(root) NOPASSWD: ${DIRETORIO}/publicar.sh" \
        > /etc/sudoers.d/rota-vital-deploy
    chmod 440 /etc/sudoers.d/rota-vital-deploy

    # visudo -c recusa arquivo malformado. Sem esta checagem, um erro de
    # sintaxe poderia travar o sudo da maquina inteira, o que numa VM com
    # outra aplicacao em producao seria bem ruim.
    if ! visudo -c -q; then
        erro "Regra de sudo invalida. Removendo."
        rm -f /etc/sudoers.d/rota-vital-deploy
        exit 1
    fi
else
    aviso "Nao identifiquei o usuario de deploy (rodou como root direto?)."
    aviso "Crie a regra na mao, trocando SEU_USUARIO:"
    aviso "  echo 'SEU_USUARIO ALL=(root) NOPASSWD: ${DIRETORIO}/publicar.sh' | sudo tee /etc/sudoers.d/rota-vital-deploy"
    aviso "  sudo chmod 440 /etc/sudoers.d/rota-vital-deploy"
fi

# -----------------------------------------------------------------------------
info "Provisionamento concluido. Nenhum outro servico foi alterado."
echo
echo "Falta:"
echo "  1. Liberar a porta ${PORTA} no firewall do GCP (ver docs/deploy.md)"
echo "  2. Cadastrar os segredos no GitHub: VM_HOST, VM_USUARIO, VM_CHAVE_SSH"
echo "  3. Dar push na main para o pipeline publicar"
echo
echo "Comandos uteis:"
echo "  sudo systemctl status rota-vital        estado do servico"
echo "  sudo journalctl -u rota-vital -f        log ao vivo"
echo "  curl localhost:${PORTA}/actuator/health   testar de dentro da VM"
echo
echo "Para remover tudo o que este script criou:"
echo "  sudo systemctl disable --now rota-vital"
echo "  sudo rm /etc/systemd/system/rota-vital.service /etc/sudoers.d/rota-vital-deploy"
echo "  sudo rm -rf ${DIRETORIO} && sudo userdel ${USUARIO_APP}"
echo "  sudo systemctl daemon-reload"
