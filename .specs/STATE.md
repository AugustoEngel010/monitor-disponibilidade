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

### AD-006
- **Decision**: MON-04 aplica os limites e as regras administrativas da MON-01 por instrução atual. Persistir configurationVersion para destino/política e executionGeneration para invalidar trabalho após edição, pausa ou retomada; mudanças de política com incidente aberto retornam 409.
- **Reason**: Separar identidade da política de invalidação de execução, preservando histórico e incidente sem inventar recuperação.
- **Trade-off**: MON-06/MON-07 devem bloquear a mesma linha do monitor e comparar versão/geração antes de aplicar resultados; o cancelamento real ainda depende do executor.
- **Scope**: MON-04 a MON-09; PROD-02 a PROD-10/29. A ratificação se limita aos contratos administrativos usados nesta entrega.
- **Date**: 2026-10-13
- **Status**: active

## Handoff

- **Feature**: MON-04; `.specs/features/mon-04-ciclo-monitores/`.
- **Phase / Task**: Execute concluído; T1 a T9 e verificação independente.
- **Completed**: Nove commits atômicos 9cdb57c, a3093c4, 50a1415, 97aff0b, 629f4e1, b7f83fe, f83aef2, 7c01733, f46df05. Todos os sete ACs e LIFE08/09 PASS. 160 testes (46 unitários/114 integrações), zero falhas/erros/skips/Checkstyle; runtime e smoke independente aprovados; seis sensores mortos.
- **In-progress**: Nenhuma implementação pendente da MON-04.
- **Next step**: MON-05, executor HTTP/registro de resultados e política de rede; usar versão/geração persistentes. MON-06 deve serializar pausa/resultados pelo mesmo lock de linha e comparar versão/geração; MON-07 adicionará incidentes/FKs reais.
- **Blockers**: Nenhum local. CI remota não executada, push não realizado. API não executa verificações ainda; incidentes/histórico existentes foram representados por fixtures nos testes desta entrega.
- **Uncommitted files**: Nenhum após commit de fechamento; relatório, backlog, estado e rastreabilidade incluídos nele.
- **Branch**: main; baseline da7276f, implementação validada f46df05. Banco de desenvolvimento preservado; somente recursos descartáveis da validação removidos.
