# Demonstração local MON-12

O roteiro usa HTTP e SMTP reais, sem mocks, alterações SQL, relógio acelerado ou mudança no código do monitor. O [README](../README.md) prepara o Compose. Os resultados da execução estão em [evidence.md](../.specs/features/mon-12-demonstracao/evidence.md).

## Executar com assertions

No Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1
```

No PowerShell 7: `pwsh -File scripts/demo.ps1`. Aguarde alguns minutos. O projeto `monitor-portfolio` tem volume próprio, portas 18080 (painel), 18081 (demo), 18025 (Mailpit) e 11025 (SMTP). Credenciais fictícias: `portfolio-operator` / `portfolio-local-only`. O script fixa SMTP local, políticas e credenciais; restaura as variáveis do processo ao terminar. Não altera `.env`. Depois de falhas tenta reativar SMTP e scheduler, mantendo banco e evidências.

O banco deve ser novo. Para repetir sem apagar o anterior, pare-o com `docker compose -p monitor-portfolio down` e use `-ProjectName monitor-portfolio-2`. As portas não podem estar em uso. `-NoBuild` é apenas para quando as imagens desse projeto já estão construídas e correspondem ao código atual. Se desejar descartar uma execução, `docker compose -p monitor-portfolio down --volumes` apaga os dados daquele projeto.

Arquivos gerados em `.validation/demo/`: snapshots JSON de cada etapa, oito capturas PNG e `compose.log`. O diretório é substituído por novas execuções; copie as evidências que deseja guardar antes de repetir. Nenhum relatório inclui o header Authorization ou as variáveis de ambiente.

| Etapa | Ação e resultado exigido | Evidência |
| --- | --- | --- |
| Sucesso inicial | Seed duas vezes preserva IDs; ambos UP; zero incidentes/avisos | 01-normal.png |
| Confirmação da queda | Mudar principal para error; primeira falha deixa contador 1 e nenhum incidente | confirming-down.json |
| Queda | Terceira falha confirma DOWN; outra falha mantém um incidente e uma intenção DOWN | normal.json, 02-down.png |
| Recuperação | Primeiro sucesso confirma recuperação; segundo fecha o mesmo incidente; DOWN SMTP antecede RECOVERY | confirming-recovery.json, 03-recovering.png, 04-recovered.png |
| Pendência | Parar SMTP e produzir nova queda; uma tentativa FAILED/SMTP_FAILURE e PENDING durável | pending.json, 05-pending.png |
| Reinício | Reiniciar app com SMTP ainda parado; mesmos IDs e todos os executionIds anteriores preservados | restart.json |
| Retomada | Restaurar SMTP; mesma intenção ACCEPTED, 2..5 tentativas, retry não anterior a término+30s | retry.json |
| Concorrência | Principal slow; secundário conclui SUCCESS entre início e término do TIMEOUT principal | slow.json |
| Pausa | DOWN → PAUSED sem fechar incidente; nenhum novo check durante 11s | paused.json, 06-paused.png |
| Lacuna | Retomar e observar TIMEOUT; desabilitar scheduler por ambiente; esperar idade >22s | stale.json, 07-stale.png |
| Encerramento | Reativar scheduler e healthy; duas recuperações confirmadas; dois incidentes CLOSED e quatro intenções ACCEPTED | final.json, 08-final.png |

Ao final, Mailpit contém quatro mensagens para `portfolio@example.test`, correlacionadas aos incidentes reais. A política não promete exactly-once SMTP; a ausência de duplicação nessa execução não elimina a possibilidade de duplicação em confirmação incerta. Reaplicação local de UUID/transição e cenários de crash durante SENDING são cobertos pelos testes Java MON-07/08; o roteiro demonstra o reinício em PENDING.

## Apresentar manualmente

Use a aplicação padrão do README em http://localhost:8080 e Mailpit em http://localhost:8025. Abra os detalhes de **Demo principal** e mantenha o segundo alvo visível na lista. O painel carrega dados ao abrir ou clicar **Atualizar**; não pressupõe atualização automática.

No PowerShell, controle o alvo sem tocar no monitor:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/control/main/healthy
Invoke-RestMethod http://localhost:8081/control
```

Depois selecione erro:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/control/main/error
```

Atualize o painel após cada intervalo de 10s. As primeiras duas falhas confirmam a queda; a terceira abre o incidente. Aguarde outra falha e confira que o ID não mudou. Consulte **Notificações** e o e-mail no Mailpit. Volte a healthy; observe um sucesso com confirmação de recuperação e, no segundo, o incidente encerrado com os quatro horários. Recarregue as consultas para ver o histórico persistido.

Para demonstrar retomada, pare SMTP **antes** de uma nova queda:

```sh
docker compose stop smtp
```

Mude principal para error e espere DOWN. Nos detalhes, aguarde uma tentativa FAILED e intenção PENDING com `nextAttemptAt` futuro. Registre IDs do incidente e notificação. Reinicie apenas a aplicação:

```sh
docker compose restart app
```

Depois da readiness voltar, confira os mesmos IDs, incidente aberto e histórico. Restaure SMTP antes de esgotar as cinco tentativas:

```sh
docker compose start smtp
```

O worker espera `nextAttemptAt`, não dispara retry imediato só porque houve reinício. Após aceitação, volte a healthy e confira a recuperação.

Para a concorrência:

```powershell
Invoke-RestMethod -Method Post http://localhost:8081/control/main/slow
```

O servidor aguarda **15s antes dos cabeçalhos**, excedendo T=2s. O monitor registra TIMEOUT, enquanto Demo independente continua registrando SUCCESS. O controle do segundo recurso é `/control/secondary/healthy|error|slow`; mudar principal não altera secundário. O runner prova a sobreposição por timestamps, em vez de inferir concorrência de uma imagem.

Pause o principal pelo painel durante um incidente. A lista mostra **Pausado**, estado confirmado **Indisponível** e o mesmo ID do incidente. Retome: a evidência corrente é invalidada até nova observação; a pausa nunca confirma recuperação.

Para mostrar dados antigos sem pausar, primeiro obtenha um DOWN recente, então altere temporariamente `.env` para `MONITOR_SCHEDULER_ENABLED=false` e recrie app:

```sh
docker compose up -d --no-deps --force-recreate app
```

Espere mais de `2I+T=22s` desde a última observação. O painel mostra **Dados antigos**, ciclo ACTIVE e estado DOWN preservado, com o mesmo incidente. Ausência de execução não produz SUCCESS nem falha externa. Restaure `MONITOR_SCHEDULER_ENABLED=true`, recrie app, mude alvo para healthy e aguarde dois sucessos. Pare normalmente com `docker compose down`, preservando volumes.

## Evidências visuais

As imagens versionadas em [docs/images/mon12](images/mon12) vêm do roteiro real em Chromium/Playwright. Os snapshots correspondentes permitem conferir IDs, instantes e tentativas sem depender da apresentação visual. O parecer final registra inspeção das imagens; não equivale a aprovação humana de design.
