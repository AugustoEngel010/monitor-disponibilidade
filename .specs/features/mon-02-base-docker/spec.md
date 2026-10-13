# MON-02: base executável por Docker

**Data:** 2026-10-13. **Status:** Implementing. Pedido do usuário autoriza especificação e execução local.
**Origem:** [backlog](../../task/monitor-disponibilidade-tasks.md), MON-02-AC01 até MON-02-AC08.
**Convenção:** SHALL significa DEVE. IDs BASE-NN atendem ao formato da skill; os IDs de origem permanecem intactos.

## Problem Statement

O checkout contém somente documentação de produto. O operador precisa construir, testar e executar a base sem Maven, Wrapper ou JDK no host. Docker Compose deve construir diretamente do código e manter o PostgreSQL entre reinícios.

## Goals

- Iniciar a aplicação com `docker compose up --build`.
- Separar compilação, runtime e testes com acesso ao daemon.
- Verificar banco vazio, migração idempotente, prontidão e persistência.
- Usar os mesmos comandos em desenvolvimento e CI, preservando falhas e relatórios.

## Out of Scope

| Capacidade | Motivo |
| --- | --- |
| CRUD, autenticação, executor, incidentes e notificações | MON-03 a MON-11 |
| SMTP e API de demonstração | MON-08 / MON-12 |
| Push, publicação de imagem ou execução remota da CI | Sem autorização para operações remotas |
| Docker Windows containers, Podman e daemon remoto | Ambiente suportado: Docker Engine Linux ou Docker Desktop com Linux containers |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Build e execução | Docker exclusivamente; sem wrapper | Decisão expressa do usuário | Sim |
| Java e Spring | Java 21 LTS e Spring Boot 3.5.16; Maven 3.9.11 | Versões explícitas compatíveis; aplicação inexistente no checkout | Default técnico |
| Imagens | Maven 3.9.11 / Temurin 21; runtime Temurin 21.0.8_9 JRE Jammy; PostgreSQL 17.6 Alpine | Mesma versão principal Java; runtime sem ferramentas de build | Default técnico |
| Configuração local | Variáveis com defaults locais descartáveis e `.env` ignorado | Permitir o comando principal sem credenciais reais | Default técnico |
| Testcontainers | Socket Unix; `host.docker.internal` via host-gateway; sem bind mounts no PostgreSQL de teste | Funciona com contêineres irmãos e portas publicadas; aplicação não recebe socket | Default técnico |
| CI | GitHub Actions em Ubuntu; execução remota não observada | Existe origin GitHub; nenhum workflow anterior | Default técnico |
| Esquema inicial | Schema `monitor`, sem antecipar tabelas de domínio | Estabelece migração real, sem implementar MON-04 prematuramente | Default técnico |
| Automação operacional | PowerShell para smoke test em Windows e CI; comandos usuais só exigem Docker | Shell já disponível no ambiente; nenhum Java no host | Default técnico |

**Open questions:** none; decisões técnicas acima explicitadas. As premissas de negócio de MON-01 continuam fora desta entrega.

## User Stories

### P1: Construir e operar a base

**User Story:** Como operador, quero iniciar a base diretamente do checkout e preservar seu banco.

**Acceptance Criteria**:
1. WHEN o operador executa `docker compose up --build` num checkout sem `target`, THEN o sistema SHALL compilar a imagem a partir de `pom.xml` e `src`, iniciar PostgreSQL e responder HTTP 200 com `status=UP` em `/actuator/health/readiness` após o banco ficar saudável. BASE-01; MON-02-AC01.
2. WHEN uma base vazia recebe a primeira inicialização, THEN o sistema SHALL aplicar V1 criando o schema `monitor` e registrar uma única migração bem-sucedida; outra inicialização SHALL manter os mesmos registros do histórico. BASE-02; MON-02-AC02.
3. WHEN os serviços são reiniciados ou removidos por `docker compose down` e recriados sem apagar volumes, THEN o sistema SHALL manter uma linha sentinela inserida no schema `monitor`. BASE-03; MON-02-AC03.
4. The sistema SHALL documentar bootstrap/infraestrutura e os limites futuros de monitoramento, incidentes, notificações e consultas, impedindo dependências de produção em bibliotecas de teste. BASE-04; MON-02-AC04.

