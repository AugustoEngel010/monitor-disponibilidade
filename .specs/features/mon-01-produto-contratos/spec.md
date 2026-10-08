# Monitor de disponibilidade: produto e contratos

**Origem:** MON-01, em [backlog](../../task/monitor-disponibilidade-tasks.md).
**Data:** 2026-10-08. **Complexidade:** Complex, pelo domínio novo e pelas transições persistentes.
**Status:** proposta especificada; aprovação de produto pendente. Nenhum comportamento foi implementado.
**Convenção:** SHALL significa DEVE. As palavras EARS permanecem em inglês para validação pela skill.

## Problem Statement

O usuário é um desenvolvedor que opera um pequeno conjunto de APIs e sites e precisa reconhecer falhas sem consultar cada serviço manualmente. O produto registra evidências HTTP, confirma quedas e recuperações e avisa por e-mail, distinguindo falhas do destino de falhas do próprio monitor.

O projeto também deve permitir que um avaliador de portfólio reproduza localmente os cenários e inspecione decisões e testes. O monitor mede observações pontuais; não prova disponibilidade contínua nem representa um serviço de monitoramento comercial.

## Goals

- Documentar os sete critérios MON-01-AC01 a MON-01-AC07 com regras e cenários reproduzíveis.
- Entregar um contrato de produto suficiente para iniciar MON-02 sem decisões comportamentais implícitas.
- Manter um MVP de instância única, operador único, até 100 monitores e notificações por e-mail.
- Preservar a origem de cada requisito para derivar testes e tasks nas entregas correspondentes.

## Produto e fronteira da entrega

O MVP permite autenticar um operador, cadastrar monitores, executar GETs periódicos, registrar verificações, confirmar incidentes, entregar alertas e consultar tudo num painel servido pela aplicação. PostgreSQL mantém o estado; Flyway controla migrações. Java e Spring Boot compõem um monólito modular. Docker Compose executará aplicação, banco, SMTP de testes e API de demonstração.

MON-01 entrega somente esta especificação, o contexto de decisões e a memória do projeto. O MVP será construído de MON-02 a MON-12. Versões de ferramentas, contratos detalhados de endpoints, esquema físico e bibliotecas serão definidos nas entregas que os implementam.

## Out of Scope

| Capacidade | Motivo / destino |
| --- | --- |
| Código, infraestrutura executável, testes automatizados e decomposição de implementação nesta entrega | Pertencem às entregas MON-02 a MON-12 |
| Página pública, organizações, múltiplos usuários ou executores distribuídos | Evolução posterior; o MVP tem um operador e uma instância |
| Manutenção programada, regiões distintas e outros canais de alerta | Evolução posterior |
| Validação de corpo, certificados como produto e heartbeat de jobs | O MVP verifica somente código HTTP por GET |
| Autenticação no destino, headers personalizados, POST e seguir redirects | Excluídos para conter a complexidade do executor |
| Exclusão de monitores e encerramento manual de incidentes | Pausa preserva o histórico; evolução posterior |
| SLA, percentual de uptime temporal e garantias de entrega na caixa de entrada | A versão mede amostras e aceitação SMTP |
| Hospedagem, publicação remota, Kafka e microserviços | Demonstração local e monólito modular |

## Assumptions & Open Questions

As escolhas do backlog são baseline. As demais linhas são recomendações adotadas para tornar a proposta revisável, sem atribuir concordância ao usuário. A aprovação desta spec ratifica essas premissas; alterações devem atualizar cenários e rastreabilidade antes de implementação.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Stack e implantação | Java, Spring Boot, PostgreSQL, Flyway; monólito modular; uma instância; Compose local | Escopo solicitado e backlog | Sim, instrução inicial |
| Configuração e capacidade | Tabela de limites abaixo; até 100 monitores; concorrência padrão 4, máximo 16 | Limitar consumo e tornar erros verificáveis, sem prometer capacidade medida | Não, premissa A-01 |
| Pausa e incidente aberto | Preservar incidente; proibir mudanças de URL e política durante incidente | Não inventar recuperação nem misturar destinos no mesmo incidente | Não, premissa A-02; pergunta apresentada |
| Lacunas entre resultados | Zerar confirmações ao perder frescor, sem encerrar incidente | Evitar confirmar uma sequência com evidências distantes | Não, premissa A-03; pergunta apresentada |
| Estado inicial e primeira observação | UNKNOWN; um sucesso torna UP; falhas precisam de confirmação | Evitar declarar disponível antes de observar | Não, premissa A-04 |
| Agendamento | Próxima tentativa = início da tentativa anterior + intervalo; sem replay de rodadas perdidas | Limitar atrasos e trabalho acumulado | Não, premissa A-05 |
| Falha interna e cancelamento | Não contar como falha externa; interromper ambas as sequências | Confirmação exige evidências consecutivas válidas | Não, premissa A-06 |
| Notificações | Cinco tentativas; espera 30, 60, 120 e 240 segundos; queda precede recuperação | Política finita e demonstrável | Não, premissa A-07 |
| Indicadores e tempo | UTC, janela semiaberta, taxa por amostras; frescor = 2 × intervalo + timeout | Reprodutibilidade e distinção explícita entre amostra e ausência | Não, premissa A-08 |
| Segurança de destinos | HTTP/HTTPS públicos; exceção restrita à API de demonstração; sem segredos na URL | Reduzir exposição de rede e dados sem remover demonstração local | Não, premissa A-09 |
| Retenção | 30 dias; preservar incidentes abertos e trabalho pendente com suas evidências | Controlar disco sem destruir trabalho em andamento | Não, premissa A-10 |

