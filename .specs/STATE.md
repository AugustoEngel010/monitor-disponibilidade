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

### AD-008
- **Decision**: MON-06 reserva uma tentativa por monitor no PostgreSQL, com início lógico/horário vencido e snapshot de versão/geração. Na reserva, nextCheckAt=início lógico+I; sem fila de execuções e sem replay. Workers limitados a C=1..16 (padrão 4), polling 250 ms (positivo/configurável), uma instância. Pausa/edição interrompem worker na rodada seguinte, mantendo vaga até sua saída.
- **Reason**: Evitar sobreposição e trabalho ilimitado; preservar agendamento e identificar tentativas interrompidas após crash.
- **Trade-off**: Não coordena múltiplas instâncias; retomada exige instância anterior parada. Reserva órfã sem resultado recebe CANCELLED com duração desconhecida 0 e quebra sequências da geração corrente; resultado já persistido vence. Aplicação das observações ao estado/incidente continua MON-07 sob AD-002.
- **Scope**: MON-06 a MON-11; PROD-05/06/17/25/26/27/28/29; MON-06-AC01 a MON-06-AC08. Contratos de agendamento/frescor/pausa da MON-01 aplicados no pedido atual.
- **Date**: 2026-10-14
- **Status**: active

### AD-009
- **Decision**: MON-07 aplica o resultado bruto vencedor sob lock de monitor e deduplica por result_applications.execution_id. Estado, contadores, incidente, transi??o e inten??o congelada confirmam juntos. ScheduleStore aplica antes de remover reserva; recupera??o reaplica resultado existente ou CANCELLED. V5 marca resultados conclu?dos anteriores como HISTORY_ONLY, sem incidentes retroativos.
- **Reason**: Preservar evid?ncia, impedir transi??es parciais e permitir retry ap?s crash/rollback sem outro HTTP. Pausa e processamento s?o serializados; ?ndice parcial ?nico garante um incidente aberto por monitor.
- **Trade-off**: Inten??es PENDING j? existem, mas SMTP/retries ficam em MON-08. Marcadores distinguem hist?rico de aplica??o; reten??o MON-11 deve preservar refer?ncias. In?cio l?gico decide lacuna; resultado anterior ao ?ltimo in?cio aplicado da gera??o corrente fica somente hist?rico.
- **Scope**: MON-07 a MON-11; PROD-05/06/11 a PROD-20/28/29/30; MON-07-AC01 a MON-07-AC08.
- **Date**: 2026-10-14
- **Status**: active

## Handoff

- **Feature**: MON-07; `.specs/features/mon-07-incidentes/`.
- **Phase / Task**: Execute; T1?T5 conclu?das; Verifier independente pendente.
- **Completed**: M?quina de estados, V5, ?ndice ?nico/FKs, processador at?mico, marcador idempotente, inten??es dur?veis e integra??o de reservas/administra??o. 356 testes Docker (138 unit?rios/218 IT), 55 novos, zero falhas/erros/skips/Checkstyle. Commits T1 491d478, T2 5e61f87, T3 1a2e8da, T4 808e7c6.
- **In-progress**: Build runtime/smoke aprovados e documenta??o conclu?da; falta relat?rio independente/sensor.
- **Next step**: Delegar Verifier com sensor em c?pia isolada; somente ap?s PASS atualizar backlog e fechamento.
- **Blockers**: Nenhum local; Docker requer execu??o autorizada fora do sandbox.
- **Uncommitted files**: Documenta??o T5/STATE/README neste commit; nenhum c?digo Java pendente.
- **Branch**: main; baseline 2179a4b. Banco do operador preservado; PostgreSQLs de teste descart?veis. Logs `.validation/mon07-*.log`.
