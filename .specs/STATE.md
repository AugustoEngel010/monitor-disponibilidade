# STATE

## Decisions

As decisões abaixo registram a baseline solicitada e os contratos propostos em MON-01. `active` significa vigente; aprovação de produto segue pendente onde indicado. MON-02 entrega a base executável por Docker, sem alterar os contratos de negócio ainda propostos por MON-01.

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

### AD-004
- **Decision**: Construir, testar e executar exclusivamente por contêineres Docker; abandonar Maven Wrapper e não exigir Maven ou JDK no host.
- **Reason**: Decisão expressa do usuário para reproduzir MON-02 com `docker compose up --build` diretamente do código-fonte.
- **Trade-off**: Testcontainers precisa de acesso ao daemon e configuração de rede no serviço tests; testes ficam separados do build runtime.
- **Scope**: MON-02 a MON-12; MON-02-AC01/AC05/AC06/AC07/AC08. O checkout inicial não continha wrapper; não há arquivos a remover.
- **Date**: 2026-10-13
- **Status**: active

### AD-005
- **Decision**: Um operador HTTP Basic com ADMIN_USERNAME/ADMIN_PASSWORD obrigatórios; autenticar toda rota salvo GET/HEAD dos três health endpoints. API e painel usam token CSRF em sessão, header X-CSRF-TOKEN ou parâmetro _csrf. Autenticação não é persistida na sessão.
- **Reason**: Restringir administração e proteger ações de navegador, preservando readiness sem segredo em probes.
- **Trade-off**: Clientes API também precisam do fluxo CSRF; rotação exige recriar app; HTTPS ao expor fora do host.
- **Scope**: MON-03/MON-04/MON-10/MON-11; PROD-01. Defaults técnicos documentados na spec MON-03, dentro da execução solicitada.
- **Date**: 2026-10-13
- **Status**: active
## Handoff

- **Feature**: MON-02; `.specs/features/mon-02-base-docker/`.
- **Phase / Task**: Execute concluído; T1 a T6 e verificação independente.
- **Completed**: Seis commits locais até 6c4eafc; verificador PASS nos oito ACs. Checkout final limpo, build sem cache, um unitário e cinco integrações, qualidade, startup e persistência após restart/down-up. Dois sensores mortos; runtime restaurado.
- **In-progress**: Nenhuma implementação pendente da MON-02.
- **Next step**: MON-03, especificar proteção administrativa; confirmar premissas de negócio de MON-01 na entrega afetada.
- **Blockers**: Nenhum local. GitHub Actions configurada, sem execução remota observada; push não realizado.
- **Uncommitted files**: Nenhum após o commit de fechamento; relatório, rastreabilidade e este snapshot incluídos nele.
- **Branch**: main; baseline d6a9601. Aplicação local pronta; banco de desenvolvimento preservado.
