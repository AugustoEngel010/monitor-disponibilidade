
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
