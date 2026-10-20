# MON-11: operação e retenção

**Data:** 2026-10-20. **Status:** Implementing. Execução local autorizada pelo pedido.
**Origem:** MON-11-AC01 a MON-11-AC06, PROD-37; AD-001 a AD-011.

## Problem Statement

O operador precisa distinguir falhas do alvo de problemas do monitor e limitar o histórico volumoso sem perder processamento ou fabricar disponibilidade.

## Goals

- Medir saúde, concorrência, duração, classificação, atraso e outbox.
- Remover histórico elegível com corte temporal explícito e seguro.
- Medir capacidade real em contêineres com alvos locais.

## Out of Scope

| Capacidade | Razão |
| --- | --- |
| Alta disponibilidade, broker, exportador externo, tracing distribuído | Instância única e Micrometer/Actuator existentes |
| Exclusão de monitores, push e deploy | Fora do pedido |
| SLA ou uptime temporal | MON-09 mede amostras |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Retenção | 30 dias, 1..3650; execução horária, lote 1000, 1..10000; habilitada por padrão | Premissa MON-01 e limites finitos | Default técnico do pedido |
| Fronteira | Remover somente finishedAt/recoveredAt/completedAt estritamente anteriores ao corte UTC; igualdade permanece | Limite verificável com Clock |
| Idempotência | Recibos UUID mínimos duráveis para execuções removidas; rejeitar regravação | Não reaplicar evidências antigas; recibos não expiram |
| Evidência protegida | Transições retidas, última observação, último APPLIED da geração corrente, reservas e resultados ainda não aplicados | Continuidade e ordenação MON-06/07 |
| Consultas | historyAvailableFrom persistido e monotônico; null antes da primeira limpeza; retainedOutPeriods separados das lacunas | Aumentar TTL não restaura dados removidos |
| Logs | JSON ECS e campos de correlação tipados; sem nomes, URLs, destinatários ou Throwable nos eventos operacionais | Manter sanitização MON-03/05/08 |
| Capacidade | 100 monitores, I=10s/T=2s, C=4, alvos 200 imediato/200 após 300ms/503/timeout; duração mínima 60s | Exercitar limites MON-01 sem prometer throughput |

**Open questions:** none; detalhes não discutidos registrados como defaults acima.

## User Stories

### P1: diagnosticar e conservar a operação

**User Story:** Como operador, quero sinais confiáveis e histórico limitado sem perder trabalho.

**Acceptance Criteria**:
1. WHEN PostgreSQL ficar indisponível THEN o sistema SHALL retornar readiness 503/DOWN e liveness 200/UP, expondo somente status nos três probes públicos e exigindo Basic nas métricas (OPS-01; MON-11-AC01).
2. WHEN verificações agendadas executarem THEN o sistema SHALL medir inFlight, capacidade, atraso não negativo, duração e resultados nos nove valores de Outcome, além de gauges PENDING/SENDING/EXHAUSTED, sem URL ou UUID em tags (OPS-02; MON-11-AC02).
3. WHEN resultados, transições e entregas forem confirmados THEN o sistema SHALL emitir eventos JSON correlacionáveis por monitorId/executionId/incidentId/notificationId quando aplicáveis, com resultado de vocabulário fixo e sem conteúdo sensível; rollback SHALL não emitir confirmação (OPS-03; MON-11-AC03).
4. WHEN a limpeza rodar THEN o sistema SHALL remover em lotes somente histórico estritamente anterior ao corte, preservando incidentes abertos, toda a cadeia de notificações não terminais ou recém concluídas, reservas, evidência corrente e idempotência; falha SHALL reverter o lote e seu watermark (OPS-04; MON-11-AC04).
5. WHEN uma consulta cruzar historyAvailableFrom THEN o sistema SHALL informar o limite e retainedOutPeriods, excluir amostras anteriores do cálculo e retornar NO_DATA/null/zero amostras em janela inteiramente removida, sem converter proteção referencial em disponibilidade histórica (OPS-05; MON-11-AC05).
6. WHEN o cenário controlado terminar THEN o relatório SHALL registrar recursos, parâmetros, duração e medições de concorrência, atrasos, classificações e backlog, verificando C<=4, ausência de sobreposição por monitor e limite de 100 monitores (OPS-06; MON-11-AC06).

**Independent Test:** JUnit/AssertJ, Clock fixo, PostgreSQL Testcontainers, HTTP local e gates exclusivamente Docker.

## Edge Cases

- Corte exato e Â±1Âµs; limpeza repetida; relógio recuado; TTL aumentado; lote parcial e rollback.
- Incidente fechado com queda terminal e recuperação pendente; SENDING; tentativa HTTP reservada; resultado sem aplicação; última aplicação interna mantém ordenação.
- Resultado removido reenviado não pode ressurgir. Ã‰poca corrente e épocas de evidências protegidas permanecem.
- Janela removida, cruzada e após corte; amostra protegida anterior não gera cobertura nem taxa; watermark null preserva MON-09.
- Exceções com credenciais/corpo/URL não entram em logs; métricas não convertem erro de consulta em backlog zero.

## Dimensões implícitas

Validação: limites de TTL/lote/poll. Falhas: rollback e eventos fixos. Idempotência: recibos duráveis. Auth: AD-005. Concorrência: locks do monitor, lote limitado e instância única. Expiração: OPS-04/05. Observabilidade: OPS-01/02/03. Dependências: readiness PostgreSQL, SMTP assíncrono via backlog. Integridade: FKs e proteção da cadeia completa. Rate limiting N/A ao MVP local.

## Requirement Traceability

| Requirement ID | Origem | Tasks | Status |
| --- | --- | --- | --- |
| OPS-01 | MON-11-AC01 | T1 | Verified |
| OPS-02 | MON-11-AC02 | T1 | Verified |
| OPS-03 | MON-11-AC03 | T2 | Verified |
| OPS-04 | MON-11-AC04 | T3,T8,T9,T10 | Implementing |
| OPS-05 | MON-11-AC05 | T4,T5 | Verified |
| OPS-06 | MON-11-AC06 | T6,T7 | Verified |

## Success Criteria

Seis ACs com assertions/evidências reais; regressão Docker, runtime, painel, smoke, cenário de capacidade e Verifier independente com sensor isolado PASS.
