# Monitor de disponibilidade — tasks do projeto

**Objetivo:** construir um monitor de APIs e sites que execute verificações HTTP, mantenha o histórico, identifique incidentes e envie avisos de queda e recuperação.

**Uso:** backlog para trabalhar com a skill tlc-spec-driven. Cada item representa uma entrega de produto ou engenharia. A skill deverá produzir a spec da entrega e, conforme a complexidade, o design e as tasks atômicas no repositório. As tasks abaixo ainda não são o arquivo de execução tasks.md gerado e validado pela skill.

**Status:** MON-02, MON-03 e MON-04 implementadas e validadas localmente em 2026-10-13. MON-01 permanece proposta documental; demais entregas planejadas. CI remota não observada.

## 1. Escopo inicial

- Java e Spring Boot, PostgreSQL e Flyway.
- Build, testes e execução por contêineres Docker. Maven e JDK ficam nos contêineres de build e testes; o ambiente local precisa de Docker e Docker Compose. A execução usa a imagem da aplicação.
- Monólito modular, com organização por domínio e limites claros entre módulos.
- Uma instância da aplicação e um único operador administrativo no MVP.
- Monitoramento HTTP/HTTPS por GET, com um código de resposta esperado por monitor.
- Configuração inicial sugerida: intervalo de 60 segundos, timeout de 5 segundos, 3 falhas consecutivas para confirmar queda e 2 sucessos consecutivos para confirmar recuperação.
- Histórico persistente de verificações, incidentes e tentativas de notificação.
- Um canal de notificação: e-mail. O ambiente de demonstração usará um servidor SMTP local de testes.
- Painel administrativo simples, servido pela própria aplicação.
- Docker Compose para executar a aplicação, o banco, o SMTP de testes e uma API de demonstração.

Essas escolhas são o ponto de partida para MON-01. Os limites de configuração e as regras de estado devem ficar explícitos na spec antes da implementação correspondente.

### Evoluções posteriores

Página pública de status; janelas de manutenção; múltiplos usuários e organizações; múltiplas instâncias do executor; verificações em diferentes regiões; alertas por outros canais; validação de conteúdo; monitoramento de certificados; monitoramento de jobs por heartbeat.

## 2. Ordem e dependências

As fases indicam a ordem recomendada. As dependências listadas são os pré-requisitos diretos de cada entrega.

| Fase | ID | Task | Depende de |
| --- | --- | --- | --- |
| Definição e base | MON-01 | Definir o produto e os contratos de comportamento | Nenhuma |
| Definição e base | MON-02 | Preparar a aplicação em Docker, o banco e a integração contínua | MON-01 |
| Administração | MON-03 | Proteger o acesso administrativo | MON-02 |
| Administração | MON-04 | Gerenciar o ciclo de vida dos monitores | MON-03 |
| Execução | MON-05 | Executar e registrar uma verificação HTTP | MON-04 |
| Execução | MON-06 | Agendar verificações com concorrência controlada | MON-05 |
| Incidentes | MON-07 | Identificar queda e recuperação e manter incidentes | MON-06 |
| Incidentes | MON-08 | Enviar notificações com histórico e novas tentativas | MON-07 |
| Consulta e interface | MON-09 | Consultar histórico, incidentes e resumo do monitor | MON-07 |
| Consulta e interface | MON-10 | Construir o painel administrativo | MON-08, MON-09 |
| Operação e portfólio | MON-11 | Instrumentar a operação e controlar a retenção de dados | MON-08, MON-09 |
| Operação e portfólio | MON-12 | Preparar a demonstração e a documentação do portfólio | MON-10, MON-11 |

## 3. Tasks detalhadas

### MON-01 — Definir o produto e os contratos de comportamento

**Objetivo:** estabelecer regras suficientes para desenvolver e verificar o monitor sem depender de decisões implícitas.

**Escopo:** visão do produto, MVP, limites, modelo conceitual, regras de transição e estratégia de testes. Descrever Monitor, Verificação, Incidente e Notificação, com suas responsabilidades. Registrar decisões arquiteturais que afetem mais de uma funcionalidade.

**Critérios de aceite:**

