# Monitor de disponibilidade

Monólito modular Java/Spring Boot com PostgreSQL e Flyway. MON-02 entrega infraestrutura Docker, MON-03 protege o acesso administrativo, MON-04 administra monitores e MON-05 executa e registra uma tentativa HTTP por contrato Java interno. Agendamento, painel, processamento de incidentes e notificações seguem no [backlog](.specs/task/monitor-disponibilidade-tasks.md).

Build, testes e execução usam contêineres. O host precisa de Docker com daemon Linux e Docker Compose; não precisa de Maven Wrapper, Maven ou JDK. São suportados Docker Desktop com Linux containers e Docker Engine Linux com `host-gateway` (20.10+), Compose v2+ e BuildKit. A validação local foi realizada no Docker Desktop Windows; a execução remota da CI não foi observada.

Antes de iniciar, copie `.env.example` para `.env` e substitua os placeholders de ADMIN_USERNAME e ADMIN_PASSWORD por valores locais. `.env` está ignorado pelo Git e excluído do build. Os placeholders, valores vazios e ausência de qualquer variável administrativa impedem startup; não existe operador ou senha padrão.

Para construir e iniciar, na raiz do checkout com a configuração preenchida:

```sh
docker compose up --build
```

A aplicação fica em `http://localhost:8080`. A API de monitores está em `/api/admin/monitors`; consulte o [contrato completo](docs/monitor-api.md) para configuração, edição, pausa, retomada, paginação e erros. O painel será criado na MON-10. A prontidão pública está em `/actuator/health/readiness`: HTTP 200 com `{"status":"UP"}`; sem banco disponível, HTTP 503 e `DOWN`. O PostgreSQL não publica porta no host.

Os defaults de banco servem para desenvolvimento local. As credenciais administrativas são obrigatórias:

| Variável | Default | Uso |
| --- | --- | --- |
| APP_PORT | 8080 | Porta da aplicação em 127.0.0.1 |
| ADMIN_USERNAME | Sem default | Operador único; 1–128 caracteres, não branco, sem colon ou caracteres de controle |
| ADMIN_PASSWORD | Sem default | Senha não branca de até 72 bytes UTF-8, limite do BCrypt |
| DB_NAME | monitor | Nome do banco criado pelo PostgreSQL |
| DB_USER | monitor | Usuário local do PostgreSQL e da aplicação |
| DB_PASSWORD | local-development-only | Valor descartável de desenvolvimento; substituir para outros ambientes |
| JAVA_TOOL_OPTIONS | -XX:MaxRAMPercentage=75.0 | Opções da JVM runtime |
| TESTCONTAINERS_HOST_OVERRIDE | host.docker.internal | Host das portas publicadas pelos contêineres de teste |

Compose deriva `DB_URL=jdbc:postgresql://db:5432/${DB_NAME}` para a aplicação. A configuração Spring recebe DB_URL, DB_USER e DB_PASSWORD pelo ambiente. Credenciais reais não devem ser versionadas. As variáveis de inicialização do PostgreSQL criam usuário/banco apenas quando o volume está vazio; alterar `.env` não altera credenciais de um banco existente.

Exemplo de configuração administrativa, com placeholders que devem ser substituídos somente no `.env` local:

```dotenv
ADMIN_USERNAME='<choose-admin-username>'
ADMIN_PASSWORD='<choose-admin-password>'
```

Use aspas simples em `.env` para preservar valores com `$` ou `#`. Não passe credenciais em URLs, `JAVA_TOOL_OPTIONS` ou comandos de diagnóstico. `docker compose config` sem `--quiet` e inspeções de ambiente Docker podem revelar valores locais; use `docker compose config --quiet` para validar a configuração. A aplicação não registra usuário, senha ou Authorization em nível padrão. Erros de configuração e respostas 401/403 omitem os valores; a senha é codificada com BCrypt somente em memória, sem tabela de usuários. Mantenha logs de segurança no nível padrão e não habilite rastreamento de headers com credenciais reais.

A porta permanece em 127.0.0.1. HTTP Basic transmite a credencial em cada requisição; use HTTPS caso exponha o serviço fora do host.

Para trocar a credencial, edite ADMIN_PASSWORD (e ADMIN_USERNAME, se necessário) no `.env`, depois recrie somente a aplicação:

```sh
docker compose up -d --force-recreate app
```

`docker compose restart app` não recarrega variáveis. Após a recriação, a senha anterior recebe 401; a nova permite acesso. Cookies antigos não autenticam. A sessão CSRF está em memória e é descartada na recriação: obtenha um novo token. A troca não altera o volume PostgreSQL.

