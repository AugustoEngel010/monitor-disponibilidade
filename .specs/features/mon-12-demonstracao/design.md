# MON-12 Design

## Architecture Overview

Preservar o monólito modular e seu banco. Adicionar API Node sem dependências, com dois alvos e controle local por POST. Seed usa a API administrativa e não SQL. Um runner Node faz assertions de consultas e captura telas com Playwright; PowerShell controla stop/restart/recreate do Compose.

Alternativas consideradas: incluir alvo no Spring alteraria o runtime de domínio; simular no navegador não provaria HTTP/SMTP reais. Serviço separado mantém a origem explícita MON-05 e permite falhas independentes.

## Code Reuse Analysis

Reutilizar contratos de MonitorController, SummaryQueries e HistoryQueries; Basic/CSRF MON-03; imagens e Playwright já usados no projeto; comandos de testes e smoke existentes. Nenhuma migração ou alteração Java.

## Components

| Componente | Local | Interface |
| --- | --- | --- |
| API demo | config/demo/server.mjs | GET /targets/main e /targets/secondary; POST /control/main/healthy, /error, /slow; GET /control |
| Seed | config/demo/seed.mjs | Dois monitores por URL, F=3/R=2/I=10/T=2, destinatário portfolio@example.test |
| Cliente e roteiro | config/demo/ | Basic/CSRF, polling limitado, snapshots JSON e screenshots |
| Orquestração | scripts/demo.ps1 | Projeto Compose isolado, SMTP parado/restaurado, app reiniciado e scheduler desligado/religado |

## Data Models

Modos do alvo são voláteis e voltam a healthy ao recriar demo. Monitores e registros permanecem no volume PostgreSQL. Seed não muda monitores existentes e rejeita divergência. Runner registra IDs e snapshots em volume de relatórios.

## Error Handling Strategy

Polling tem deadline e erros preservam o diagnóstico; roteiro não apaga volume automaticamente. Credenciais são externas e nunca impressas. Seed espera readiness com timeout; nenhuma chamada administrativa ignora CSRF.

## Risks & Concerns

| Risco | Local | Mitigação |
| --- | --- | --- |
| Seed concorrente duplicar URL | MonitorController permite URLs duplicadas | Seed serial no Compose; documentar não executar simultaneamente |
| SMTP duplicado em confirmação incerta | AD-010 | Distinguir intenção única de entrega SMTP; não prometer exactly-once |
| Lacuna virar recuperação aparente | SummaryQueries.label | Assert estado DOWN e mesmo incidente durante STALE e pausa |
| Credenciais em relatórios | Runner | Somente snapshots de negócio; sem headers/env |
| Roteiro alterar dados pessoais | Orquestração | Projeto separado, credenciais fictícias e volume próprio |

## Tech Decisions

Sem nova convenção arquitetural global. Execução inline em sete tasks e Verifier independente automático exigido pela skill. Proibição de commits prevalece sobre o ciclo de commit da skill.