- [ ] MON-01-AC01: o documento de produto identifica o usuário, o problema, a entrega inicial e as evoluções posteriores.
- [ ] MON-01-AC02: estão definidos intervalo, timeout, códigos esperados, limites de configuração e quantidade máxima de verificações simultâneas.
- [ ] MON-01-AC03: estão definidos os estados de monitoramento, o estado inicial, as condições de queda e recuperação e o efeito de resultados internos ou cancelados.
- [ ] MON-01-AC04: estão definidos os efeitos de pausa, retomada, edição de destino ou política e reinício da aplicação, incluindo um monitor com incidente aberto.
- [ ] MON-01-AC05: estão definidos os horários registrados no incidente: primeira falha da sequência, confirmação da queda, início da sequência de recuperação e confirmação da recuperação.
- [ ] MON-01-AC06: estão definidos o limite de frescor de uma observação, a representação de períodos sem dados e o cálculo dos indicadores.
- [ ] MON-01-AC07: cada requisito funcional recebe um identificador e critérios verificáveis. Os testes futuros serão relacionados a esses identificadores.

**Verificação:** revisão dos cenários descritos com sequências de sucesso, falha, pausa e reinício. Registrar as decisões no contexto do projeto. Essa entrega documental não exige um teste automatizado que apenas repita seu texto.

**Conclusão:** produto e regras documentados, sem lacunas impeditivas para MON-02. Questões que alterem o comportamento devem ser resolvidas durante a especificação da entrega afetada.

### MON-02 — Preparar a aplicação em Docker, o banco e a integração contínua

**Objetivo:** fornecer uma base executável e uma rotina reproduzível de validação para as próximas entregas.

**Escopo:** projeto Spring Boot com Maven no contêiner de build, Dockerfile com etapas de build e execução, imagem da aplicação, conexão PostgreSQL, migrações Flyway, configuração por ambiente, organização dos módulos e workflow de CI. Usar Docker Compose para construir a imagem a partir do código e iniciar aplicação e banco. Preparar testes unitários e testes de integração com PostgreSQL real em ambiente isolado, por exemplo com Testcontainers, executados em um serviço de testes separado.

**Decisão de execução:** substituir o Maven Wrapper por build e execução via Docker. Remover mvnw, mvnw.cmd e os arquivos exclusivos de .mvn/wrapper; preservar pom.xml e outras configurações Maven necessárias. Registrar esta decisão nos artefatos de SDD já existentes que dependam do wrapper. Fixar versões compatíveis das imagens de build e execução conforme as decisões do projeto.

**Critérios de aceite:**

- [x] MON-02-AC01: a partir de um checkout limpo, um ambiente com Docker e Docker Compose constrói a imagem e inicia aplicação e banco com docker compose up --build e as variáveis previstas. A construção parte do código-fonte, sem exigir JAR compilado previamente ou JDK/Maven instalados no host.
- [x] MON-02-AC02: uma base vazia recebe as migrações necessárias. Reiniciar a aplicação não reaplica uma migração já concluída.
- [x] MON-02-AC03: o banco mantém os dados após reiniciar os serviços de desenvolvimento, usando volume persistente.
- [x] MON-02-AC04: os módulos têm responsabilidades documentadas e dependências coerentes com o desenho escolhido.
- [x] MON-02-AC05: a CI utiliza o fluxo de build e testes em contêineres e valida a construção da imagem da aplicação, as verificações de qualidade e os testes. Uma falha em qualquer etapa impede o resultado de sucesso; um build de imagem bem-sucedido não substitui o resultado da suíte de testes.
- [x] MON-02-AC06: o README registra os comandos reais para construir, iniciar, parar, consultar logs e executar testes com Docker, além das variáveis necessárias e da política de persistência. Nenhuma credencial real é versionada.
- [x] MON-02-AC07: mvnw, mvnw.cmd e os arquivos exclusivos do wrapper são removidos. Dockerfile, Compose, CI e documentação usam o fluxo em contêineres. A imagem de execução utiliza o runtime Java e o artefato da aplicação, com as ferramentas de build mantidas na etapa de construção.
- [x] MON-02-AC08: os testes podem ser executados pelo serviço de testes em contêiner, com saída diferente de zero em caso de falha e relatórios acessíveis. Testcontainers utiliza uma configuração documentada de acesso ao daemon Docker e de rede para o ambiente suportado; a imagem da aplicação não precisa desse acesso.

**Testes:** construção da imagem a partir do código e smoke test da aplicação em Compose; integração das migrações com PostgreSQL; reinício dos serviços preservando o volume; execução da suíte pelo serviço de testes. A estrutura deve permitir que os testes de comportamento sejam adicionados junto de cada funcionalidade. O fluxo de testes de integração utiliza um contêiner de testes com acesso ao daemon Docker e a configuração de rede necessária, separado da construção normal da imagem da aplicação.

