# Capacidade observada na MON-11

Execução local em 20/10/2026, iniciada às 17:54:31 em America/Sao_Paulo (20:54:31 UTC). [Medições brutas](mon-11.json). Este cenário curto mede esta combinação de carga e recursos; não estabelece SLA nem capacidade sustentável universal.

## Reprodução

```powershell
docker compose -p mon11-capacity -f compose.yaml -f compose.capacity.yaml run --build --rm tests
```

O perfil Maven `capacity` é executado dentro do serviço tests. Ele executa os 169 testes unitários e um cenário de integração separado, sem substituir a suíte normal. O relatório aparece em `target/capacity.json`; o comando copia relatórios mesmo em caso de falha. Não exige Maven/JDK no host. O banco Testcontainers é descartável, sem conexão com o volume do operador. O servidor HTTP e o servidor SMTP são locais ao JVM de teste; a exceção de destinos é somente a origem HTTP exata, sem desabilitar a política geral.

O harness usa `CheckScheduler`, `CheckService`, `NotificationRuntime`, HTTP e SMTP reais. Chama as rodadas de agendamento e notificação, coleta os gauges e espera 250ms; esses custos entram no tempo observado. Em produção os dois pollers têm threads próprias. Portanto, o resultado caracteriza este harness e estes recursos, incluindo sua coleta, sem extrapolar o throughput para outra implantação.

## Ambiente e carga

| Parâmetro | Valor |
| --- | --- |
| Host | Windows, Intel Core i5-13420H, 8 núcleos/12 processadores lógicos, 16.869.351.424 bytes RAM |
| Docker Desktop | Engine 29.8.2, 12 CPUs, 8.173.436.928 bytes disponíveis à VM Linux |
| Aplicação, runner Maven e alvos locais | Mesmo contêiner, 2 CPUs, 2 GiB; JVM do cenário com heap máximo de 512 MiB |
| PostgreSQL | 17.6-alpine, contêiner próprio, 1 CPU e 512 MiB |
| Monitores | 100; tentativa de criar o 101º rejeitada com MONITOR_LIMIT |
| Política | I=10s, T=2s, F=1, R=1; concorrência C=4, polling 250ms |
| Respostas | 25 × 200 imediato; 25 × 200 após 300ms; 25 × 503 imediato; 25 × cabeçalhos após 3000ms, excedendo T |
| SMTP | Socket local real, greeting 421, sem entrega externa |
| Duração medida | 60,070333095s incluindo shutdown; não inclui preparação do banco/JVM |

Uma amostra de `docker stats` durante a primeira execu??o do mesmo cen?rio registrou aplicação/runner a 13,71% CPU e 836,9 MiB; PostgreSQL a 3,30% e 57,66 MiB. É uma amostra, não pico nem média. Os serviços de navegador foram encerrados antes da fase temporizada.

## Resultados

| Medição | Observado |
| --- | --- |
| Verificações registradas | 285, todos os 100 monitores observados |
| Concorrência máxima | 4, zero sobreposições por monitor |
| SUCCESS | 144, média 155,360ms combinando os alvos rápidos e de 300ms |
| HTTP_FAILURE | 72, média 4,789ms |
| TIMEOUT | 69, média 2000,393ms |
| CANCELLED | 2 no encerramento, média 805,024ms; não são falhas externas |
| Atraso de agendamento | 285 medições; máximo 20,634817s, média 10,967730218s |
| Pendências SMTP | Pico e término com 50 PENDING; zero SENDING ao terminar |
| Conexões SMTP na fase temporizada | 100, duas tentativas reais por intenção |
| Reservas HTTP após shutdown | Zero |

A mistura de timeouts, alvos lentos e polling **não sustentou o intervalo de 10s** nessa configuração. A contrapressão apareceu no atraso de agendamento; não houve crescimento de workers acima de C ou replay de cada intervalo perdido. MON-01 limita configuração/concorrência, sem prometer que toda combinação de 100 alvos cumpre I=10s. A medição confirma os limites e torna a saturação visível.

## Fases com relógio acelerado

Após encerrar o período medido e os workers HTTP, o cenário avançou o relógio lógico da outbox em uma hora por rodada. O SMTP continuou sendo o socket real com 421. Foram observadas **50 EXHAUSTED**, zero PENDING e no máximo cinco tentativas por intenção, totalizando 250 conexões. Essa fase verifica persistência, métricas e política de retry; seu tempo não representa throughput de entrega.

Uma limpeza com Clock avançado em 31 dias removeu 133 resultados elegíveis e preservou os 50 incidentes abertos. Os 133 recibos ocuparam aproximadamente 56 bytes por linha lógica e 24.576 bytes de relação com índice, nessa amostra pequena. Páginas/índices têm custos fixos; não extrapolar essa razão como custo por linha em escala.

Recibos de idempotência, últimas evidências e cadeias protegidas não expiram pela regra comum. A retenção reduz payload e histórico; não promete disco constante. Incidentes abertos, trabalho pendente ou dados ainda não aplicados podem reter evidências por mais de 30 dias. Diagnostique essas exceções com os comandos de [operação](../operations.md).

## Evidência de execução

`CapacityScenario` verifica as medições com assertions sobre dados e métricas reais. O comando encerrou com BUILD SUCCESS, 169 unitários e um cenário, zero falhas/erros/skips/Checkstyle; log local `.validation/mon11-t6-capacity-final.log`. Os dados versionados foram copiados de `target/capacity.json` antes do próximo `clean verify`.