**Open questions:** none — todas as ambiguidades desta entrega têm interpretação e justificativa registradas acima. Ratificação das premissas e aprovação de produto continuam pendentes. Detalhes de implementação são adiados com dono explícito, no contexto.

## Modelo conceitual e responsabilidades

| Conceito | Responsabilidade e dados conceituais |
| --- | --- |
| Monitor | Identidade, nome, URL, política, destinatário, ativo/pausado, versão da configuração, próximo horário, estado confirmado e sequências de confirmação |
| Verificação | Uma execução identificada unicamente; monitor e versão da configuração; início, término UTC, duração monotônica, resultado, status HTTP opcional e erro sanitizado; sem corpo |
| Incidente | Um período observado de falhas, associado ao monitor e à versão do destino/política; primeira falha, confirmação de queda, início de recuperação, confirmação de recuperação; aberto ou encerrado |
| Transição | Evidência de abertura/encerramento ligada à verificação causadora e ao incidente; chave estável para deduplicação |
| Notificação | Uma intenção por transição e destinatário, com conteúdo congelado, estado de entrega, próximo envio e histórico de tentativas |

Limites conceituais dos módulos: Administração controla configuração e acesso; Monitoramento agenda e produz verificações; Incidentes aplica resultados e produz transições; Notificações entrega intenções; Consultas compõe histórico e indicadores. Dados pertencem ao módulo responsável. Outros módulos usam contratos explícitos, sem alterar diretamente dados alheios. O desenho físico das dependências pertence a MON-02.

## Configuração e validação

| Campo | Padrão | Limite / regra |
| --- | --- | --- |
| Nome | Obrigatório | 1–100 caracteres após trim; nomes duplicados permitidos |
| URL | Obrigatória | Absoluta HTTP/HTTPS; até 2048 caracteres; host obrigatório; porta explícita 1–65535; sem userinfo, fragmento ou query |
| Intervalo I | 60 s | Inteiro, 10–3600 s |
| Timeout total T | 5 s | Inteiro, 1–30 s, estritamente menor que I |
| Código esperado | 200 | Um inteiro 200–599, comparação exata |
| Falhas para queda F | 3 | Inteiro, 1–10 |
| Sucessos para recuperação R | 2 | Inteiro, 1–10 |
| Destinatário | Obrigatório | Um endereço de e-mail, até 254 caracteres, sem display name ou múltiplos destinatários; validação sintática não garante existência |
| Quantidade de monitores | — | Até 100 cadastrados, incluindo pausados |
| Concorrência global C | 4 | Inteiro 1–16; configurado ao iniciar aplicação |

A query é excluída para impedir armazenamento acidental de tokens; endpoints que dependem de query ficam fora do MVP. A gramática exata de e-mail e a normalização de URL serão fixadas em MON-04 com exemplos aceitos/rejeitados.

O timeout inclui resolução, conexão, TLS e recebimento de cabeçalhos. A latência termina nos cabeçalhos, sem aguardar ou persistir o corpo. Redirects não são seguidos: um 301 esperado é sucesso; um 301 quando se espera 200 é falha HTTP.

## Estados, evidência e transições

Há três dimensões independentes: ciclo de vida ACTIVE/PAUSED; estado confirmado UNKNOWN/UP/DOWN; evidência NEVER_OBSERVED/FRESH/STALE. O incidente aberto implica DOWN. Confirmar queda a partir de UNKNOWN é permitido.

Resultados externos válidos: SUCCESS (código esperado), HTTP_FAILURE (outro código), TIMEOUT, DNS_FAILURE, CONNECTION_FAILURE e TLS_FAILURE. Resultados não externos: INTERNAL_ERROR, DESTINATION_BLOCKED e CANCELLED. Somente resultados externos da versão corrente e elegíveis para processamento atualizam a última observação e as sequências. Todos os resultados persistidos permanecem no histórico.