**Conclusão:** validada localmente em 2026-10-13: checkout limpo, build sem cache, startup, migrações, seis testes e persistência após restart/down-up. Verificador independente PASS 8/8; dois sensores detectaram regressões. Evidências em `.specs/features/mon-02-base-docker/validation.md`. CI configurada e comandos locais validados; execução remota não observada.

### MON-03 — Proteger o acesso administrativo

**Objetivo:** restringir a configuração dos monitores e a consulta administrativa ao operador autenticado.

**Escopo:** autenticação administrativa simples, credenciais por configuração externa e proteção da API e do painel. HTTP Basic é a proposta inicial para manter o MVP de um único operador pequeno. Documentar como configurar e trocar a credencial.

**Critérios de aceite:**

- [x] MON-03-AC01: uma requisição sem credencial válida não acessa os recursos administrativos e recebe HTTP 401.
- [x] MON-03-AC02: uma credencial válida permite acesso aos recursos administrativos existentes.
- [x] MON-03-AC03: a proteção aplica-se também ao painel quando ele for criado, incluindo ações de configuração.
- [x] MON-03-AC04: uma configuração administrativa obrigatória ausente impede uma inicialização insegura, conforme o contrato de configuração.
- [x] MON-03-AC05: credenciais não aparecem em logs, respostas, exemplos preenchidos ou arquivos versionados.
- [x] MON-03-AC06: ações administrativas originadas no navegador têm proteção contra requisições forjadas de outra origem, conforme o mecanismo definido para o painel.

**Testes:** integração de acesso sem autenticação, com credencial inválida e com credencial válida; verificação da configuração obrigatória e da proteção das ações administrativas do navegador.

**Conclusão:** verificada localmente em 2026-10-13: um operador Basic obrigatório, fronteira global incluindo futuro painel/API, token CSRF em sessão, startup fechado, rotação e logs sem segredos. 48 testes sem falhas/skips; build runtime e smoke de prontidão/persistência da MON-02 aprovados. Verificador independente PASS 6/6 e oito mutações mortas. Evidências em `.specs/features/mon-03-acesso-administrativo/validation.md`; CI remota não observada.

### MON-04 — Gerenciar o ciclo de vida dos monitores

**Objetivo:** permitir cadastrar, consultar, editar, pausar e retomar os serviços monitorados.

**Escopo:** contrato HTTP de administração, persistência e validação de nome, URL, intervalo, timeout, código esperado, limites de confirmação e destinatário de alertas. Paginar a listagem. Aplicar o comportamento de pausa e edição definido em MON-01.

**Critérios de aceite:**

- [x] MON-04-AC01: um cadastro válido persiste a configuração e retorna HTTP 201 com o identificador do monitor.
- [x] MON-04-AC02: valores fora dos limites, timeout incompatível com o intervalo e URL com esquema diferente de HTTP/HTTPS recebem HTTP 400 com erros por campo.
- [x] MON-04-AC03: a URL administrativa não aceita credenciais embutidas. A política de destinos é complementada pela validação de execução em MON-05.
- [x] MON-04-AC04: consultas e alterações de identificador inexistente retornam HTTP 404.
- [x] MON-04-AC05: edição válida altera somente a configuração prevista, preservando o histórico existente e respeitando a regra para incidentes abertos.
- [x] MON-04-AC06: pausar ou retomar novamente um monitor já nessa condição é idempotente e preserva seu histórico.
- [x] MON-04-AC07: a listagem é paginada, tem ordenação estável e informa se o monitor está ativo ou pausado e se possui uma observação recente.

**Testes:** integração dos contratos administrativos, persistência e validações; testes unitários das regras de edição e ciclo de vida que forem implementadas nesta entrega.

**Conclusão:** validada localmente em 2026-10-13. API Basic/CSRF, configuração validada, persistência Flyway V2/PostgreSQL, edição com guard de incidente, pausa/retomada idempotentes e paginação estável. 160 testes sem falhas/erros/skips, qualidade/build/smoke Docker aprovados; verificador independente PASS 7/7 e seis sensores mortos. Evidências em `.specs/features/mon-04-ciclo-monitores/validation.md`. MON-06 completará integração com execução/cancelamento; MON-07 criará/processará incidentes reais. CI remota não executada.

