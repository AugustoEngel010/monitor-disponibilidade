# MON-11: operaÃ§Ã£o e retenÃ§Ã£o

**Data:** 2026-10-20. **Status:** Implementing. ExecuÃ§Ã£o local autorizada pelo pedido.
**Origem:** MON-11-AC01 a MON-11-AC06, PROD-37; AD-001 a AD-011.

## Problem Statement

O operador precisa distinguir falhas do alvo de problemas do monitor e limitar o histÃ³rico volumoso sem perder processamento ou fabricar disponibilidade.

## Goals

- Medir saÃºde, concorrÃªncia, duraÃ§Ã£o, classificaÃ§Ã£o, atraso e outbox.
- Remover histÃ³rico elegÃ­vel com corte temporal explÃ­cito e seguro.
- Medir capacidade real em contÃªineres com alvos locais.

## Out of Scope

| Capacidade | RazÃ£o |
| --- | --- |
| Alta disponibilidade, broker, exportador externo, tracing distribuÃ­do | InstÃ¢ncia Ãºnica e Micrometer/Actuator existentes |
| ExclusÃ£o de monitores, push e deploy | Fora do pedido |
| SLA ou uptime temporal | MON-09 mede amostras |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| RetenÃ§Ã£o | 30 dias, 1..3650; execuÃ§Ã£o horÃ¡ria, lote 1000, 1..10000; habilitada por padrÃ£o | Premissa MON-01 e limites finitos | Default tÃ©cnico do pedido |
| Fronteira | Remover somente finishedAt/recoveredAt/completedAt estritamente anteriores ao corte UTC; igualdade permanece | Limite verificÃ¡vel com Clock |
| IdempotÃªncia | Recibos UUID mÃ­nimos durÃ¡veis para execuÃ§Ãµes removidas; rejeitar regravaÃ§Ã£o | NÃ£o reaplicar evidÃªncias antigas; recibos nÃ£o expiram |
| EvidÃªncia protegida | TransiÃ§Ãµes retidas, Ãºltima observaÃ§Ã£o, Ãºltimo APPLIED da geraÃ§Ã£o corrente, reservas e resultados ainda nÃ£o aplicados | Continuidade e ordenaÃ§Ã£o MON-06/07 |
| Consultas | historyAvailableFrom persistido e monotÃ´nico; null antes da primeira limpeza; retainedOutPeriods separados das lacunas | Aumentar TTL nÃ£o restaura dados removidos |
| Logs | JSON ECS e campos de correlaÃ§Ã£o tipados; sem nomes, URLs, destinatÃ¡rios ou Throwable nos eventos operacionais | Manter sanitizaÃ§Ã£o MON-03/05/08 |
| Capacidade | 100 monitores, I=10s/T=2s, C=4, alvos 200 imediato/200 apÃ³s 300ms/503/timeout; duraÃ§Ã£o mÃ­nima 60s | Exercitar limites MON-01 sem prometer throughput |

**Open questions:** none; detalhes nÃ£o discutidos registrados como defaults acima.

## User Stories

### P1: diagnosticar e conservar a operaÃ§Ã£o

**User Story:** Como operador, quero sinais confiÃ¡veis e histÃ³rico limitado sem perder trabalho.