Frescor L = 2 × I + T. Uma observação é recente se 0 ≤ agora − término ≤ L. Lacuna ocorre quando o início da próxima tentativa é estritamente posterior a término da última observação + L. Não se considera o tempo que a própria tentativa levou como lacuna anterior. A comparação usa a configuração vigente da mesma versão.

| Estado / contexto | Resultado elegível | Efeito |
| --- | --- | --- |
| UNKNOWN ou UP | Falha externa | Incrementar falhas; zerar sucessos; em F falhas abrir incidente e tornar DOWN |
| UNKNOWN ou UP | SUCCESS | Zerar falhas; tornar/manter UP sem criar recuperação |
| DOWN | Falha externa | Zerar recuperação; manter o mesmo incidente |
| DOWN | SUCCESS | Incrementar recuperação; em R sucessos encerrar incidente e tornar UP |
| Qualquer estado | Resultado não externo | Zerar ambas as sequências e seus horários provisórios; preservar estado e incidente |
| Qualquer estado | Lacuna antes da tentativa | Zerar sequências antes de aplicar resultado; preservar estado e incidente |
| Qualquer estado | Resultado duplicado | Nenhuma reaplicação de efeito |
| PAUSED ou versão antiga | Resultado tardio | Histórico somente; sem efeito no estado, incidente ou notificação |

Contadores são saturados no limiar; não crescem indefinidamente. O primeiro sucesso em UNKNOWN é uma observação de disponibilidade, sem e-mail de recuperação. Com F=1/R=1, observação e confirmação coincidem.

Pausar é idempotente: impedir novos inícios, invalidar a tentativa em andamento e solicitar cancelamento. Se a tentativa já tiver terminado, seu resultado real pode ser registrado; se cancelada, registrar CANCELLED. Em ambos os casos, se não foi aplicado antes da pausa, seu resultado não muda o estado. A ordem entre processamento e pausa é serializada por monitor. Pausar zera sequências, preserva estado confirmado e incidente e encerra a cobertura de observação.

Retomar é idempotente: manter incidente/estado, zerar sequências e tornar uma tentativa elegível imediatamente. A evidência só volta a ser recente depois de uma nova observação válida, mesmo que a observação anterior ainda estivesse dentro de L.

Editar nome/destinatário preserva sequências, versão do destino/política, estado e histórico. Intenções já criadas preservam o destinatário original. Uma mudança efetiva de URL, I, T, código esperado, F ou R constitui nova versão: sem incidente aberto, cancelar/inutilizar tentativa anterior, zerar sequências, tornar UNKNOWN e sem observação corrente; se ativo, elegível imediatamente; se pausado, esperar retomada. Com incidente aberto, rejeitar essa mudança com HTTP 409. Reenviar os mesmos valores não constitui nova versão.

Reinício mantém configurações, estado, contadores, incidentes, notificações e próximos horários persistidos. Uma execução interrompida é finalizada como CANCELLED e quebra confirmações; sem execução interrompida, contadores sobrevivem apenas se não houve lacuna. O monitor ativo vencido executa uma nova tentativa, sem reproduzir cada rodada perdida. O monitor pausado permanece pausado.

Para distribuir vagas, selecionar o menor próximo horário previsto, com ID do monitor como desempate estável. Enquanto a tentativa não iniciar, o horário vencido não avança. Isso evita que uma rodada do agendador sempre favoreça os mesmos monitores; a capacidade real ainda será medida em MON-11.

## Horários, frescor e indicadores

O término da verificação é o instante de observação. No incidente: `firstFailureAt` é o término da primeira falha da sequência que confirmou queda; `downConfirmedAt` é o término da F-ésima falha; `recoveryStartedAt` é o término do primeiro sucesso da sequência corrente; `recoveredAt` é o término do R-ésimo sucesso. Falha, lacuna, pausa ou resultado não externo durante recuperação limpa `recoveryStartedAt`; o horário final é o da sequência que efetivamente encerrou o incidente.

Duração estimada de um incidente encerrado = `recoveryStartedAt − firstFailureAt`. Para um incidente aberto, idade desde a primeira falha = `agora − firstFailureAt`, explicitamente sem duração final. Os dois valores incluem lacunas e pausas e não provam indisponibilidade contínua. Também são expostos os instantes de confirmação. UTC é o contrato de armazenamento/consulta; apresentação local converte sem alterar os instantes. Duração HTTP usa relógio monotônico, separado dos timestamps.