**Independent Test:** Construção, HTTP de prontidão e consulta SQL antes/depois do reinício em projeto Compose isolado.

### P1: Validar por contêineres

**User Story:** Como desenvolvedor, quero testes, qualidade e imagem verificados pelo mesmo fluxo no host e na CI.

**Acceptance Criteria**:
1. WHEN a CI executa a validação, THEN o workflow SHALL rodar `docker compose run --build --rm tests`, construir a imagem runtime e executar smoke test; cada comando obrigatório SHALL propagar falha para o job. BASE-05; MON-02-AC05.
2. The README SHALL listar comandos reais de build, início, parada, logs e testes, variáveis, relatórios e persistência, sem credenciais reais versionadas. BASE-06; MON-02-AC06.
3. The checkout SHALL não conter `mvnw`, `mvnw.cmd` ou arquivos exclusivos de `.mvn/wrapper`; o runtime SHALL conter JRE e JAR, executar como usuário não root e não conter Maven, javac ou socket Docker. BASE-07; MON-02-AC07.
4. WHEN o serviço `tests` executa `mvn verify`, THEN o sistema SHALL executar testes unitários e integração PostgreSQL real, verificar qualidade, retornar zero somente se todos passarem e salvar relatórios em `target`; uma falha SHALL retornar código diferente de zero. BASE-08; MON-02-AC08.

**Independent Test:** Serviço de testes separado, relatórios Surefire/Failsafe/Checkstyle e execução negativa em cópia isolada.

## Edge Cases

- IF o PostgreSQL está indisponível após startup, THEN a prontidão SHALL retornar HTTP 503 e `status=DOWN` (BASE-01).
- IF uma migração já aplicada foi alterada, THEN Flyway SHALL rejeitar o checksum diferente (BASE-02).
- IF um teste ou a qualidade falha, THEN `verify` SHALL falhar independentemente de um build runtime bem-sucedido (BASE-05/08).
- WHEN `down` é executado sem `--volumes`, THEN os dados SHALL permanecer; apagar volumes é operação explícita e não faz parte do comando de parada (BASE-03/06).

## Dimensões implícitas

| Dimensão | Cobertura |
| --- | --- |
| Validação e limites | Configuração por ambiente; porta local e banco sem publicação de porta |
| Falha / dependência externa | Prontidão 503 sem banco; falha de migração impede startup |
| Idempotência | Histórico Flyway inalterado no reinício |
| Acesso | Socket exclusivo do serviço tests; autenticação de produto em MON-03 |
| Concorrência / ordem | PostgreSQL saudável antes da aplicação; smoke isolado por projeto |
| Ciclo de dados | Volume persistente; limpeza explícita apenas de recursos descartáveis da validação |
| Observabilidade | Health/readiness, logs Compose e relatórios persistidos |
| Integridade | Checksum Flyway e sentinela após restart/down-up |
| Rate limiting / pagamentos | N/A: esta entrega não possui API administrativa nem cobrança |

## Requirement Traceability

| Requirement ID | Origem | Task | Teste / evidência prevista | Status |
| --- | --- | --- | --- | --- |
| BASE-01 | MON-02-AC01 | T2, T3, T4 | ApplicationIT; smoke Compose; checkout limpo | Implementing |
| BASE-02 | MON-02-AC02 | T3, T4 | MigrationIT: quatro assertions de histórico + checksum; histórico SQL no smoke ainda pendente | Implementing |
| BASE-03 | MON-02-AC03 | T4 | Sentinela SQL após restart/down-up | Implementing |
| BASE-04 | MON-02-AC04 | T2, T6 | ArchitectureTest e README | Implementing |
| BASE-05 | MON-02-AC05 | T5 | Workflow, gate local e sensor de falha | Implementing |
| BASE-06 | MON-02-AC06 | T6 | Comandos documentados confrontados com execução | Implementing |
| BASE-07 | MON-02-AC07 | T2, T4 | Inspeção checkout e runtime | Implementing |
| BASE-08 | MON-02-AC08 | T3, T5 | Surefire/Failsafe; sensor de falha | Implementing |

## Success Criteria

Oito critérios com evidências por arquivo/linha, gates aprovados e verificador independente. Execução local e execução remota da CI são registradas separadamente. Nenhuma entrega é marcada concluída com validação Docker pendente.
