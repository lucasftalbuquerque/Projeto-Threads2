#!/usr/bin/env bash
#
# Prepara a VM Ubuntu para receber a aplicacao. Roda uma vez, na mao.
#
#   curl -fsSL https://raw.githubusercontent.com/rsc3-pixel/Projeto3-2026.2/main/infra/provisionar.sh | sudo bash
#
# ou, com o repositorio clonado na VM:
#
#   sudo bash infra/provisionar.sh
#
# O que faz:
#   1. instala o Java 21, se ainda nao houver
#   2. cria um usuario sem privilegios para rodar a aplicacao
#   3. instala o script de publicacao e o servico systemd
#
# E idempotente: rodar duas vezes nao quebra nada.

set -euo pipefail

DIRETORIO=/opt/rota-vital
USUARIO_APP=rotavital
VERSAO_JAVA=21

# Cores so quando a saida e um terminal. Em log de CI, texto puro.
if [ -t 1 ]; then
    VERDE='\033[0;32m'; AMARELO='\033[0;33m'; VERMELHO='\033[0;31m'; FIM='\033[0m'
else
    VERDE=''; AMARELO=''; VERMELHO=''; FIM=''
fi

info()   { echo -e "${VERDE}==>${FIM} $1"; }
aviso()  { echo -e "${AMARELO}==>${FIM} $1"; }
erro()   { echo -e "${VERMELHO}==>${FIM} $1" >&2; }

if [ "$EUID" -ne 0 ]; then
    erro "Rode com sudo: sudo bash infra/provisionar.sh"
    exit 1
fi

# -----------------------------------------------------------------------------
# 1. Java
# -----------------------------------------------------------------------------
if java -version 2>&1 | grep -q "version \"${VERSAO_JAVA}"; then
    info "Java ${VERSAO_JAVA} ja instalado."
else
    info "Instalando Java ${VERSAO_JAVA}..."
    apt-get update -qq
    # headless: sem bibliotecas graficas, que um servidor nao usa. Economiza
    # cerca de 100 MB e reduz a superficie de ataque.
    apt-get install -y -qq "openjdk-${VERSAO_JAVA}-jre-headless"
    info "Java instalado: $(java -version 2>&1 | head -1)"
fi

# -----------------------------------------------------------------------------
# 2. Usuario da aplicacao
#
# A aplicacao nao roda como root. Se alguem explorar uma falha nela, fica
# limitado ao que este usuario pode fazer, que e quase nada: sem shell de
# login, sem diretorio home, dono apenas do proprio diretorio.
# -----------------------------------------------------------------------------
if id "$USUARIO_APP" &>/dev/null; then
    info "Usuario '${USUARIO_APP}' ja existe."
else
    info "Criando usuario '${USUARIO_APP}'..."
    useradd --system --no-create-home --shell /usr/sbin/nologin "$USUARIO_APP"
fi

# -----------------------------------------------------------------------------
# 3. Diretorio
# -----------------------------------------------------------------------------
info "Preparando ${DIRETORIO}..."
mkdir -p "$DIRETORIO"
chown "$USUARIO_APP:$USUARIO_APP" "$DIRETORIO"
chmod 755 "$DIRETORIO"

# -----------------------------------------------------------------------------
# 4. Script de publicacao
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

echo "Reiniciando o servico..."
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
# 5. Servico systemd
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

# O perfil prod desliga o console do H2 e restringe o actuator.
# Ver src/main/resources/application-prod.properties.
Environment="SPRING_PROFILES_ACTIVE=prod"

# -Xmx512m limita a memoria. A VM free tier do GCP tem 1 GB; sem limite, a
# JVM tenta usar 1/4 da RAM e o sistema pode ficar sem folega.
ExecStart=/usr/bin/java -Xmx512m -jar ${DIRETORIO}/rota-vital.jar

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
# 6. Permissao de sudo para o script de publicacao
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
    # sintaxe poderia travar o sudo da maquina inteira.
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
info "Provisionamento concluido."
echo
echo "Falta:"
echo "  1. Liberar a porta 8080 no firewall do GCP (ver docs/deploy.md)"
echo "  2. Cadastrar os segredos no GitHub: VM_HOST, VM_USUARIO, VM_CHAVE_SSH"
echo "  3. Dar push na main para o pipeline publicar"
echo
echo "Comandos uteis:"
echo "  sudo systemctl status rota-vital     estado do servico"
echo "  sudo journalctl -u rota-vital -f     log ao vivo"
echo "  curl localhost:8080/actuator/health  testar de dentro da VM"
