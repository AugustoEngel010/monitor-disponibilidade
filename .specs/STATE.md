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

### AD-007
- **Decision**: MON-05 produz resultado por UUID com snapshot de configurationVersion/executionGeneration; primeiro registro vence. HTTP é limitado até cabeçalhos e usa um único IP do snapshot DNS integralmente validado, sem fallback, proxy, redirect ou retry. TLS padrão preservado; exceção interna somente por MONITOR_DEMO_ORIGIN exata e explícita.
- **Reason**: Evitar rebinding, erro TLS mascarado e alteração de evidência sob repetição; preservar identificação de trabalho tardio para MON-06/07.
- **Trade-off**: Uma tentativa pode falhar no primeiro IP mesmo que outro esteja acessível; idempotência da persistência não garante envio HTTP único. DNS nativo pode concluir depois do timeout, sem abrir conexão posterior.
- **Scope**: MON-05 a MON-09; PROD-21/22/23/24/29; MON-05-AC01 a MON-05-AC08. Aplicação ao estado/incidente/intenção continua sob AD-002 em MON-07/08.
- **Date**: 2026-10-13
- **Status**: active

## Handoff

- **Feature**: MON-05; `.specs/features/mon-05-verificacao-http/`.
- **Phase / Task**: T1 a T5 implementadas; verificação independente pendente.
- **Completed**: Política de rede, executor GET com timeout/cleanup/TLS, V3 e registro idempotente, serviço Spring, configuração demo e documentação. Gates 264 testes (110 unitários/154 IT), zero falhas/erros/skips/Checkstyle; runtime e smoke aprovados. Commits T1 b16132b, T2 d2ca099, T3 0a5aba7, T4 8b4d0eb; T5 neste commit.
- **In-progress**: Verifier independente e sensor de discriminação; nenhuma implementação pendente conhecida.
- **Next step**: Validar MON-05 independentemente, atualizar backlog/rastreabilidade e executar validate_state. MON-06 coordenará agendamento/cancelamento e comparação de versão/geração; MON-07 aplicará resultado ao estado/incidente.
- **Blockers**: Nenhum local. CI remota não executada, push não realizado. Execução é contrato Java interno; não há scheduler/endpoint HTTP novo.
- **Uncommitted files**: Documentação/configuração T5 até este commit; relatório independente ainda não criado.
- **Branch**: main; baseline c5ad290. Banco de desenvolvimento preservado; smoke usa somente recursos descartáveis próprios.