| Rotas | Acesso |
| --- | --- |
| GET/HEAD /actuator/health, /actuator/health/liveness, /actuator/health/readiness | Público, somente status, sem detalhes ou criação de sessão |
| GET /actuator/info | HTTP Basic do operador |
| GET /api/admin/csrf | HTTP Basic; entrega token/headerName/parameterName para CSRF |
| Todas as demais, incluindo /, /api/**, /admin/**, assets e outros actuators | HTTP Basic por padrão; rotas não implementadas retornam 404 após autenticação |
| POST/PUT/PATCH/DELETE administrativos | Basic + cookie de sessão e token CSRF válido |

Sem Basic válido a resposta é 401, corpo vazio e `WWW-Authenticate: Basic realm="monitor-admin"`, inclusive em escritas sem token. Com Basic válido e CSRF ausente/inválido a resposta é 403 e corpo vazio. O painel ainda será criado na MON-10; sua fronteira já está protegida. Os endpoints `/admin/probe` e `/api/admin/probe` existem somente nos testes.

O futuro painel e clientes API devem obter `GET /api/admin/csrf` com Basic, preservar o cookie JSESSIONID e enviar o campo `token` no header indicado por `headerName` (`X-CSRF-TOKEN`) em cada escrita. Formulários podem enviar o parâmetro `_csrf`. A sessão expira após 30 minutos sem uso; renove o token quando a sessão expirar. GET/HEAD não alteram domínio. Nenhuma API de escrita é isenta de CSRF.

Exemplo JavaScript para cliente na mesma origem, depois da autenticação Basic pelo navegador:

```javascript
const response = await fetch('/api/admin/csrf', { credentials: 'same-origin' });
if (!response.ok) throw new Error('Autenticação administrativa necessária');
const csrf = await response.json();
// Na futura ação administrativa, inclua este header e preserve o cookie:
const headers = { [csrf.headerName]: csrf.token };
```

O servidor mantém o token esperado na sessão e entrega uma versão mascarada. O cookie é HttpOnly e SameSite=Lax; não contém a credencial e não substitui Basic. Outra origem não recebe permissão CORS para ler o token; formulário forjado sem token é rejeitado. O comportamento segue os mecanismos nativos de [CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html) e [Basic](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/basic.html) do Spring Security.

O healthcheck de `db` e `depends_on: service_healthy` do Compose permanecem. O serviço app não possui healthcheck de contêiner; o smoke verifica sua prontidão por HTTP. `/actuator/health/readiness` permanece público para não inserir credenciais nos probes: 200/UP com banco e 503/DOWN sem banco. Isso não torna API, painel ou outros actuators públicos. Configuração administrativa inválida impede que app fique pronto, mesmo com db saudável.

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

Esse comando executa `mvn clean verify` dentro do contêiner e propaga código diferente de zero em falhas. Surefire executa `*Test` unitários; Failsafe executa `*IT` com PostgreSQL real; Checkstyle verifica produção e testes. A suíte tem 301 casos: 124 unitários e 177 integrações, incluindo os 264 anteriores e 37 de reserva, concorrência e runtime da MON-06. As credenciais de teste são efêmeras; `tests` e build runtime não exigem credenciais administrativas do operador ou `.env`.

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

O smoke operacional automatiza build, startup fechado sem cada variável administrativa, acesso 401/200, rotação de senha inclusive com cookie anterior, ausência de credenciais nos logs, migração, comparação integral do histórico, inserção de sentinela e preservação após restart e down/up:

```powershell
# Windows PowerShell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1

# PowerShell 7, inclusive Linux
pwsh -File scripts/validate-compose.ps1
```

Esse roteiro opcional precisa de PowerShell, cria um projeto Compose com nome único e porta aleatória, gera credenciais descartáveis em memória, restaura o ambiente anterior, grava logs em `.validation` e remove somente seus próprios contêineres/volumes ao terminar. Não usa a credencial do operador. Os comandos normais de operação e testes acima precisam apenas de Docker. `-NoBuild` reutiliza a imagem já construída; `-ProjectDirectory <checkout>` valida outro diretório de código-fonte.

A [CI](.github/workflows/ci.yml) executa testes/qualidade no serviço tests, constrói runtime e roda o smoke. Uma falha impede sucesso; construir imagem não substitui a suíte. Surefire, Failsafe, Checkstyle e logs do smoke são anexados ao job mesmo em falha. O runner usa Docker e PowerShell; não instala Java ou Maven no host.

Limites dos módulos:

| Área | Responsabilidade | Dependências permitidas / situação |
| --- | --- | --- |
| Bootstrap (`br.com.monitor`) | Inicializar aplicação e infraestrutura | Spring Boot; implementado em MON-02 |
| Segurança (`br.com.monitor.security`) | Operador Basic, fronteira global, token CSRF e health público mínimo | Spring Security/Actuator; implementado em MON-03 |
| Administração (`br.com.monitor.administration`) | Configuração, ciclo de vida, API e persistência dos monitores | Modelo puro, JDBC e transações; implementado em MON-04 |
| Infraestrutura (`src/main/resources`) | Configuração JDBC, prontidão e migrações | Ambiente, PostgreSQL, Flyway; implementada em MON-02 |
| Monitoramento (`br.com.monitor.monitoring`) | Agendamento persistente, concorrência limitada, execução HTTP segura e resultados idempotentes | Monitor/configuração, Apache HttpClient, JDBC; MON-05/06 implementadas |
| Incidentes (futuro `br.com.monitor.incident`) | Confirmar transições e manter incidentes | Resultados publicados por monitoramento, sem executar HTTP; MON-07 |
| Notificações (futuro `br.com.monitor.notification`) | Intenções duráveis, envio e tentativas | Contratos de transição e adaptador SMTP; MON-08 |
| Consultas (futuro `br.com.monitor.reporting`) | Histórico, indicadores e painel | Leitura dos contratos dos módulos, sem alterar estado do executor; MON-09/MON-10 |

Nenhum módulo de produção depende de bibliotecas de teste; `ArchitectureTest` impõe esse limite. Os pacotes futuros serão criados com suas funcionalidades, sem classes vazias nesta base. As [specs da MON-02](.specs/features/mon-02-base-docker/spec.md), [decisão AD-004](.specs/STATE.md) e [tasks](.specs/features/mon-02-base-docker/tasks.md) mantêm a rastreabilidade MON-02-AC01 a MON-02-AC08.

O [contrato MON-03](.specs/features/mon-03-acesso-administrativo/spec.md), [tasks](.specs/features/mon-03-acesso-administrativo/tasks.md) e [evidências](.specs/features/mon-03-acesso-administrativo/evidence.md) relacionam autenticação, configuração e CSRF a MON-03-AC01 até MON-03-AC06. AD-005 estabelece a política para as próximas entregas.

A [validação independente MON-03](.specs/features/mon-03-acesso-administrativo/validation.md) concluiu PASS 6/6: 48 testes, build e smoke Docker aprovados, oito regressões injetadas detectadas e nenhuma sobrevivente. A validação é local; CI remota não executada.

A [spec MON-04](.specs/features/mon-04-ciclo-monitores/spec.md), [tasks](.specs/features/mon-04-ciclo-monitores/tasks.md) e [evidências](.specs/features/mon-04-ciclo-monitores/evidence.md) mantêm MON-04-AC01 a MON-04-AC07. V2 cria `monitor.monitors` sem alterar V1; configurações e estado sobrevivem a reinícios. `configurationVersion` identifica o destino/política; `executionGeneration` invalida trabalho após pausa, retomada ou edição. A integração com execução/cancelamento e o processamento de incidentes serão entregues em MON-05 a MON-07.

A [validação independente MON-04](.specs/features/mon-04-ciclo-monitores/validation.md) concluiu PASS nos sete critérios: 160 testes, zero falhas/erros/skips, qualidade, build e smoke Docker aprovados; seis mutações detectadas e nenhuma sobrevivente. A validação é local; CI remota não executada.

O [contrato HTTP MON-05](docs/http-checks.md) documenta GET, timeout total até cabeçalhos, classificações, proteção de rede/TLS e persistência por UUID. `MONITOR_DEMO_ORIGIN` é opcional e vazia por padrão; permite somente a origem interna explicitamente configurada, sem liberar a sub-rede. V3 cria `monitor.check_results`; resultados guardam versão/geração e não alteram estado ou incidentes. [Spec](.specs/features/mon-05-verificacao-http/spec.md), [tasks](.specs/features/mon-05-verificacao-http/tasks.md) e [evidências](.specs/features/mon-05-verificacao-http/evidence.md) mantêm MON-05-AC01 a MON-05-AC08.

A [validação independente MON-05](.specs/features/mon-05-verificacao-http/validation.md) concluiu PASS 8/8: 264 testes (110 unitários/154 integrações), zero falhas/erros/skips, qualidade, runtime e smoke Docker aprovados; seis regressões injetadas detectadas e nenhuma sobrevivente. Validação local; CI remota não executada.

O [agendamento MON-06](docs/scheduling.md) inicia automaticamente após startup, com `MONITOR_CHECK_CONCURRENCY=4` (1..16) e `MONITOR_SCHEDULER_POLL_MS=250` (positivo). V4 persiste uma reserva por monitor; excesso permanece vencido no banco, sem fila de workers. Pausa/edição solicitam cancelamento; restart reconcilia tentativas interrompidas sem replay e preserva o próximo horário. [Spec](.specs/features/mon-06-agendamento/spec.md), [tasks](.specs/features/mon-06-agendamento/tasks.md) e [evidências](.specs/features/mon-06-agendamento/evidence.md) mantêm MON-06-AC01 a MON-06-AC08. Aplicação das observações a estado e incidentes permanece MON-07.