Na janela [a,b), com a < b e b ≤ agora, incluir verificações pelo término. Taxa de sucesso = 100 × SUCCESS / (SUCCESS + falhas externas). Cancelamentos, bloqueios e erros internos não entram no denominador. Latência média = soma de durações válidas dos sucessos / quantidade desses sucessos. Denominador zero retorna `null` e zero amostras; arredondar somente a apresentação, com duas casas decimais.

Para períodos sem observação: cada observação externa válida elegível fornece cobertura [término,término+L), cortada pela pausa ou mudança de versão seguinte. A cobertura não atravessa retomada sem nova observação. Dentro de [a,b), unir as coberturas, inclusive as iniciadas antes de a, e devolver seu complemento como intervalos sem dados. O limite pontual de frescor em término+L é recente, embora esse ponto isolado tenha duração zero no cálculo de cobertura. Retenção removida é informada separadamente de lacuna de execução.

O resumo expõe sempre ciclo de vida, estado confirmado, evidência, contadores e incidente. Rótulo principal, em ordem: PAUSED; SEM_DADOS (nunca observado na versão/período ativo corrente); SEM_DADOS_RECENTES (stale); CONFIRMANDO_QUEDA; CONFIRMANDO_RECUPERACAO; UP; DOWN. Um monitor stale ou pausado pode continuar com incidente aberto visível.

## User Stories

### P1: operar alvos sem perder a identidade do histórico

**User Story:** Como operador, quero configurar e suspender verificações para controlar os alvos sem fabricar recuperação.
**Independent Test:** Revisar cadastros nos limites e os cenários C-08 a C-11; implementar os testes correspondentes em MON-03/MON-04/MON-06.

**Acceptance Criteria**:
1. The sistema SHALL restringir administração ao operador autenticado, incluindo API e painel (PROD-01).
2. WHEN um cadastro respeitar a tabela de configuração THEN o sistema SHALL aceitar a configuração (PROD-02).
3. IF um campo violar a tabela de configuração THEN o sistema SHALL rejeitar a operação com HTTP 400 e erro por campo (PROD-03).
4. IF o cadastro exceder 100 monitores THEN o sistema SHALL rejeitar a operação com HTTP 409 (PROD-04).
5. WHEN um monitor for pausado THEN o sistema SHALL aplicar o contrato de pausa sem fechar incidente aberto (PROD-05).
6. WHEN um monitor pausado for retomado THEN o sistema SHALL aplicar o contrato de retomada com nova evidência obrigatória (PROD-06).
7. WHEN nome ou destinatário forem editados THEN o sistema SHALL preservar o estado de monitoramento (PROD-07).
8. WHEN destino ou política forem alterados sem incidente aberto THEN o sistema SHALL iniciar a nova versão conforme o contrato de edição (PROD-08).
9. IF destino ou política forem alterados com incidente aberto THEN o sistema SHALL rejeitar a edição com HTTP 409 (PROD-09).

### P1: reconhecer queda e recuperação por evidências consecutivas

**User Story:** Como operador, quero confirmar transições para evitar alertas de falhas isoladas.
**Independent Test:** Aplicar as sequências C-01 a C-07 a um modelo puro com relógio controlado; posteriormente verificar persistência em MON-07.

**Acceptance Criteria**:
1. WHEN um monitor for criado THEN o sistema SHALL iniciar em ACTIVE, UNKNOWN e NEVER_OBSERVED (PROD-10).
2. WHEN ocorrer um sucesso elegível em UNKNOWN ou UP THEN o sistema SHALL estabelecer UP sem evento de recuperação (PROD-11).
3. WHEN ocorrerem F falhas externas consecutivas elegíveis em UNKNOWN ou UP THEN o sistema SHALL abrir um incidente e confirmar DOWN na F-ésima (PROD-12).
4. WHEN ocorrer sucesso antes de F falhas THEN o sistema SHALL zerar a sequência de falhas (PROD-13).
5. WHILE houver incidente aberto o sistema SHALL manter no máximo um incidente aberto por monitor (PROD-14).
6. WHEN ocorrerem R sucessos consecutivos elegíveis em DOWN THEN o sistema SHALL encerrar o incidente e confirmar UP no R-ésimo (PROD-15).
7. WHEN ocorrer falha externa durante confirmação de recuperação THEN o sistema SHALL zerar a sequência de recuperação (PROD-16).
8. WHEN ocorrer resultado não externo ou lacuna THEN o sistema SHALL interromper as sequências sem alterar o estado confirmado (PROD-17).
9. WHEN um resultado já aplicado for reprocessado THEN o sistema SHALL manter contadores e transições inalterados (PROD-18).
10. WHEN um incidente abrir ou encerrar THEN o sistema SHALL registrar os quatro horários conforme o contrato temporal (PROD-19).
11. WHEN uma transição for persistida THEN o sistema SHALL confirmar resultado aplicado, estado, incidente, transição e intenção de notificação na mesma transação (PROD-20).