### MON-05 — Executar e registrar uma verificação HTTP

**Objetivo:** transformar uma tentativa de consulta em um resultado persistido e classificável.

**Escopo:** executor HTTP por GET, timeout total, medição até o recebimento dos cabeçalhos, classificação de resultados e histórico de verificações. Receber uma configuração de monitor e um identificador de execução. Registrar sucesso, status inesperado, timeout, erro de DNS, conexão e TLS. Erros do próprio executor e cancelamentos têm classificação distinta das falhas externas.

**Critérios de aceite:**

- [ ] MON-05-AC01: o código esperado gera sucesso; outro código gera falha HTTP e preserva o status recebido.
- [ ] MON-05-AC02: uma requisição que excede o timeout é encerrada e registrada como timeout. Nenhuma tentativa pode bloquear o executor por tempo indefinido.
- [ ] MON-05-AC03: cada resultado informa o monitor, identificador da execução, início, término, duração, classificação e status HTTP quando disponível.
- [ ] MON-05-AC04: a consulta não persiste o corpo da resposta. Recursos de conexão são liberados ao concluir ou cancelar a tentativa.
- [ ] MON-05-AC05: destinos de rede proibidos são bloqueados também na resolução e conexão, de acordo com a política especificada. Redirecionamentos automáticos ficam desabilitados no MVP.
- [ ] MON-05-AC06: o ambiente de demonstração permite somente o destino interno de testes configurado explicitamente. Esse acesso não amplia automaticamente a política dos demais destinos.
- [ ] MON-05-AC07: um resultado rejeitado pela política de destino ou causado por erro interno é distinguido de uma indisponibilidade observada do serviço.
- [ ] MON-05-AC08: repetir a persistência do mesmo identificador de execução não cria um segundo resultado.

**Testes:** integração com servidor HTTP controlado para sucesso, status inesperado, resposta lenta e conexão interrompida; cenários de resolução e destinos bloqueados; persistência repetida do mesmo identificador.

**Conclusão:** uma tentativa produz um resultado durável e os recursos são liberados em todos os caminhos previstos.

### MON-06 — Agendar verificações com concorrência controlada

**Objetivo:** executar verificações periódicas sem sobreposição por monitor nem crescimento ilimitado de trabalho.

**Escopo:** selecionar monitores ativos com execução vencida, respeitar o limite de trabalho simultâneo e persistir o próximo horário previsto. A primeira versão terá uma única instância. Recuperar execuções interrompidas após reinício e tornar atrasos e períodos sem dados consultáveis.

**Critérios de aceite:**

- [ ] MON-06-AC01: monitores ativos elegíveis são executados conforme a política de agendamento. Monitores pausados não iniciam novas verificações.
- [ ] MON-06-AC02: novas rodadas do agendador não iniciam uma segunda execução enquanto o mesmo monitor tiver uma tentativa em andamento.
- [ ] MON-06-AC03: a quantidade de verificações em andamento respeita o limite configurado. O excedente permanece pendente sem criar uma fila ilimitada em memória.
- [ ] MON-06-AC04: um monitor lento ocupa somente sua parcela da capacidade; outros monitores continuam avançando.
- [ ] MON-06-AC05: reiniciar a aplicação mantém configuração, histórico, incidentes e próximo agendamento. Execuções interrompidas não ficam bloqueadas permanentemente.
- [ ] MON-06-AC06: um monitor vencido após reinício gera uma nova tentativa conforme a política, sem executar em rajada todas as verificações perdidas.
- [ ] MON-06-AC07: observações que ultrapassam o limite de frescor são apresentadas como sem dados recentes. A ausência de execução não é transformada em sucesso ou falha externa.
- [ ] MON-06-AC08: a interação entre pausa e uma tentativa já iniciada segue o contrato de MON-01, sem mudar o monitor para disponível por causa da pausa.

**Testes:** relógio controlável para seleção e atraso; integração de concorrência e reinício com estado persistido; servidor lento e servidor saudável executados no mesmo cenário. Evitar testes dependentes de esperas longas de relógio real.

**Conclusão:** execução periódica verificável, limitada e recuperável após reinício.

### MON-07 — Identificar queda e recuperação e manter incidentes

**Objetivo:** reconhecer transições confirmadas e manter um histórico consistente dos períodos de indisponibilidade observados.

