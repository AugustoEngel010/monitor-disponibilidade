# MON-02: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada.

## Architecture Overview

Um Dockerfile fornece uma etapa Maven/JDK com código-fonte, uma etapa build sem acesso ao daemon e uma etapa runtime JRE com somente o JAR. Compose constrói runtime para a aplicação e a etapa de código para testes. `tests` roda `verify` no seu startup, fora de `docker build`, e recebe o socket Docker. O perfil tests não participa do `up` normal.

Aplicação → PostgreSQL persistente, após `pg_isready`. Testes → daemon Docker → PostgreSQLs descartáveis. Portas aleatórias dos PostgreSQLs de teste são acessadas por `host.docker.internal`; no Engine Linux, Compose resolve esse nome com `host-gateway`. Não são usados bind mounts nos contêineres criados por Testcontainers, evitando a divergência de paths Windows/Linux.

## Code Reuse Analysis

Nenhum código anterior. Reutilizar contratos do backlog, decisões AD-001 a AD-003 e validadores da skill. AD-004 fixa o fluxo por contêineres sem alterar contratos funcionais.

## Components

| Componente | Responsabilidade | Dependências |
| --- | --- | --- |
| Bootstrap `br.com.monitor` | Inicializar Spring e infraestrutura | Spring Boot |
| Recursos/configuração | JDBC externo, health e Flyway | PostgreSQL e ambiente |
| Build | Compilar JAR Java 21 | Maven no contêiner |
| Runtime | Executar JAR como usuário 10001 | JRE 21, sem socket |
| Testes | Qualidade, arquitetura e integração real | Maven/JDK, daemon local |
| Smoke operacional | Startup, histórico e persistência | Docker CLI e PowerShell |

Os módulos de negócio são documentados como futuros; não criar classes vazias que aparentem funcionalidades entregues. Produção não depende de JUnit, ArchUnit ou Testcontainers.

## Data Models

V1 cria o schema `monitor`. Flyway usa `public.flyway_schema_history`. O smoke cria uma tabela sentinela somente no projeto descartável. Nenhuma tabela de domínio é antecipada.

## Error Handling Strategy

Banco indisponível → health/readiness 503; checksum alterado → migração falha; teste/Checkstyle falha → verify não zero. A construção runtime empacota com testes separados, e a CI exige aprovação do serviço tests antes do build.

## Risks & Concerns

| Risco | Mitigação |
| --- | --- |
| Socket concede controle sobre daemon | Restrito a tests e documentado; runtime nunca recebe socket |
| Docker Desktop / Engine têm rotas distintas | Override documentado e host-gateway; validar Desktop local e usar mesma configuração Linux na CI |
| Tags explícitas podem mudar conteúdo | Versões explícitas verificadas por pull; registrar digests observados como evidência, sem prometer imutabilidade de tags |
| CI remota não executada | Não publicar nem declarar CI verde; validar seus comandos localmente |
| Ausência de suíte anterior | Testes derivados dos ACs; sensor independente ao concluir |

## Tech Decisions

Fontes oficiais: [compatibilidade Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Testcontainers em contêineres irmãos](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/), [ordem e saúde no Compose](https://docs.docker.com/compose/how-tos/startup-order/).

Usar daemon do host em vez de Docker-in-Docker segue a decisão do usuário e evita um segundo daemon. Montar o `target` do host em `/reports` e copiar a saída após `clean verify`, preservando seu exit code. Não montar o diretório de build `/workspace/target`: Maven clean falha ao tentar apagar um ponto de montagem. Cache Maven em volume; código por COPY na etapa de testes.
