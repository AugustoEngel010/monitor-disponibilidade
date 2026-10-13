# MON-02: contexto

**Gathered:** 2026-10-13. **Status:** execução autorizada pelo pedido.

## Feature Boundary

Base Spring Boot/PostgreSQL/Flyway, build, testes, Compose, CI e documentação. Preservar MON-02-AC01 a AC08. Nenhuma alteração nos contratos de negócio propostos por MON-01.

## Implementation Decisions

O usuário decidiu construir, testar e executar com Docker, sem ferramentas Java no host. `docker compose up --build` é o comando principal. Testcontainers deve rodar num serviço separado, com acesso ao daemon e configuração de rede documentada. A implementação e os commits locais estão autorizados pelo pedido; não é necessário repetir confirmações de fases.

O checkout inicial d6a9601 contém apenas SDD e skills. Não há pom, código, wrapper, CI ou README. A alteração não versionada no backlog veio do usuário e será preservada integralmente. O handoff de MON-01 dizia que não havia Git; o histórico real prevalece.

### Undiscussed Gray Areas → Assumptions

Defaults técnicos e limites suportados estão em Assumptions & Open Questions da spec. A investigação confirmou Docker Desktop com daemon Linux. Context7 não está disponível; foram consultadas fontes oficiais após o código e os documentos.

## Deferred Ideas

Funcionalidades MON-03 a MON-12, atualização automática de imagens e publicação remota ficam fora desta entrega.