**Escopo:** regras de falhas e sucessos consecutivos, transições de estado, abertura e encerramento de incidentes e persistência transacional. Preservar a relação entre resultado processado, alteração de estado e evento de transição.

**Critérios de aceite:**

- [ ] MON-07-AC01: com limite de 3 falhas, as duas primeiras iniciam a confirmação; a terceira abre um incidente e confirma a indisponibilidade.
- [ ] MON-07-AC02: um sucesso antes de atingir o limite interrompe a sequência de falhas e não abre um incidente.
- [ ] MON-07-AC03: novas falhas durante um incidente aberto não criam outro incidente para o mesmo monitor. Essa unicidade também é garantida na persistência.
- [ ] MON-07-AC04: com limite de 2 sucessos, o primeiro inicia a confirmação da recuperação; o segundo encerra o incidente. Uma falha entre eles reinicia a confirmação.
- [ ] MON-07-AC05: reprocessar o mesmo resultado não incrementa contadores nem produz uma segunda transição.
- [ ] MON-07-AC06: resultados internos, bloqueados ou cancelados e intervalos sem dados seguem o contrato especificado, sem produzir uma falsa queda ou recuperação.
- [ ] MON-07-AC07: contadores e incidentes persistem conforme a política de reinício. Pausa e retomada preservam o histórico e aplicam as regras de MON-01.
- [ ] MON-07-AC08: os horários de observação e confirmação são registrados separadamente. A criação ou o encerramento do incidente é atômico com a alteração de estado e o registro da transição.

**Testes:** unitários para todas as sequências descritas; integração de unicidade, reprocessamento, transação e retomada de estado persistido.

**Conclusão:** cada transição tem evidência no histórico, com um único incidente aberto por monitor.

### MON-08 — Enviar notificações com histórico e novas tentativas

**Objetivo:** avisar sobre queda e recuperação, preservando o trabalho de entrega quando o SMTP falhar.

**Escopo:** registrar uma intenção durável por transição e destinatário, realizar envio assíncrono, manter tentativas e aplicar espera progressiva e limite de tentativas. O envio não deve manter aberta a transação que processa a verificação. Documentar os estados de entrega e o tratamento de confirmação incerta.

**Critérios de aceite:**

- [ ] MON-08-AC01: confirmar uma queda gera uma intenção de notificação; confirmar a recuperação gera outra. Falhas repetidas durante o mesmo incidente não geram novos avisos de queda.
- [ ] MON-08-AC02: a intenção é persistida junto da transição. Uma falha ou reinício entre o registro e o envio mantém a entrega pendente.
- [ ] MON-08-AC03: repetir o processamento da mesma transição e destinatário mantém uma única intenção de notificação.
- [ ] MON-08-AC04: falha no SMTP registra a tentativa e agenda a próxima conforme a política. Ao atingir o limite, a entrega fica marcada como esgotada e consultável.
- [ ] MON-08-AC05: cada tentativa registra horário, resultado e erro sanitizado. As novas tentativas preservam o identificador da notificação.
- [ ] MON-08-AC06: o e-mail informa monitor, tipo de transição, horários relevantes e identificador do incidente. O aviso de recuperação informa a duração estimada conforme a regra da spec.
- [ ] MON-08-AC07: os avisos do mesmo incidente têm uma política explícita de ordem e esgotamento, incluindo o caso em que a recuperação ocorre enquanto o aviso de queda está pendente.

**Testes:** integração com SMTP de testes para queda e recuperação; SMTP indisponível, retomada, limite de tentativas, reprocessamento e reinício com entrega pendente.

**Conclusão:** as entregas podem ser acompanhadas e retomadas. A evidência de envio refere-se à aceitação pelo SMTP, com a semântica documentada na spec.

### MON-09 — Consultar histórico, incidentes e resumo do monitor

**Objetivo:** fornecer dados consistentes para explicar o estado atual e o comportamento observado no período escolhido.

**Escopo:** consultas paginadas de verificações, incidentes e notificações; filtros por período e resultado; resumo do monitor com última observação, frescor, incidente atual, taxa de sucesso das verificações e latência média dos sucessos.

**Critérios de aceite:**

