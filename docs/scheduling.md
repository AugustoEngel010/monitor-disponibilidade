# Agendamento de verificações

A aplicação agenda verificações automaticamente após startup. O MVP aceita somente uma instância. PostgreSQL persiste configurações, próximo horário, reservas em andamento e resultados; reiniciar não apaga esses dados.

| Variável | Padrão | Regra |
| --- | --- | --- |
| MONITOR_CHECK_CONCURRENCY | 4 | Inteiro 1..16; inválido impede startup |
| MONITOR_SCHEDULER_POLL_MS | 250 | Milissegundos positivos entre rodadas; inválido impede startup |
| MONITOR_SCHEDULER_ENABLED | true | false desabilita polling para execução manual/testes |

Após editar `.env`, recrie app com `docker compose up -d --force-recreate app`. Um restart simples não recarrega variáveis. Use o fluxo Docker do README para build e testes.

## Seleção, intervalos e capacidade

Cada rodada seleciona ACTIVE com `nextCheckAt <= agora`, em ordem de vencimento e UUID. Pausados e horários futuros são excluídos. A tentativa começa logicamente na reserva transacional imediatamente antes do despacho. Essa reserva grava um UUID, versão/geração e o horário vencido original em `monitor.check_attempts`, e atualiza `nextCheckAt = início lógico + intervalo`. O HTTP usa o snapshot MON-05 fora da transação; seu `startedAt` mede a entrada efetiva no executor e pode diferir ligeiramente do início lógico.

Existe no máximo uma reserva por monitor. Rodadas simultâneas usam o mesmo lock de linha da administração. Além da reserva, o mapa local exclui workers ainda terminando, cobrindo a janela entre a remoção da reserva e a saída da thread. A capacidade inclui execução e gravação; cancelamento não libera a vaga antes de o worker terminar.

O mapa contém no máximo C workers. Não há fila de execuções em memória. Quando faltam vagas, o monitor mantém o horário vencido no PostgreSQL. Assim, um monitor atrasado continua prioritário quando surgir uma vaga. O atraso pode ser calculado como `max(0, agora - nextCheckAt)` antes da reserva; `scheduled_at` preserva o vencimento original durante a tentativa. Métricas e consulta paginada serão entregues nas MON-09/11.

Cada monitor lento ocupa somente uma vaga e respeita seu timeout total MON-05. Se C=1, os demais esperam essa vaga; com C>=2, podem avançar enquanto a tentativa lenta está em andamento. Timeout, sucesso, falha e cancelamento liberam capacidade no fim do worker. O polling seguinte distribui a vaga. O intervalo não é garantia de início pontual sob carga.

## Pausa, edição e resultados tardios

Pausa invalida a geração, remove o próximo horário e impede novas reservas. Na rodada seguinte, o agendador solicita interrupção da tentativa iniciada. Se o HTTP já concluiu, seu resultado real permanece no histórico; se a interrupção vence, permanece CANCELLED. Ambos preservam estado confirmado e incidente. A conclusão não sobrescreve o agendamento estabelecido por pausa, retomada ou edição.

Retomada torna o monitor elegível imediatamente, mas aguarda o término da tentativa anterior. Mudança de política invalida versão/geração e também cancela a tentativa antiga; a próxima utiliza a nova configuração. Nome e destinatário preservam a geração e não cancelam HTTP. A conclusão aplica resultados pelo [processador MON-07](incidents.md), comparando versão/geração sob o lock administrativo conforme AD-002/006/008/009.

## Reinício e falhas de gravação

Pare a instância anterior antes de iniciar outra. A primeira rodada reconcilia reservas sem worker local. Se já existe resultado para o UUID, ele vence e é aplicado antes de remover a reserva. Sem resultado, registra CANCELLED não externo, com snapshot original e duração 0 indicando medição desconhecida. Na geração corrente, esse cancelamento zera sequências/horários provisórios, inclusive início de recuperação do incidente, sem alterar configuração, observação, saúde confirmada, identidade do incidente ou próximo horário. Resultado de versão/geração antiga fica somente no histórico.

Um monitor vencido executa somente uma nova tentativa; o próximo horário usa o início atual + intervalo. Não existe replay das verificações perdidas. Monitores ainda não vencidos aguardam seu horário; pausados permanecem pausados. Recuperação repetida não duplica resultados.

Shutdown interrompe polling e workers antes de fechar o datasource e aguarda até cinco segundos pelo término. Se o processo termina sem gravar/limpar uma reserva, a próxima instância a recupera. Erro de banco impede gravação, mantém trabalho durável e emite mensagem operacional fixa, sem converter indisponibilidade do banco em falha externa. A vaga local é liberada; a próxima rodada com banco disponível reconcilia a reserva órfã. Não há retry imediato de HTTP.

## Ausência de evidência

A API administrativa já expõe `evidence`, `recentObservation`, `confirmedState`, `lifecycle`, `lastObservedAt` e `nextCheckAt`. A observação corrente é FRESH se sua idade está entre 0 e `2 × intervalo + timeout`, inclusive. Acima desse limite ou no futuro, é STALE e `recentObservation=false`. Ausência de observação corrente é NEVER_OBSERVED. Pausa e retomada invalidam a observação corrente sem apagar o timestamp histórico. Nenhuma lacuna de execução cria SUCCESS ou falha externa.

MON-06 executa e preserva evidência; a integração MON-07 aplica resultados e registra estado, incidente, transição e intenção atomicamente antes de liberar a reserva. Falha transacional mantém a reserva recuperável; resultado bruto previamente salvo não exige novo HTTP. Os testes de ciclo de vida agora usam incidentes reais.

## Verificação

```sh
docker compose run --build --rm tests
docker compose build app
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-compose.ps1 -NoBuild
```

ScheduleStoreIT verifica reserva/ordem/rollback/recuperação no PostgreSQL. CheckSchedulerTest usa relógio mutável e latches para sobreposição, limite, cancelamento e falhas. SchedulerRuntimeIT verifica polling real, servidores lento e saudável, timeout, pausa/edição, restart Spring/PostgreSQL, falha de gravação, upgrade V3→V4 e frescor por HTTP. Relógio real é usado somente para timeout HTTP e esperas limitadas de sincronização.

[Spec e rastreabilidade](../.specs/features/mon-06-agendamento/spec.md), [tasks](../.specs/features/mon-06-agendamento/tasks.md), [evidências de gates](../.specs/features/mon-06-agendamento/evidence.md) e [decisão AD-008](../.specs/STATE.md).

[Validação independente](../.specs/features/mon-06-agendamento/validation.md): PASS nos oito critérios, 301 testes e cinco mutações mortas; build e smoke aprovados.