### P1: executar consultas limitadas e recuperar a execução local

**User Story:** Como operador, quero que um alvo lento não impeça os demais e que reinícios não percam trabalho persistido.
**Independent Test:** Servidor controlado para classificações; C-12/C-13 para reinício e concorrência, implementados em MON-05/MON-06.

**Acceptance Criteria**:
1. WHEN uma tentativa HTTP terminar THEN o sistema SHALL registrar um único resultado por ID de execução com os dados do modelo conceitual (PROD-21).
2. WHEN os cabeçalhos forem recebidos THEN o sistema SHALL classificar o código por igualdade exata sem seguir redirects (PROD-22).
3. IF a tentativa exceder T segundos THEN o sistema SHALL cancelar a operação e classificá-la como TIMEOUT (PROD-23).
4. IF a política de rede proibir o destino THEN o sistema SHALL registrar DESTINATION_BLOCKED sem tentar o acesso proibido (PROD-24).
5. WHILE houver tentativas em andamento o sistema SHALL respeitar simultaneamente C global e uma tentativa por monitor (PROD-25).
6. WHEN houver monitor vencido sem capacidade THEN o sistema SHALL mantê-lo pendente no estado persistente sem fila ilimitada em memória (PROD-26).
7. WHEN uma tentativa começar THEN o sistema SHALL programar a próxima para início + I, sem executar rodadas perdidas em rajada (PROD-27).
8. WHEN a aplicação reiniciar THEN o sistema SHALL recuperar o estado conforme o contrato de reinício (PROD-28).
9. WHEN chegar um resultado invalidado por pausa ou versão THEN o sistema SHALL preservá-lo somente no histórico (PROD-29).

### P1: receber e consultar avisos com semântica explícita

**User Story:** Como operador, quero acompanhar alertas e indicadores para entender tanto o destino quanto as limitações das observações.
**Independent Test:** C-14/C-15 com dados e SMTP controlados, nas entregas MON-08/MON-09/MON-11.

**Acceptance Criteria**:
1. WHEN uma transição confirmada ocorrer THEN o sistema SHALL criar uma única intenção por transição e destinatário (PROD-30).
2. IF o SMTP falhar THEN o sistema SHALL registrar tentativa e aplicar a política finita de retentativas (PROD-31).
3. WHEN uma recuperação tiver aviso de queda pendente THEN o sistema SHALL aguardar aceitação ou esgotamento do aviso de queda antes de enviar a recuperação (PROD-32).
4. WHEN um aviso for enviado THEN o sistema SHALL incluir nome, URL, tipo, ID do incidente e horários pertinentes; recuperação inclui duração estimada (PROD-33).
5. WHEN um resumo for consultado THEN o sistema SHALL distinguir ciclo de vida, estado, confirmação e frescor segundo a ordem de rótulos definida (PROD-34).
6. WHEN indicadores forem consultados THEN o sistema SHALL calcular taxa de sucesso e latência conforme a janela e os denominadores definidos (PROD-35).
7. WHEN a janela não estiver inteiramente coberta THEN o sistema SHALL retornar os períodos sem dados e a limitação de retenção separadamente (PROD-36).
8. WHEN a limpeza de retenção executar THEN o sistema SHALL preservar incidentes abertos, intenções pendentes e suas evidências necessárias (PROD-37).

## Entrega de notificações e segurança

Notificação passa por PENDING, SENDING, ACCEPTED ou EXHAUSTED. ACCEPTED indica resposta positiva do SMTP, não recebimento pelo usuário. No máximo cinco tentativas; após falhas 1–4, próximo início = término da tentativa + 30/60/120/240 s. Após a quinta falha, EXHAUSTED. Se cair durante envio, registrar confirmação incerta e retomar como pendente, consumindo a tentativa iniciada; se era a quinta, esgotar. Pode haver entrega duplicada quando a aceitação ocorreu mas não foi persistida. A mesma intenção mantém ID e Message-ID estáveis. Não se promete exactly-once SMTP.

Recuperação só fica elegível quando a queda do mesmo incidente estiver ACCEPTED ou EXHAUSTED. Esgotamento não bloqueia recuperação indefinidamente. Cada aviso contém horário da transição para explicar entrega atrasada. Outros incidentes não aguardam esta cadeia. Envios são assíncronos e fora da transação de domínio; falha de SMTP não impede novas verificações. Destinatário e conteúdo são congelados na criação de cada intenção.

