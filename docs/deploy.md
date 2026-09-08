# Deploy do Rota Vital

Como a aplicação sai do GitHub e chega no ar. Documento da PI3-21.

---

## O pipeline em uma imagem

```
   push na main
        │
        ▼
┌───────────────┐
│  1. Build     │  o código compila?
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│  2. Testes    │  o comportamento está correto?
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│ 3. Empacotar  │  gera o rota-vital.jar
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│  4. Deploy    │  só na main, só com os segredos configurados
└───────┬───────┘
        │
        ├─ envia o jar por scp
        ├─ chama publicar.sh (troca o jar e reinicia)
        ├─ confirma /actuator/health = UP
        └─ confirma /h2-console inacessível
```

Cada etapa só começa se a anterior passou. Falha em qualquer ponto impede o
deploy, que é o critério de aceite da story.

**Por que quatro jobs e não um só?** Três motivos:

1. **Erro aparece antes.** Erro de compilação quebra em segundos, sem esperar
   a suite inteira de testes.
2. **A aba Actions fica legível.** Você vê onde quebrou sem abrir o log.
3. **O jar publicado é o mesmo que passou nos testes.** A etapa de deploy baixa
   o artefato gerado no empacotamento, em vez de compilar de novo.

---

## Parte 1: preparar a VM

Roda uma vez só.

### 1.1 Criar a VM (se ainda não existir)

No console do Google Cloud, **Compute Engine > Instâncias de VM > Criar**:

| Campo | Valor |
|---|---|
| Nome | `rota-vital` |
| Região | `southamerica-east1` (São Paulo) |
| Tipo de máquina | `e2-micro` (elegível ao free tier) |
| Disco de inicialização | Ubuntu 24.04 LTS, 20 GB |
| Firewall | marque **Permitir tráfego HTTP** |

> **Sobre o e2-micro:** 1 GB de RAM. É apertado para a JVM, e por isso o
> serviço systemd limita o heap em 512 MB (`-Xmx512m`). Se a aplicação for
> morta por falta de memória, o log do sistema mostra `Killed` ou `OOM`.

### 1.2 Liberar a porta 8080

A aplicação escuta na 8080, e o GCP bloqueia tudo que não for liberado
explicitamente.

**Console:** VPC network > Firewall > Criar regra de firewall

| Campo | Valor |
|---|---|
| Nome | `permitir-8080` |
| Direção | Entrada |
| Destinos | Todas as instâncias na rede |
| Intervalos de IP de origem | `0.0.0.0/0` |
| Protocolos e portas | TCP, `8080` |

**Ou pelo `gcloud`:**

```bash
gcloud compute firewall-rules create permitir-8080 \
  --allow=tcp:8080 \
  --source-ranges=0.0.0.0/0 \
  --description="Rota Vital - API"
```

> `0.0.0.0/0` significa "de qualquer lugar da internet". É o que a demonstração
> exige. Em sistema real, você restringiria a origem e colocaria o Nginx na
> frente, como prevê o [diagrama de arquitetura](../diagrama_arquitetura_redes.png).

### 1.3 Rodar o provisionamento

Conecte na VM por SSH e rode:

```bash
sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/rsc3-pixel/Projeto3-2026.2/main/infra/provisionar.sh)"
```

O script instala o Java 21, cria o usuário da aplicação, instala o serviço
systemd e libera a regra de sudo do deploy. Rodar duas vezes não causa
problema.

Confira ao final:

```bash
java -version                          # deve mostrar 21
sudo systemctl status rota-vital       # inativo, ainda sem jar
```

---

## Parte 2: conectar o GitHub à VM

O workflow precisa entrar na VM por SSH. Para isso, gera-se um par de chaves:
a pública fica na VM, a privada vira segredo no GitHub.

### 2.1 Gerar o par de chaves

**Na sua máquina**, não na VM:

```bash
ssh-keygen -t ed25519 -C "github-actions-rota-vital" -f ~/.ssh/rota_vital_deploy -N ""
```

