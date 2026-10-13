# Monitor de disponibilidade

Base de um monólito modular Java/Spring Boot com PostgreSQL e Flyway. MON-02 entrega infraestrutura executável; cadastro de monitores, autenticação, verificações HTTP, incidentes e notificações pertencem às próximas entregas do [backlog](.specs/task/monitor-disponibilidade-tasks.md).

Build, testes e execução usam contêineres. O host precisa de Docker com daemon Linux e Docker Compose; não precisa de Maven Wrapper, Maven ou JDK. São suportados Docker Desktop com Linux containers e Docker Engine Linux com `host-gateway` (20.10+), Compose v2+ e BuildKit. A validação local foi realizada no Docker Desktop Windows; a execução remota da CI não foi observada.

Para construir e iniciar, na raiz do checkout:

```sh
docker compose up --build
```

A aplicação fica em `http://localhost:8080`. A base ainda não tem painel; consulte `http://localhost:8080/actuator/health/readiness`. A resposta pronta é HTTP 200 com `{"status":"UP"}`. Sem banco disponível, a prontidão responde HTTP 503 e `DOWN`. O PostgreSQL não publica porta no host.

Os defaults servem para desenvolvimento local. Opcionalmente, copie `.env.example` para `.env` e ajuste os valores antes de iniciar. `.env` está ignorado pelo Git.

| Variável | Default | Uso |
| --- | --- | --- |
| APP_PORT | 8080 | Porta da aplicação em 127.0.0.1 |
| DB_NAME | monitor | Nome do banco criado pelo PostgreSQL |
| DB_USER | monitor | Usuário local do PostgreSQL e da aplicação |
| DB_PASSWORD | local-development-only | Valor descartável de desenvolvimento; substituir para outros ambientes |
| JAVA_TOOL_OPTIONS | -XX:MaxRAMPercentage=75.0 | Opções da JVM runtime |
| TESTCONTAINERS_HOST_OVERRIDE | host.docker.internal | Host das portas publicadas pelos contêineres de teste |

Compose deriva `DB_URL=jdbc:postgresql://db:5432/${DB_NAME}` para a aplicação. A configuração Spring recebe DB_URL, DB_USER e DB_PASSWORD pelo ambiente. Credenciais reais não devem ser versionadas. As variáveis de inicialização do PostgreSQL criam usuário/banco apenas quando o volume está vazio; alterar `.env` não altera credenciais de um banco existente.

Comandos de operação:

```sh
# Construir somente a imagem da aplicação, diretamente do código
docker compose build app

# Iniciar em segundo plano
docker compose up --build -d

# Consultar os serviços e acompanhar os logs
docker compose ps
docker compose logs -f app db

# Reiniciar os serviços
docker compose restart db app

# Parar e remover contêineres/rede, preservando os dados
docker compose down
```

O volume nomeado `postgres-data`, prefixado pelo projeto Compose, mantém os dados após restart e `down/up`. O histórico Flyway fica explicitamente em `public.flyway_schema_history`; V1 cria o schema `monitor`. Migrações aplicadas não devem ser editadas: adicione uma nova versão. `docker compose down --volumes` apaga os volumes desse projeto, incluindo o banco e o cache Maven. A parada habitual usa `down` sem essa opção.

Para executar testes e verificações de qualidade:

```sh
docker compose run --build --rm tests
```

Esse comando executa `mvn clean verify` dentro do contêiner e propaga código diferente de zero em falhas. Surefire executa `*Test` unitários; Failsafe executa `*IT` com PostgreSQL real; Checkstyle verifica produção e testes. A suíte atual tem um teste de arquitetura e cinco integrações: migração inicial/idempotência, checksum alterado, prontidão com/sem banco, credenciais inválidas e reinicialização com usuário/schema de mesmo nome.

Os resultados são copiados para o host mesmo se a suíte falhar:

| Relatório | Local |
| --- | --- |
| Unitários | target/surefire-reports/ |
| Integração | target/failsafe-reports/ |
| Qualidade | target/checkstyle-result.xml |
| JAR produzido pelos testes | target/monitor.jar |