- [ ] MON-09-AC01: a consulta de verificações aceita filtros e paginação com ordenação estável, incluindo casos com horários iguais.
- [ ] MON-09-AC02: a consulta de incidentes informa aberto ou encerrado, horários de observação e confirmação e duração estimada.
- [ ] MON-09-AC03: o resumo distingue monitor pausado, ainda sem observação, com dados antigos, em confirmação, disponível e indisponível, conforme o modelo especificado.
- [ ] MON-09-AC04: em uma janela com 8 sucessos e 2 falhas externas válidas, a taxa de sucesso das verificações é 80%. Erros internos, bloqueios e cancelamentos não entram nesse denominador.
- [ ] MON-09-AC05: uma janela sem verificações válidas retorna indicador sem dados, sem inventar um percentual. O contrato informa a quantidade de amostras e os períodos sem observação.
- [ ] MON-09-AC06: a latência média considera os sucessos com duração válida. Os limites da janela e o fuso utilizado no contrato estão documentados.
- [ ] MON-09-AC07: a taxa de sucesso das verificações é identificada como métrica por amostragem. A duração dos incidentes permanece um indicador separado.

**Testes:** integração das consultas e filtros; casos com página vazia e identificador inexistente; conjunto de dados controlado para percentuais, latência e períodos sem dados.

**Conclusão:** os indicadores têm definição e exemplo reproduzível, com consultas paginadas e testadas.

### MON-10 — Construir o painel administrativo

**Objetivo:** permitir entender e operar o monitor sem depender de chamadas manuais à API.

**Escopo:** painel simples, servido pela aplicação, com lista de monitores, formulário de configuração, pausa e retomada, página de detalhes e histórico de incidentes e entregas de notificação. Usar as mesmas regras de aplicação dos contratos administrativos.

**Critérios de aceite:**

- [ ] MON-10-AC01: a lista mostra nome, estado, última verificação e incidente aberto, distinguindo pausa e ausência de dados recentes.
- [ ] MON-10-AC02: o operador cadastra e edita um monitor e recebe mensagens de validação ligadas aos campos.
- [ ] MON-10-AC03: pausar e retomar atualiza a condição do monitor conforme o resultado da operação, sem simular uma recuperação.
- [ ] MON-10-AC04: a página de detalhes mostra resultados recentes, transições do incidente e estado das notificações, com paginação quando necessária.
- [ ] MON-10-AC05: o painel apresenta estados vazios, falhas de operação e dados antigos sem os representar como disponibilidade confirmada.
- [ ] MON-10-AC06: o painel é protegido pela autenticação administrativa e pode ser utilizado em tela pequena sem impedir as ações principais.

**Testes:** fluxos de interface para cadastro, erro de validação, edição, pausa, retomada e consulta de um incidente. Incluir a proteção das páginas administrativas.

**Conclusão:** um operador consegue executar o fluxo principal pelo painel.

### MON-11 — Instrumentar a operação e controlar a retenção de dados

**Objetivo:** permitir observar a saúde do próprio monitor e manter o volume de dados sob controle.

**Escopo:** health checks, métricas com Actuator e Micrometer, logs estruturados e política de retenção. Medir verificações em andamento, atrasos de agendamento, duração, falhas por classificação e entregas pendentes ou esgotadas. Usar um conjunto controlado de alvos para registrar a capacidade da versão.

**Critérios de aceite:**

- [ ] MON-11-AC01: o estado operacional informa quando o monitor não consegue usar uma dependência necessária, como o banco, conforme a distinção de saúde adotada no projeto.
- [ ] MON-11-AC02: métricas permitem verificar o limite de concorrência, atraso de execução e acúmulo de notificações sem usar URLs ou identificadores ilimitados como rótulos.
- [ ] MON-11-AC03: os logs permitem relacionar monitor, execução, incidente e notificação sem expor credenciais, corpos de resposta ou mensagens de erro sem sanitização.
- [ ] MON-11-AC04: a limpeza remove somente registros elegíveis pela política, preservando incidentes abertos e trabalho de notificação pendente.
- [ ] MON-11-AC05: consultas que cruzam um período já removido informam o limite de retenção; ausência de histórico não é convertida em sucesso.
- [ ] MON-11-AC06: um cenário de capacidade documenta quantidade de monitores, intervalos, respostas simuladas, recursos da máquina, duração e resultados medidos. O comportamento respeita os limites definidos em MON-01.

**Testes:** integração dos indicadores operacionais e retenção com relógio controlado; carga controlada para execução concorrente e atrasos. Registrar resultados reais, sem afirmar desempenho ainda não medido.

**Conclusão:** o próprio monitor tem sinais operacionais úteis, e a política de retenção é verificável.