Autenticação administrativa proposta: HTTP Basic com configuração externa obrigatória; ações no navegador protegidas contra CSRF. No ambiente local, portas administrativas ficam restritas ao host. Política de execução permite endereços públicos HTTP/HTTPS, bloqueia loopback, privados, link-local, multicast, não especificados e metadados de infraestrutura, inclusive em resolução/conexão e IPv6. A única exceção é o serviço/porta da API de demonstração configurados explicitamente no perfil local; a exceção não libera a sub-rede inteira. MON-05 deverá especificar e provar a proteção contra mudança de resolução entre validação e conexão.

Não persistir corpos, credenciais ou erros brutos. Logs têm IDs de monitor/execução/incidente/notificação; métricas usam rótulos de cardinalidade limitada. Retenção padrão de 30 dias usa término para verificações, encerramento para incidentes e término para notificações terminais; proteger os registros ainda referenciados pelo estado corrente ou trabalho pendente. A base registra o limite de histórico efetivamente disponível. Detalhes de limpeza ficam em MON-11.

## Edge Cases

Os cenários usam F=3/R=2, salvo indicação. S=sucesso; F=falha externa; X=resultado não externo. Cada passo inclui persistência e processamento do resultado; timestamps abaixo são términos, salvo menção ao início.

| Cenário | Entrada / condição | Resultado esperado / requisitos |
| --- | --- | --- |
| C-01 | Novo monitor; S | UP; zero incidentes e zero avisos; PROD-10/11 |
| C-02 | UNKNOWN; F,F,F,F | Confirmação nas duas primeiras; DOWN na terceira; um incidente e uma intenção de queda; PROD-12/14/30 |
| C-03 | UP; F,F,S,F | Após S falhas=0; último F deixa falhas=1, sem incidente; PROD-13 |
| C-04 | DOWN; S,F,S,S | Primeiro S inicia recuperação; F apaga início; dois últimos S encerram; PROD-15/16/19 |
| C-05 | F=1/R=1; F,S | Um incidente fechado; observação e confirmação coincidem em cada transição; PROD-12/15/19 |
| C-06 | UP; F,F,X,F | X interrompe sequência; último F deixa falhas=1; não abre incidente; PROD-17 |
| C-07 | DOWN; S em t=0; próximo início em t=126, I=60/T=5 | L=125; lacuna zera recuperação; próximo S conta 1; incidente permanece aberto; PROD-17 |
| C-08 | DOWN com recuperação=1; pausa; retomada; S | Incidente preservado; após retomada e S, recuperação=1; sem recuperação fictícia; PROD-05/06 |
| C-09 | Tentativa em andamento; pausa; resposta S tardia | Histórico registra resposta ou cancelamento; estado e incidente inalterados pela resposta tardia; PROD-29 |
| C-10 | Sem incidente; URL muda durante tentativa | UNKNOWN, sem observação corrente; resposta antiga só histórico; configuração nova elegível; PROD-08/29 |
| C-11 | Incidente aberto; tentar mudar I ou URL; depois editar nome | Primeira operação 409 sem alteração; nome aceito sem fechar incidente; PROD-07/09 |
| C-12 | Reinício com incidente aberto e recuperação=1; tentativa interrompida | Tentativa CANCELLED; recuperação=0; incidente preservado; monitor vencido elegível uma vez; PROD-28 |
| C-13 | C=2; três monitores vencidos; um lento | No máximo dois em andamento, nenhum monitor sobreposto; terceiro permanece pendente e avança quando há vaga; PROD-25/26 |
| C-14 | Janela com 8 S (100 ms cada), 2 F, 1 X | Taxa=80%, 10 amostras externas, média=100 ms com 8 amostras de latência; janela só X retorna null/zero; PROD-35 |
| C-15 | Queda pendente; recuperação; SMTP falha cinco vezes | Queda EXHAUSTED; recuperação então elegível; verificações continuam; PROD-31/32 |
| C-16 | I=60/T=5; observação em t=0; janela [0,300); próxima observação t=200 | Coberturas [0,125) e [200,300); lacuna [125,200); em t=125 frescor pontual ainda recente; PROD-34/36 |
| C-17 | Falhas em 0,60,120; sucessos em 180,240 | Incidente: primeira falha=0, queda confirmada=120, início recuperação=180, recuperação=240; duração estimada=180 s; PROD-19/33 |
| C-18 | Processar mesma terceira falha duas vezes; falha transacional ao abrir | Repetição não duplica contador/incidente/intenção; rollback não deixa transição parcial e permite reaplicar; PROD-18/20 |
| C-19 | Reinício sem tentativa interrompida, com 2 falhas; próximo início dentro de L | Contadores preservados; próxima falha confirma queda; fora de L, conta somente 1; PROD-17/28 |
| C-20 | Observação em 0; pausa em 30; retomada em 40; nova observação em 60; janela [0,100) | Coberturas [0,30), [60,100); lacuna [30,60); incidente aberto continua visível; PROD-05/06/36 |
| C-21 | I=10/T=9, código=599; depois T=10; depois 101º monitor | Primeira configuração aceita; T=10 rejeitado 400; cadastro excedente rejeitado 409; PROD-02/03/04 |
| C-22 | Esperado 200, recebido 301; destino proibido; operação excede T | HTTP_FAILURE sem redirect; DESTINATION_BLOCKED não conta falha; TIMEOUT conta falha externa; PROD-22/23/24 |
| C-23 | Envio cai após possível aceitação, na tentativa 2 | Intenção volta a PENDING, 2 tentativas consumidas; nova tentativa usa mesmo ID; possível duplicação declarada; PROD-31 |