**Acceptance Criteria**:
1. WHEN PostgreSQL ficar indisponÃ­vel THEN o sistema SHALL retornar readiness 503/DOWN e liveness 200/UP, expondo somente status nos trÃªs probes pÃºblicos e exigindo Basic nas mÃ©tricas (OPS-01; MON-11-AC01).
2. WHEN verificaÃ§Ãµes agendadas executarem THEN o sistema SHALL medir inFlight, capacidade, atraso nÃ£o negativo, duraÃ§Ã£o e resultados nos nove valores de Outcome, alÃ©m de gauges PENDING/SENDING/EXHAUSTED, sem URL ou UUID em tags (OPS-02; MON-11-AC02).
3. WHEN resultados, transiÃ§Ãµes e entregas forem confirmados THEN o sistema SHALL emitir eventos JSON correlacionÃ¡veis por monitorId/executionId/incidentId/notificationId quando aplicÃ¡veis, com resultado de vocabulÃ¡rio fixo e sem conteÃºdo sensÃ­vel; rollback SHALL nÃ£o emitir confirmaÃ§Ã£o (OPS-03; MON-11-AC03).
4. WHEN a limpeza rodar THEN o sistema SHALL remover em lotes somente histÃ³rico estritamente anterior ao corte, preservando incidentes abertos, toda a cadeia de notificaÃ§Ãµes nÃ£o terminais ou recÃ©m concluÃ­das, reservas, evidÃªncia corrente e idempotÃªncia; falha SHALL reverter o lote e seu watermark (OPS-04; MON-11-AC04).
5. WHEN uma consulta cruzar historyAvailableFrom THEN o sistema SHALL informar o limite e retainedOutPeriods, excluir amostras anteriores do cÃ¡lculo e retornar NO_DATA/null/zero amostras em janela inteiramente removida, sem converter proteÃ§Ã£o referencial em disponibilidade histÃ³rica (OPS-05; MON-11-AC05).
6. WHEN o cenÃ¡rio controlado terminar THEN o relatÃ³rio SHALL registrar recursos, parÃ¢metros, duraÃ§Ã£o e mediÃ§Ãµes de concorrÃªncia, atrasos, classificaÃ§Ãµes e backlog, verificando C<=4, ausÃªncia de sobreposiÃ§Ã£o por monitor e limite de 100 monitores (OPS-06; MON-11-AC06).

**Independent Test:** JUnit/AssertJ, Clock fixo, PostgreSQL Testcontainers, HTTP local e gates exclusivamente Docker.

## Edge Cases

- Corte exato e Â±1Âµs; limpeza repetida; relÃ³gio recuado; TTL aumentado; lote parcial e rollback.
- Incidente fechado com queda terminal e recuperaÃ§Ã£o pendente; SENDING; tentativa HTTP reservada; resultado sem aplicaÃ§Ã£o; Ãºltima aplicaÃ§Ã£o interna mantÃ©m ordenaÃ§Ã£o.
- Resultado removido reenviado nÃ£o pode ressurgir. Ã‰poca corrente e Ã©pocas de evidÃªncias protegidas permanecem.
- Janela removida, cruzada e apÃ³s corte; amostra protegida anterior nÃ£o gera cobertura nem taxa; watermark null preserva MON-09.
- ExceÃ§Ãµes com credenciais/corpo/URL nÃ£o entram em logs; mÃ©tricas nÃ£o convertem erro de consulta em backlog zero.

## DimensÃµes implÃ­citas

ValidaÃ§Ã£o: limites de TTL/lote/poll. Falhas: rollback e eventos fixos. IdempotÃªncia: recibos durÃ¡veis. Auth: AD-005. ConcorrÃªncia: locks do monitor, lote limitado e instÃ¢ncia Ãºnica. ExpiraÃ§Ã£o: OPS-04/05. Observabilidade: OPS-01/02/03. DependÃªncias: readiness PostgreSQL, SMTP assÃ­ncrono via backlog. Integridade: FKs e proteÃ§Ã£o da cadeia completa. Rate limiting N/A ao MVP local.

## Requirement Traceability

| Requirement ID | Origem | Tasks | Status |
| --- | --- | --- | --- |
| OPS-01 | MON-11-AC01 | T1 | Verified |
| OPS-02 | MON-11-AC02 | T1 | Verified |
| OPS-03 | MON-11-AC03 | T2 | Verified |
| OPS-04 | MON-11-AC04 | T3 | Verified |
| OPS-05 | MON-11-AC05 | T4,T5 | Implementing |
| OPS-06 | MON-11-AC06 | T6,T7 | Pending |

## Success Criteria

Seis ACs com assertions/evidÃªncias reais; regressÃ£o Docker, runtime, painel, smoke, cenÃ¡rio de capacidade e Verifier independente com sensor isolado PASS.