### MON-12 — Preparar a demonstração e a documentação do portfólio

**Objetivo:** permitir que outra pessoa execute o projeto e veja os comportamentos que comprovam sua robustez.

**Escopo:** Docker Compose completo, API de demonstração com modos saudável, erro HTTP e resposta lenta, dados iniciais, roteiro de demonstração e README de apresentação. Documentar arquitetura, specs, decisões, testes e limitações da versão.

**Critérios de aceite:**

- [ ] MON-12-AC01: seguindo o README a partir de um checkout limpo, um ambiente com Docker e Docker Compose constrói e inicia a imagem da aplicação, PostgreSQL, SMTP de testes e API de demonstração com os comandos documentados.
- [ ] MON-12-AC02: a API de demonstração permite alternar entre sucesso, status inesperado e resposta lenta, sem alterar o código do monitor.
- [ ] MON-12-AC03: o roteiro demonstra sucesso, confirmação de queda, uma única abertura de incidente, notificação, confirmação de recuperação e encerramento.
- [ ] MON-12-AC04: o roteiro demonstra reinício com incidente aberto e envio pendente, seguido de retomada sem perda dos registros persistidos.
- [ ] MON-12-AC05: o roteiro mostra um alvo lento enquanto outro continua sendo verificado e mostra a diferença entre pausa, falha e período sem dados.
- [ ] MON-12-AC06: o README explica o produto, a arquitetura, como executar, como testar e onde consultar specs e decisões. Contém imagens ou uma gravação curta do fluxo real.
- [ ] MON-12-AC07: a documentação relaciona requisitos aos testes e inclui os resultados de capacidade medidos em MON-11.
- [ ] MON-12-AC08: os testes e verificações de cada entrega estão concluídos e a verificação final da skill tem evidências dos critérios de aceite. Publicação e hospedagem podem ser planejadas como uma entrega posterior.

**Testes:** execução do roteiro completo no ambiente Compose e verificação do caminho de recuperação. Esse roteiro complementa os testes que já foram feitos junto das funcionalidades.

**Conclusão:** a versão local é reproduzível e tem evidências visuais e técnicas para apresentação no GitHub.

## 4. Como levar o backlog à tlc-spec-driven

Começar por MON-01 e trabalhar uma entrega de cada vez. Usar as dependências e os critérios deste arquivo como entrada. A spec produzida no repositório será a referência para os testes e para as tasks atômicas de implementação.

Texto inicial para usar com o agente:

> Use a skill tlc-spec-driven para trabalhar no projeto Monitor de disponibilidade. Leia este backlog e comece por MON-01. Produza os requisitos verificáveis da entrega atual e registre as decisões necessárias. Nas entregas de implementação, derive os testes dos critérios de aceite e decomponha o trabalho conforme o fluxo da skill. Preserve os identificadores de origem para manter a rastreabilidade. Use os comandos reais de build e testes do repositório quando eles existirem. Mantenha o escopo na entrega atual.

### Regras de rastreabilidade

- Manter o ID MON-XX como origem da funcionalidade.
- Relacionar cada critério MON-XX-ACYY a um requisito da spec e aos testes que o verificam.
- Incluir os testes no trabalho que implementa o comportamento. A demonstração final não substitui essa verificação.
- Registrar alterações de escopo e comportamento na spec antes de ajustar a implementação correspondente.
- Gerar caminhos de código, comandos de teste e gates a partir do repositório criado, em vez de tratá-los como fatos já existentes neste backlog.

## 5. Referências de organização

Foram consultadas as instruções públicas da tlc-spec-driven do Tech Leads Club para distinguir planejamento de funcionalidades, especificação e decomposição em tasks de implementação. O conteúdo do projeto e seus critérios foram escritos para este monitor.

- Skill: https://github.com/tech-leads-club/agent-skills/blob/main/packages/skills-catalog/skills/%28development%29/tlc-spec-driven/SKILL.md
- Referência de tasks: https://github.com/tech-leads-club/agent-skills/blob/main/packages/skills-catalog/skills/%28development%29/tlc-spec-driven/references/tasks.md
- [Docker: build com múltiplas etapas](https://docs.docker.com/build/building/multi-stage/).
- [Testcontainers: execução de testes dentro de contêineres](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).

Fonte consultada em 8 de outubro de 2026. A versão instalada da skill no ambiente de implementação será a referência para seus próprios formatos e validadores.