## Estratégia de testes e dimensões implícitas

MON-01 é revisada por cenários e pelo validador estrutural; não terá teste que apenas procure frases nesta documentação. Nas entregas seguintes, testes derivam de resultados esperados dos critérios, antes das tasks atômicas. Nenhum teste está executado ou verificado nesta fase.

| Dimensão | Cobertura / próxima entrega |
| --- | --- |
| Validação e limites | PROD-02/03/04; testes de fronteira e contratos em MON-04 |
| Falha e persistência parcial | PROD-17/20/28; rollback e reinício em MON-06/MON-07 |
| Idempotência e retentativa | PROD-18/21/30/31; duplicação e SMTP em MON-05/MON-07/MON-08 |
| Acesso e limite de requisições | PROD-01; Basic/CSRF em MON-03; throttling administrativo N/A no MVP local de operador único |
| Concorrência e ordem | PROD-25/26/29/32; concorrência real controlada em MON-06/MON-08 |
| Ciclo de dados e expiração | PROD-34/36/37; relógio controlado e retenção em MON-09/MON-11 |
| Observabilidade | Logs/limites já contratados; evidência operacional em MON-11 |
| Dependências externas | PROD-23/24/31; HTTP/SMTP controlados; indisponibilidade do banco não vira falha do destino |
| Integridade de transições | PROD-10 a PROD-20; tabela de sequências e PostgreSQL real em MON-07 |
| Pagamentos | N/A: não existe cobrança no produto |

Testes unitários cobrem modelo de estados e relógio injetável. Integração com PostgreSQL real cobre migração, unicidade, atomicidade e reinício; servidor HTTP controlado cobre timeout/classificações; SMTP local cobre entrega e incerteza. Testes de contratos verificam API/autenticação; fluxos do painel ficam em MON-10. O roteiro Compose de MON-12 complementa a suite. Não usar sleeps longos para regras temporais; timeouts reais do executor exigem testes de integração limitados.

Cada entrega cria sua matriz requisito → critério → teste real → task → evidência. Tasks levam Tests, Gate e dependências, com um commit atômico por task; o verificador independente da skill é aplicado ao encerrar execução. Caminhos de teste, comandos e versões vêm da aplicação quando existir, sem inventá-los nesta spec.

## Requirement Traceability

IDs PROD-NN são únicos neste contrato. Critérios de origem MON-01-ACNN permanecem intactos; a coluna de implementação indica dono futuro, não execução já realizada.

