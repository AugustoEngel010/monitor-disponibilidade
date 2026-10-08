# STATE

## Decisions

As decisões abaixo registram a baseline solicitada e os contratos propostos em MON-01. `active` significa vigente nesta proposta; aprovação de produto segue pendente onde indicado. Nenhuma implementação existe.

### AD-001
- **Decision**: Usar monólito modular Java/Spring Boot com PostgreSQL/Flyway, uma instância e execução local por Docker Compose.
- **Reason**: Entregar um portfólio backend reproduzível com limites de domínio, sem coordenação distribuída.
- **Trade-off**: Não oferecer execução multirregional, alta disponibilidade do executor ou múltiplas organizações no MVP.
- **Scope**: MON-02 a MON-12; baseline expressa pelo usuário e backlog; MON-01-AC01.
- **Date**: 2026-10-08
- **Status**: active

### AD-002
- **Decision**: Persistir aplicação do resultado, estado, incidente, transição e intenção de notificação atomicamente; enviar e-mail fora da transação.
- **Reason**: Um reinício entre confirmação e envio não pode perder a intenção nem gerar transição parcial.
- **Trade-off**: Exige coordenação transacional e trabalho durável; SMTP pode aceitar uma mensagem cuja confirmação local seja perdida.
- **Scope**: MON-05 a MON-08; PROD-18/20/21/30; MON-01-AC03/AC05/AC07. Contrato recomendado, ratificação pendente.
- **Date**: 2026-10-08
- **Status**: active

### AD-003
- **Decision**: Separar ciclo de vida, estado confirmado e frescor; não inferir recuperação de pausa nem uptime temporal da taxa de sucesso.
- **Reason**: Ausência de dados e suspensão administrativa não demonstram saúde do destino.
- **Trade-off**: Consultas e painel precisam representar dimensões simultâneas; incidente pode permanecer aberto com evidência antiga.
- **Scope**: MON-04, MON-06, MON-07, MON-09 a MON-12; PROD-05/06/17/34/35/36; MON-01-AC03/AC04/AC06. Ratificação pendente.
- **Date**: 2026-10-08
- **Status**: active

## Handoff

- **Feature**: MON-01; `.specs/features/mon-01-produto-contratos/`.
- **Phase / Task**: Specify documental; proposta aguardando revisão de produto.
- **Completed**: spec, context e decisões produzidos; sete critérios de origem mapeados; nenhuma entrega de implementação concluída.
- **In-progress**: Nenhum código; defaults A-01 a A-10 não ratificados.
- **Next step**: Revisar/ratificar premissas de MON-01 e atualizar contrato se necessário; então especificar MON-02 a partir desta baseline.
- **Blockers**: Nenhuma lacuna impeditiva para especificar MON-02; aprovação antes de avançar no fluxo da skill.
- **Uncommitted files**: Três artefatos em `.specs/`; não há repositório Git inicializado.
- **Branch**: N/A; Git inexistente. Nenhum commit, build ou teste de aplicação executado.
