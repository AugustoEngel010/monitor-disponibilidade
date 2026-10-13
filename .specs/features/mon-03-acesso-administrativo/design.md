# MON-03: design

**Spec:** [spec.md](spec.md). **Status:** execução local autorizada.

## Architecture Overview

Spring Security aplica uma única SecurityFilterChain a qualquer rota. Um BasicAuthenticationFilter nativo é posicionado antes de CsrfFilter para garantir 401 com credencial inválida mesmo em escritas sem token. O handler CSRF retorna 401 sem autenticação e 403 quando autenticado. HTTP Basic não salva autenticação na sessão; autenticação é exigida em toda requisição. Não há form login ou logout por sessão.

GET/HEAD dos três health endpoints são públicos. anyRequest exige o operador. Health mantém show-details/show-components=never. Não se liberam /error, OPTIONS, assets ou actuators por wildcard.

StatusOnlyHealthWebExtension conserva cálculo/status HTTP nativo e retorna somente status, removendo também os nomes de grupos do health agregado. A primeira execução do teste revelou que show-details=never não remove groups; a extensão atende à resposta mínima da spec sem enfraquecer assertions.

CSRF usa HttpSessionCsrfTokenRepository e o mascaramento XOR padrão. GET /api/admin/csrf entrega token/headerName/parameterName; clientes enviam cookie mais token por header ou parâmetro em toda escrita. Sem CORS aberto. Cookie HttpOnly e SameSite=Lax, sessão 30 min. Códigos 401/403 usam corpo vazio e não ecoam inputs.

Contexto de autenticação fica em RequestAttributeSecurityContextRepository para preservar a identidade no dispatch de erro da mesma requisição. SessionManagement é desabilitado: não existe login por sessão. A política STATELESS com a estratégia CSRF padrão ainda acionava CsrfAuthenticationStrategy a cada escrita, invalidando o token após uma ação; a suíte detectou isso e a integração foi corrigida sem enfraquecer testes.

Alternativa cookie legível pelo JavaScript requer cuidado com token bruto e BREACH; token em sessão reutiliza os defaults de segurança. Sessão de login ampliaria o mecanismo de autenticação e complicaria rotação; Basic por requisição atende o MVP.

## Code Reuse Analysis

Reutilizar bootstrap MON-02, PostgreSQL Testcontainers, Java HttpClient, Failsafe, Checkstyle e o smoke isolado existente. Dockerfile e run-tests.sh mantêm os mesmos estágios e gates. Nenhuma migração/tabela nova.

## Components

| Componente | Local | Responsabilidade |
| --- | --- | --- |
| AdministrativeSecurityConfiguration | security/ | Validar ambiente sem binding de senha em erros, BCrypt em memória, operador e chain |
| CsrfController | security/ | Entregar token autenticado para o cliente da mesma origem |
| AdministrativeConfigurationIT | testes | Startup completo com PostgreSQL real, valores inválidos e logs sem segredos |
| AdministrativeSecurityIT | testes | HTTP real, rotas de prova somente no teste, autorização e efeitos CSRF |
| Compose/smoke | raiz e scripts/ | Passar ambiente, manter tests independente e testar startup/rotação |

## Data Models

Um UserDetails em memória com ROLE_ADMIN e senha BCrypt. Environment recebe a senha externa; não serializar esse valor nem criar objeto de configuração com toString de segredo. Sessão guarda somente token CSRF. Rotação requer recriar app e invalida sessões em memória.

## Error Handling Strategy

Configuração inválida: IllegalStateException genérica, sem valores. Autenticação inválida: 401 e WWW-Authenticate Basic realm monitor-admin. CSRF inválido autenticado: 403. Readiness mantém 200/503 sem credenciais de probe.

## Risks & Concerns

| Concern | Local | Impacto | Mitigação |
| --- | --- | --- | --- |
| Todas as rotas abertas | pom.xml / application.yml | Acesso administrativo público futuro | Chain global e testes de rotas fora de prefixos |
| CsrfFilter precede Basic por padrão | Configuração nova | Escrita anônima retornaria 403, violando AC01 | Basic antes de CSRF; testar todos os métodos |
| Erros de binding podem ecoar senha | Configuração nova | Segredo em logs | Leitura/validação explícita, mensagem genérica; captura logs |
| Smoke/CI sem .env | scripts/validate-compose.ps1 | Startup falha após configuração obrigatória | Smoke gera valores efêmeros no ambiente e restaura os anteriores |
| Basic em HTTP | compose.yaml | Transporte sem cifragem | Manter localhost; documentar HTTPS ao expor |
| BCrypt tem limite de 72 bytes | Configuração nova | Senha truncada | Rejeitar >72 bytes UTF-8 antes de encode |

## Tech Decisions

AD-005 registra o contrato compartilhado das futuras MON-04/MON-10. Fontes oficiais: [CSRF Spring Security 6.5](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html), [HTTP Basic](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/basic.html). O mecanismo nativo de sessão e token é aplicado ao contrato desta entrega; os testes verificam os resultados reais.