| Requirement ID | Story | Origem MON-01 | Implementação futura | Cenário / teste futuro | Status |
| --- | --- | --- | --- | --- | --- |
| PROD-01 | Operar | AC01, AC07 | MON-03, MON-10 | Acesso/CSRF | Pending |
| PROD-02 | Operar | AC02, AC07 | MON-04 | C-21, fronteiras | Pending |
| PROD-03 | Operar | AC02, AC07 | MON-04 | C-21, inválidos | Pending |
| PROD-04 | Operar | AC02, AC07 | MON-04 | C-21 | Pending |
| PROD-05 | Operar | AC04, AC07 | MON-04, MON-06, MON-07 | C-08/09/20 | Pending |
| PROD-06 | Operar | AC04, AC07 | MON-04, MON-06, MON-07 | C-08/20 | Pending |
| PROD-07 | Operar | AC04, AC07 | MON-04 | C-11 | Pending |
| PROD-08 | Operar | AC04, AC07 | MON-04, MON-06 | C-10 | Pending |
| PROD-09 | Operar | AC04, AC07 | MON-04 | C-11 | Pending |
| PROD-10 | Reconhecer | AC03, AC07 | MON-04, MON-07 | C-01 | Pending |
| PROD-11 | Reconhecer | AC03, AC07 | MON-07 | C-01 | Pending |
| PROD-12 | Reconhecer | AC03, AC07 | MON-07 | C-02/05 | Pending |
| PROD-13 | Reconhecer | AC03, AC07 | MON-07 | C-03 | Pending |
| PROD-14 | Reconhecer | AC03, AC07 | MON-07 | C-02, unicidade SQL | Pending |
| PROD-15 | Reconhecer | AC03, AC07 | MON-07 | C-04/05 | Pending |
| PROD-16 | Reconhecer | AC03, AC07 | MON-07 | C-04 | Pending |
| PROD-17 | Reconhecer | AC03, AC06, AC07 | MON-06, MON-07 | C-06/07/19 | Pending |
| PROD-18 | Reconhecer | AC03, AC07 | MON-07 | C-18 | Pending |
| PROD-19 | Reconhecer | AC05, AC07 | MON-07 | C-04/17 | Pending |
| PROD-20 | Reconhecer | AC03, AC05, AC07 | MON-07, MON-08 | C-18, rollback | Pending |
| PROD-21 | Executar | AC01, AC03, AC07 | MON-05 | Persistência duplicada | Pending |
| PROD-22 | Executar | AC02, AC03, AC07 | MON-05 | C-22 | Pending |
| PROD-23 | Executar | AC02, AC03, AC07 | MON-05 | C-22, timeout total | Pending |
| PROD-24 | Executar | AC03, AC07 | MON-05 | C-22, rede | Pending |
| PROD-25 | Executar | AC02, AC07 | MON-06 | C-13 | Pending |
| PROD-26 | Executar | AC02, AC06, AC07 | MON-06 | C-13 | Pending |
| PROD-27 | Executar | AC02, AC04, AC07 | MON-06 | C-12, relógio | Pending |
| PROD-28 | Executar | AC04, AC07 | MON-06, MON-07 | C-12/19 | Pending |
| PROD-29 | Executar | AC04, AC07 | MON-06, MON-07 | C-09/10 | Pending |
| PROD-30 | Consultar | AC01, AC03, AC07 | MON-08 | C-02/18 | Pending |
| PROD-31 | Consultar | AC01, AC07 | MON-08 | C-15/23 | Pending |
| PROD-32 | Consultar | AC01, AC07 | MON-08 | C-15 | Pending |
| PROD-33 | Consultar | AC05, AC06, AC07 | MON-08 | C-17 | Pending |
| PROD-34 | Consultar | AC03, AC06, AC07 | MON-09, MON-10 | C-16/20 | Pending |
| PROD-35 | Consultar | AC06, AC07 | MON-09 | C-14 | Pending |
| PROD-36 | Consultar | AC06, AC07 | MON-09, MON-11 | C-16/20 | Pending |
| PROD-37 | Consultar | AC06, AC07 | MON-11 | Retenção e referências | Pending |

**Coverage:** 37 requisitos, todos com dono futuro e verificação prevista; zero tasks de implementação e zero testes reais nesta entrega documental.

## Success Criteria

| Critério do backlog | Evidência documental verificável |
| --- | --- |
| MON-01-AC01 | Problem Statement, produto, modelo conceitual e Out of Scope identificam usuário, problema, MVP e evoluções |
| MON-01-AC02 | Tabela de configuração e PROD-02/03/04/22/23/25 definem números, fronteiras e concorrência |
| MON-01-AC03 | Modelo de estados, tabela de transições, PROD-10 a PROD-18 e C-01 a C-07 definem resultados |
| MON-01-AC04 | Contratos de pausa/retomada/edição/reinício e C-08 a C-12/C-19/C-20 incluem incidente aberto |
| MON-01-AC05 | Contrato temporal, PROD-19 e C-17 distinguem quatro instantes e duração estimada |
| MON-01-AC06 | Frescor, janela, cobertura e PROD-34 a PROD-37; C-14/C-16/C-20 verificam fórmulas |
| MON-01-AC07 | 37 IDs PROD-NN, critérios EARS, tabela de rastreabilidade e estratégia de testes futuros |

A conclusão documental depende de revisão das premissas. O gate estrutural deve passar antes de apresentar a proposta. O status dos requisitos permanece Pending até evidência das respectivas implementações; aprovação de texto não equivale a comportamento Verified.

## Revisão desta entrega

Em 2026-10-08, o comando `python .codex/skills/tlc-spec-driven/scripts/validate_spec.py .specs/features/mon-01-produto-contratos/spec.md --strict` passou com zero erros e zero avisos. A revisão documental confrontou os sete critérios de origem com a tabela acima e as sequências C-01 a C-23. A ordem de seleção por vencimento e a invalidade de resultados tardios foram explicitadas para evitar lacunas de concorrência.

O gate verifica estrutura, não correção de uma aplicação. Não houve build, teste de aplicação, verificador de Execute ou relatório `validation.md`. Esses passos serão executados nas entregas de implementação; não se marca o backlog como concluído antes da aceitação desta proposta.