Gera dois arquivos:

| Arquivo | Vai para |
|---|---|
| `rota_vital_deploy.pub` | a VM (pode ser vista por qualquer um) |
| `rota_vital_deploy` | GitHub Secrets (**nunca** commitar) |

> `-N ""` cria a chave sem senha. Uma automação não tem como digitar senha.
> Por isso a chave privada precisa ficar em segredo: quem a tiver, entra na VM.

### 2.2 Instalar a chave pública na VM

```bash
ssh-copy-id -i ~/.ssh/rota_vital_deploy.pub SEU_USUARIO@IP_DA_VM
```

Ou, manualmente, dentro da VM:

```bash
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "CONTEUDO_DO_ARQUIVO_.pub" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Teste antes de seguir:

```bash
ssh -i ~/.ssh/rota_vital_deploy SEU_USUARIO@IP_DA_VM "echo funcionou"
```

Se não imprimir `funcionou`, o deploy também não vai funcionar.

### 2.3 Cadastrar os segredos

No GitHub: **Settings > Secrets and variables > Actions > New repository secret**

| Nome | Valor |
|---|---|
| `VM_HOST` | o IP externo da VM (ex.: `34.95.120.44`) |
| `VM_USUARIO` | seu usuário de SSH na VM |
| `VM_CHAVE_SSH` | o conteúdo **inteiro** de `~/.ssh/rota_vital_deploy` |

Para copiar a chave privada:

```bash
# Windows (Git Bash)
cat ~/.ssh/rota_vital_deploy | clip

# Linux / macOS
cat ~/.ssh/rota_vital_deploy
```

> Cole **tudo**, incluindo as linhas `-----BEGIN OPENSSH PRIVATE KEY-----` e
> `-----END OPENSSH PRIVATE KEY-----`, e sem espaço sobrando no fim.

**O IP externo da VM muda a cada reinício** se for efêmero. Para fixar:
VPC network > Endereços IP > reserve o IP da instância como estático.

---

## Parte 3: publicar

Com tudo configurado, o deploy é automático: qualquer push na `main` dispara
o pipeline.

Para disparar sem commit novo: aba **Actions** > workflow **CI/CD** >
**Run workflow**.

### Confirmar que funcionou

```bash
curl http://IP_DA_VM:8080/actuator/health
# {"status":"UP"}

curl http://IP_DA_VM:8080/api/v1/hemocentros
# a lista de hemocentros da carga inicial

curl -o /dev/null -w "%{http_code}\n" http://IP_DA_VM:8080/h2-console
# 404 — precisa estar inacessível
```

O próprio workflow já faz as três verificações. Se alguma falhar, o job de
deploy fica vermelho.

---

## Segurança: o que foi decidido e por quê

### O console do H2 desligado em produção

É o critério de aceite mais importante da story, e vale entender o motivo.

O H2 console é uma página web com um terminal SQL. Em desenvolvimento, é
prático. Exposto na internet, qualquer pessoa que acesse
`http://seu-ip:8080/h2-console` pode ler, alterar e apagar qualquer dado,
**sem senha** — porque o H2 em memória sobe com usuário `sa` e senha vazia.

Onde está desligado: [`application-prod.properties`](../src/main/resources/application-prod.properties).

```properties
spring.h2.console.enabled=false
```

Três camadas garantem que isso não se perca:

1. O arquivo de perfil desliga
2. `PerfilProducaoTest` quebra o build se alguém religar
3. O workflow confere na URL pública depois do deploy

Uma linha num arquivo de configuração é fácil de reverter sem querer numa
sessão de depuração. Por isso o teste e a verificação pós-deploy.

### A aplicação não roda como root

O serviço roda com o usuário `rotavital`, criado sem shell de login e sem
diretório home. Se alguém explorar uma falha na aplicação, fica limitado ao
que esse usuário pode fazer, que é quase nada.

O systemd acrescenta restrições: `ProtectSystem=strict` (sistema de arquivos
somente leitura, exceto `/opt/rota-vital`), `PrivateTmp=true` (`/tmp` isolado)
e `NoNewPrivileges=true` (não consegue escalar privilégio).

