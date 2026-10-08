# MON-01: contexto de produto

**Gathered:** 2026-10-08.
**Spec:** [spec.md](spec.md).
**Status:** proposta pronta para revisão; recomendações não ratificadas.

## Feature Boundary

Definir produto, MVP, modelo conceitual, contratos de comportamento e estratégia de testes. MON-01 não implementa a aplicação nem decompõe entregas futuras em tasks. O backlog original é preservado, inclusive seus critérios ainda desmarcados.

## Implementation Decisions

### Baseline solicitado

Java, Spring Boot, PostgreSQL e Flyway; monólito modular; execução local por Compose. Um operador, uma instância, GET HTTP/HTTPS com código esperado exato, e-mail e painel administrativo simples. A spec descreve produto futuro; a implementação começa em MON-02.

### Ambiguidades e recomendações

| Decisão | Alternativa considerada | Recomendação incorporada à proposta | Motivo e consequência | Rastreabilidade |
| --- | --- | --- | --- | --- |
| Pausa durante incidente | Encerrar como interrompido | Preservar incidente, invalidar tentativa e zerar confirmação | Pausa não é evidência de recuperação; incidente pode durar muito tempo pausado | A-02; PROD-05/06/29; MON-01-AC04 |
| Edição de alvo/política com incidente | Encerrar administrativamente e iniciar outro contexto | Rejeitar com 409 até recuperação | Mantém identidade do incidente; operador não consegue trocar alvo permanentemente quebrado no MVP | A-02; PROD-08/09; MON-01-AC04 |
| Lacuna de observação | Preservar contadores até nova amostra | Zerar sequências, manter estado e incidente | Evita confirmação com observações distantes; confirmação pode demorar sob sobrecarga | A-03; PROD-17/34; MON-01-AC03/AC06 |
| Estado inicial | UP por presunção, ou exigir R sucessos iniciais | UNKNOWN; primeiro sucesso gera UP sem recuperação | Distingue falta de observação de indisponibilidade | A-04; PROD-10/11; MON-01-AC03 |
| Agendamento | Intervalo após término, ou recuperar todos os ticks | Início + I; capacidade limitada; sem rajada | Cadência simples; atrasos ficam visíveis em vez de esconder sobrecarga | A-01/A-05; PROD-25/26/27; MON-01-AC02/AC04 |
| Resultado interno | Ignorar preservando contadores, ou contar falha externa | Interromper sequências sem mudar estado | Falha do monitor não comprova falha do destino | A-06; PROD-17; MON-01-AC03 |
| Frescor | Um intervalo, limite arbitrário fixo ou infinito | 2 × I + T; fronteira inclusiva no resumo | Tolera uma rodada perdida; atraso maior aparece como ausência recente | A-08; PROD-34/36; MON-01-AC06 |
| Indicadores | Uptime temporal inferido de amostras | Taxa por amostra e duração de incidentes separadas | Evita afirmar disponibilidade contínua sem observação | A-08; PROD-35/36; MON-01-AC06 |
| Retry e ordem de e-mails | Retry infinito; envio de recuperação independente | Cinco tentativas; queda aceita/esgotada antes da recuperação | Ordem local clara e trabalho finito; esgotamento não bloqueia recuperação para sempre | A-07; PROD-30/31/32; MON-08 |
| Destinos e dados | Aceitar rede interna e URLs arbitrárias | Públicos com exceção exata de demo; URL sem query/segredos | Restringe rede e evita tokens persistidos; alguns endpoints ficam fora do MVP | A-09; PROD-02/24; MON-04/MON-05 |
| Retenção | Histórico ilimitado ou limpeza indiscriminada | 30 dias; proteger referências correntes e pendências | Volume limitado sem apagar trabalho ainda necessário | A-10; PROD-37; MON-11 |

### Agent's Discretion

A organização e a redação dos artefatos seguem o pedido. Não houve delegação explícita de decisões de produto nem aceite das recomendações. O usuário recebeu duas perguntas opcionais sobre pausa/edição e lacunas. Sem resposta registrada nesta entrega, A-02 e A-03 permanecem premissas, assim como os demais defaults não confirmados.

### Declined / Undiscussed Gray Areas → Assumptions

Não há área declarada recusada pelo usuário. A-01 a A-10 constam em Assumptions & Open Questions da spec, com default, motivo e confirmação. A proposta não está sendo apresentada como aprovada. Não se iniciou Design, Tasks ou Execute do produto.

## Detalhes adiados com dono

| Entrega | Detalhe que deve ser resolvido antes de implementar |
| --- | --- |
| MON-02 | Versões suportadas, wrapper/build, organização física, dependências, migrações, configuração e comandos reais de CI |
| MON-03 | Contrato de credenciais obrigatórias, CSRF e proteção de recursos operacionais |
| MON-04 | Endpoints/DTOs, gramática de e-mail, normalização de URL, paginação e concorrência de alterações administrativas |
| MON-05 | Enumeração verificável de redes proibidas, resolução/conexão segura, limites efetivos de timeout e sanitização |
| MON-06/MON-07 | Coordenação de pausa/resultado, recuperação transacional de execuções e relógio controlável |
| MON-08 | Limites de envio SMTP, recuperação de SENDING e integração com outbox durável |
| MON-09/MON-11 | Contratos de consulta, intervalos sem dados, fronteira retida, exclusão em lotes e métricas |
| MON-10/MON-12 | Interface, roteiro e evidências visuais do comportamento real |

Esses detalhes não impedem MON-02 e não autorizam mudança silenciosa nas regras desta spec.

## Specific References

Fonte de produto: [monitor-disponibilidade-tasks.md](../../task/monitor-disponibilidade-tasks.md), MON-01 e critérios MON-01-AC01 a MON-01-AC07. Fonte de processo: skill local [tlc-spec-driven](../../../.codex/skills/tlc-spec-driven/SKILL.md), versão declarada 3.3.0; referências Specify, Discuss e Memory lidas integralmente. Não foi necessário pesquisar APIs ou versões para esta entrega de produto.

## Deferred Ideas

Evoluções do backlog: página pública, manutenção, organizações, múltiplos executores/regiões, canais adicionais, conteúdo/certificados e heartbeat. Encerramento administrativo e troca de alvo com incidente aberto também ficam para evolução, se desejados. Nenhuma dessas capacidades integra a implementação autorizada nesta entrega.
