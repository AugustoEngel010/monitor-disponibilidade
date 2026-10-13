# MON-02: evidências observadas

**Data:** 2026-10-13. Resultados locais; nenhuma execução remota de GitHub Actions observada.

## Ambiente e imagens

Windows com Docker Desktop 4.94.0, Engine Linux 29.8.2, Compose 5.5.1. Nenhum comando Maven/JDK do host executado.

| Imagem | Digest observado |
| --- | --- |
| maven:3.9.11-eclipse-temurin-21 | sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 |
| eclipse-temurin:21.0.8_9-jre-jammy | sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0 |
| postgres:17.6-alpine | sha256:ef257d85f76e48da1c64832459b59fcaba1a4dac97bf5d7450c77753542eee94 |

## Comandos e resultados

| Comando | Resultado |
| --- | --- |
| validate_spec / validate_tasks | Zero erros e avisos |
| docker build --target build -t monitor-bootstrap . | Exit 0; um unitário; zero Checkstyle violations |
| docker compose run --build --rm tests | Exit 0; após regressão: 1 unitário + 5 integrações, zero failures/errors/skips |
| docker compose up --build -d | Exit 0; db saudável; app em 127.0.0.1:8080; readiness 200/UP |
| docker compose exec -T db psql ... | Histórico inicial V1: 1\|t |
| build --no-cache app no worktree a583a9c | Exit 0; build partiu do código sem target/env, antes da correção de search_path |
| validate-compose.ps1 sobre export Git eb6829ab68a80e791be2cfeedd1e3fe6bf82fb77 | Exit 0; código corrigido; sem target/env no export inicial |

XMLs locais: `target/surefire-reports/TEST-br.com.monitor.ArchitectureTest.xml` (1), `target/failsafe-reports/TEST-br.com.monitor.ApplicationIT.xml` (3), `target/failsafe-reports/TEST-br.com.monitor.MigrationIT.xml` (2). Zero falhas/erros/skips em todos. `target/checkstyle-result.xml` sem violações.

## Saída do smoke limpo

Projeto isolado `mon02-smoke-824c724cee3d`; log completo local em `.validation/clean-source-t4/.validation/mon02-smoke-824c724cee3d.log`.

```text
PASS MON-02-AC01 HTTP readiness = 200
PASS MON-02-AC01 readiness status = UP
PASS MON-02-AC02 migration count = 1
PASS MON-02-AC02 migration version = 1
PASS MON-02-AC07 UID=10001, JAR present, no Maven/javac/socket
PASS MON-02-AC03 restart sentinel = 4242
PASS MON-02-AC02 restart history = mesma linha JSON integral
PASS MON-02-AC03 down/up sentinel = 4242
PASS MON-02-AC02 down/up history = mesma linha JSON integral
PASS Compose lifecycle
```

## Falhas detectadas e corrigidas

1. `mvn clean` tentou apagar bind mount de target; exit 1. Build agora usa diretório interno e copia para `/reports`, preservando o código de saída.
2. Usuário PostgreSQL monitor passou a buscar histórico no schema monitor criado por V1; restart falhou. `spring.flyway.default-schema=public` e `schemas=public` corrigem. Teste adicional usa usuário/schema iguais, reinicia contexto e verifica sentinela e histórico.

O verificador independente registrará o checkout final, os testes negativos e o sensor em validation.md. Esta evidência parcial não substitui seu veredito.