`target` é saída gerada e é limpo pelo serviço de testes a cada execução. O build runtime recebe apenas `pom.xml`, `config` e `src`, nunca usa um JAR do host. O Dockerfile compila com Maven 3.9.11/Temurin JDK 21 e executa com Temurin 21.0.8_9 JRE Jammy, copiando somente o JAR para `/app`. Runtime usa UID 10001, sem Maven, javac ou socket Docker. PostgreSQL usa `17.6-alpine`; Spring Boot usa `3.5.16`; Testcontainers `1.21.4` é gerenciado pelo parent Spring Boot. As tags têm versões explícitas; os digests observados estão nas [evidências](.specs/features/mon-02-base-docker/evidence.md).

O serviço `tests` usa o perfil `tests` e só inicia quando solicitado. Ele monta `/var/run/docker.sock`, define `DOCKER_HOST=unix:///var/run/docker.sock` e `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. Isso permite criar PostgreSQLs descartáveis como contêineres irmãos no daemon local. O acesso ao socket concede controle sobre esse daemon e é restrito ao serviço de testes.

As portas aleatórias desses PostgreSQLs são acessadas por `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Compose adiciona `host.docker.internal:host-gateway` para resolver a rota também no Engine Linux. Essa rota deve alcançar as portas publicadas no host; firewall ou daemon remoto exigem outra configuração e ficam fora do ambiente suportado. Os contêineres de teste não usam bind mounts do código. Ryuk permanece ativo para limpar os recursos de Testcontainers. Os testes não usam nem alteram o banco persistente da aplicação; runtime e build normal não recebem acesso ao daemon.

O smoke operacional automatiza build, startup, migração, comparação integral do histórico, inserção de sentinela e preservação após restart e down/up:

```powershell
# Windows PowerShell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1

# PowerShell 7, inclusive Linux
pwsh -File scripts/validate-compose.ps1
```

Esse roteiro opcional precisa de PowerShell, cria um projeto Compose com nome único e porta aleatória, grava logs em `.validation` e remove somente seus próprios contêineres/volumes ao terminar. Os comandos normais de operação e testes acima precisam apenas de Docker. `-NoBuild` reutiliza a imagem já construída; `-ProjectDirectory <checkout>` valida outro diretório de código-fonte.

A [CI](.github/workflows/ci.yml) executa testes/qualidade no serviço tests, constrói runtime e roda o smoke. Uma falha impede sucesso; construir imagem não substitui a suíte. Surefire, Failsafe, Checkstyle e logs do smoke são anexados ao job mesmo em falha. O runner usa Docker e PowerShell; não instala Java ou Maven no host.

Limites dos módulos:

| Área | Responsabilidade | Dependências permitidas / situação |
| --- | --- | --- |
| Bootstrap (`br.com.monitor`) | Inicializar aplicação e infraestrutura | Spring Boot; implementado em MON-02 |
| Infraestrutura (`src/main/resources`) | Configuração JDBC, prontidão e migrações | Ambiente, PostgreSQL, Flyway; implementada em MON-02 |
| Monitoramento (futuro `br.com.monitor.monitoring`) | Configuração, ciclo de vida, execução e histórico | Contratos próprios e adaptadores de persistência/HTTP; MON-04 a MON-06 |
| Incidentes (futuro `br.com.monitor.incident`) | Confirmar transições e manter incidentes | Resultados publicados por monitoramento, sem executar HTTP; MON-07 |
| Notificações (futuro `br.com.monitor.notification`) | Intenções duráveis, envio e tentativas | Contratos de transição e adaptador SMTP; MON-08 |
| Consultas (futuro `br.com.monitor.reporting`) | Histórico, indicadores e painel | Leitura dos contratos dos módulos, sem alterar estado do executor; MON-09/MON-10 |

Nenhum módulo de produção depende de bibliotecas de teste; `ArchitectureTest` impõe esse limite. Os pacotes futuros serão criados com suas funcionalidades, sem classes vazias nesta base. As [specs da MON-02](.specs/features/mon-02-base-docker/spec.md), [decisão AD-004](.specs/STATE.md) e [tasks](.specs/features/mon-02-base-docker/tasks.md) mantêm a rastreabilidade MON-02-AC01 a MON-02-AC08.
