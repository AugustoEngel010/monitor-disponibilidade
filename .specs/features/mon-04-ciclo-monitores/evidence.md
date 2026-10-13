
## T2

Quick Docker exit 0; 40 unitários, zero failures/errors/skips; 39 novos cenários de configuração. LIFE-02/03: default, trim, limites numéricos, T=I, URL e e-mail.

| Evidência / assertion | Requisito / resultado | Necessidade |
| --- | --- | --- |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:17`: `assertThat(config).isEqualTo(new MonitorConfiguration("Demo", "https://example.org/a",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:23`: `assertThat(MonitorConfiguration.validated("n", "http://example.org:1", 10, 9, 200, 1, 1,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:24`: `"a@example.org").timeoutSeconds()).isEqualTo(9);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:25`: `assertThat(MonitorConfiguration.validated("n".repeat(100), "https://example.org:65535",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:26`: `3600, 30, 599, 10, 10, "a@example.org").name()).hasSize(100);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:28`: `assertThat(MonitorConfiguration.validated("n", url, 10, 1, 200, 1, 1,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:29`: `"a@example.org").url()).hasSize(2048);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:38`: `assertThat(problem.status()).isEqualTo(400);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:39`: `assertThat(problem.errors()).containsOnlyKeys("name", "url", "intervalSeconds", "timeoutSeconds",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:46`: `rejects("intervalSeconds", () -> config(3601, 5, 200, 3, 2));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:47`: `rejects("timeoutSeconds", () -> config(60, 0, 200, 3, 2));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:48`: `rejects("timeoutSeconds", () -> config(10, 10, 200, 3, 2));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:49`: `rejects("expectedStatus", () -> config(60, 5, 600, 3, 2));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:50`: `rejects("failureThreshold", () -> config(60, 5, 200, 11, 2));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:51`: `rejects("recoveryThreshold", () -> config(60, 5, 200, 3, 0));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:52`: `rejects("name", () -> MonitorConfiguration.validated("n".repeat(101), "https://example.org",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:63`: `rejects("url", () -> MonitorConfiguration.validated("n", url, null, null, null, null, null,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:70`: `rejects(field, () -> MonitorConfiguration.validated(null, null, null, null, null, null, null, null));` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:72`: `rejects("url", () -> MonitorConfiguration.validated("n", "http://example.org/" + "x".repeat(2030),` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:74`: `rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "http://example.org",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:82`: `assertThat(MonitorConfiguration.validated("n", url, null, null, null, null, null,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:83`: `"a@example.org").url()).isEqualTo(url);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:91`: `rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "https://example.org",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:98`: `assertThat(email).hasSize(254);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:99`: `assertThat(MonitorConfiguration.validated("n", "https://example.org", null, null, null, null, null,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:100`: `email).alertRecipient()).isEqualTo(email);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:101`: `rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "https://example.org",` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:110`: `private void rejects(String field, Runnable operation) {` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:112`: `assertThat(problem.status()).isEqualTo(400);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:113`: `assertThat(problem.errors()).containsKey(field);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorConfigurationTest.java:114`: `assertThat(problem.getMessage()).doesNotContain("secret", "token@example");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |

Adequação: assertions de estado/valores, sem mocks de chamada, todos os cenários ligados à spec; JUnit/AssertJ e Checkstyle da baseline. Nenhum teste removido, enfraquecido ou ignorado.

## T3

Quick Docker exit 0: 46 unitários; seis novos testes de modelo, zero falhas/erros/skips. LIFE-05/06/07: igualdade integral de state, incidente preservado, versões, counters e fronteira inclusiva de frescor.

| Evidência / assertion | Requisito / resultado | Necessidade |
| --- | --- | --- |
| `src/test/java/br/com/monitor/MonitorTest.java:20`: `assertThat(monitor.state()).isEqualTo(new Monitor.State("ACTIVE", "UNKNOWN", 0, 0,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:22`: `assertThat(monitor.evidence(now)).isEqualTo("NEVER_OBSERVED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:23`: `assertThat(monitor.createdAt()).isEqualTo(now);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:24`: `assertThat(monitor.updatedAt()).isEqualTo(now);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:31`: `assertThat(changed.state()).isEqualTo(monitor.state());` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:32`: `assertThat(changed.id()).isEqualTo(monitor.id());` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:33`: `assertThat(changed.createdAt()).isEqualTo(monitor.createdAt());` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:34`: `assertThat(changed.configuration().name()).isEqualTo("new");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:35`: `assertThat(changed.configuration().alertRecipient()).isEqualTo("new@e.org");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:36`: `assertThat(changed.updatedAt()).isEqualTo(now);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:37`: `assertThat(changed.edit(changed.configuration(), now.plusSeconds(10))).isEqualTo(changed);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:45`: `assertThat(error.status()).isEqualTo(409);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:46`: `assertThat(error.code()).isEqualTo("OPEN_INCIDENT");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:49`: `assertThat(monitor.configuration()).isEqualTo(config);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:50`: `assertThat(monitor.state().confirmedState()).isEqualTo("DOWN");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:51`: `assertThat(monitor.state().recoveryCount()).isEqualTo(1);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:59`: `assertThat(changed.id()).isEqualTo(monitor.id());` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:60`: `assertThat(changed.createdAt()).isEqualTo(monitor.createdAt());` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:61`: `assertThat(changed.configuration()).isEqualTo(policy);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:62`: `assertThat(changed.state()).isEqualTo(new Monitor.State("ACTIVE", "UNKNOWN", 0, 0,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:64`: `assertThat(changed.evidence(now)).isEqualTo("NEVER_OBSERVED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:68`: `assertThat(changed.state().lifecycle()).isEqualTo("PAUSED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:69`: `assertThat(changed.state().nextCheckAt()).isNull();` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:76`: `assertThat(paused.state()).isEqualTo(new Monitor.State("PAUSED", "DOWN", 0, 0, null, null,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:78`: `assertThat(paused.pause(now.plusSeconds(1))).isEqualTo(paused);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:79`: `assertThat(paused.evidence(now)).isEqualTo("NEVER_OBSERVED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:81`: `assertThat(resumed.state()).isEqualTo(new Monitor.State("ACTIVE", "DOWN", 0, 0, null, null,` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:83`: `assertThat(resumed.resume(now.plusSeconds(3))).isEqualTo(resumed);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:84`: `assertThat(resumed.evidence(now.plusSeconds(2))).isEqualTo("NEVER_OBSERVED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:85`: `assertThat(monitor.resume(now)).isEqualTo(monitor);` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:86`: `assertThat(Monitor.create(UUID.randomUUID(), config, now).pause(now).state().confirmedState())` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:87`: `.isEqualTo("UNKNOWN");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:94`: `assertThat(monitor.evidence(observedAt)).isEqualTo("FRESH");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:95`: `assertThat(monitor.evidence(observedAt.plusSeconds(125))).isEqualTo("FRESH");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:96`: `assertThat(monitor.evidence(observedAt.plusSeconds(125).plusNanos(1))).isEqualTo("STALE");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:97`: `assertThat(monitor.evidence(observedAt.minusNanos(1))).isEqualTo("STALE");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |
| `src/test/java/br/com/monitor/MonitorTest.java:98`: `assertThat(monitor.pause(now).evidence(observedAt)).isEqualTo("NEVER_OBSERVED");` | Spec MON-04; outcomes/limites do cenário nomeado no teste | Manter: requisito correspondente |

Adequação: assertions de estado/valores, sem mocks de chamada, todos os cenários ligados à spec; JUnit/AssertJ e Checkstyle da baseline. Nenhum teste removido, enfraquecido ou ignorado.