### Sudo restrito a um script

O usuário de deploy precisa reiniciar o serviço, o que exige root. Em vez de
`NOPASSWD: ALL`, a regra libera apenas `/opt/rota-vital/publicar.sh`:

```
usuario ALL=(root) NOPASSWD: /opt/rota-vital/publicar.sh
```

Se a chave SSH vazar, o atacante consegue publicar um jar, o que já é ruim,
mas não vira root da máquina de imediato.

### Nenhum segredo no repositório

IP, usuário e chave privada ficam em GitHub Secrets. O repositório não tem
nenhum deles, e `PerfilProducaoTest.semSegredoVersionado` recusa qualquer
propriedade de senha ou token que não venha de variável de ambiente.

---

## Quando algo dá errado

### O job de deploy é pulado com aviso amarelo

Falta cadastrar `VM_HOST`, `VM_USUARIO` ou `VM_CHAVE_SSH`. É o comportamento
esperado enquanto a VM não está configurada — o pipeline segue verde de
propósito, para o time não perder a referência de "verde = está tudo bem".

### `Permission denied (publickey)`

A chave pública não está na VM, ou o `VM_USUARIO` está errado. Teste na mão:

```bash
ssh -i ~/.ssh/rota_vital_deploy SEU_USUARIO@IP_DA_VM
```

### `Host key verification failed`

O IP da VM mudou. Se for IP efêmero, reserve um estático e atualize o
`VM_HOST`.

### A aplicação não responde `UP` em 60 segundos

Entre na VM e leia o log:

```bash
sudo systemctl status rota-vital
sudo journalctl -u rota-vital -n 50 --no-pager
```

Causas comuns:

| Sintoma no log | Causa |
|---|---|
| `Port 8080 was already in use` | processo antigo não morreu; `sudo systemctl restart rota-vital` |
| `Killed` ou `OutOfMemoryError` | RAM insuficiente; reduza `-Xmx` no serviço ou use uma VM maior |
| `Unable to access jarfile` | o envio do jar falhou; confira `/opt/rota-vital/rota-vital.jar` |

### Responde de dentro da VM mas não de fora

É firewall. Confira a regra da porta 8080:

```bash
gcloud compute firewall-rules list --filter="name~8080"
```

### Voltar para a versão anterior

O `publicar.sh` guarda o jar substituído:

```bash
sudo cp /opt/rota-vital/rota-vital-anterior.jar /opt/rota-vital/rota-vital.jar
sudo systemctl restart rota-vital
```

---

## Comandos de referência

**Na VM:**

```bash
sudo systemctl status rota-vital      # estado
sudo systemctl restart rota-vital     # reiniciar
sudo journalctl -u rota-vital -f      # log ao vivo
sudo journalctl -u rota-vital -n 100  # últimas 100 linhas
curl localhost:8080/actuator/health   # testar localmente
```

**Arquivos:**

| Caminho | O que é |
|---|---|
| `/opt/rota-vital/rota-vital.jar` | a aplicação em uso |
| `/opt/rota-vital/rota-vital-anterior.jar` | a versão anterior |
| `/opt/rota-vital/publicar.sh` | troca o jar e reinicia |
| `/etc/systemd/system/rota-vital.service` | definição do serviço |
| `/etc/sudoers.d/rota-vital-deploy` | permissão do deploy |

---

## O que ficou fora

| Item | Onde entra |
|---|---|
| Nginx como proxy reverso e HTTPS | previsto no [PI3-16](../diagrama_arquitetura_redes.png), sem story ainda |
| PostgreSQL no lugar do H2 | Entrega 02 |
| Deploy sem interrupção (zero downtime) | exige duas instâncias e balanceador |
| Ambiente de homologação | uma VM só no free tier |

O deploy atual reinicia o serviço, então há alguns segundos de indisponibilidade
a cada publicação. Para o escopo do projeto, é aceitável.
