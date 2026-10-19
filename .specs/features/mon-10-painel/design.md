# MON-10: design

**Spec:** [spec.md](spec.md). **Status:** execução autorizada no pedido.

## Architecture Overview

HTML estático em resources/static/index.html, CSS local e módulos JavaScript sem dependências runtime. Spring Boot serve os arquivos com a fronteira Basic global existente. Fetch same-origin consome os DTOs MON-04/09; token CSRF é consultado antes de cada escrita e mantido apenas na chamada. Hashes representam lista, formulário e detalhe; não é necessário controller novo.

Alternativas avaliadas: templates server-side exigiriam engine/dependência e tradução dos contratos; SPA com framework exigiria build Node runtime adicional. Módulos nativos atendem ao pedido e preservam o JAR atual. Não há nova arquitetura de backend.

## Code Reuse Analysis

| Componente | Uso |
| --- | --- |
| MonitorController e docs/monitor-api.md | Oito campos, CRUD sem exclusão, pausa/retomada e erros por campo |
| QueryController e docs/queries.md | Labels, indicadores, cobertura, histórico e filtros |
| AdministrativeSecurityConfiguration / CsrfController | Basic por requisição, cookie/token de sessão, 401/403 vazios |
| Dockerfile e serviço tests | Build Java/Checkstyle/Testcontainers mantidos |

## Components

| Componente | Local | Responsabilidade |
| --- | --- | --- |
| Cliente API | static/panel/api.js | Fetch, timeout finito, CSRF e erros sanitizados |
| Shell e lista | static/panel/app.js | Roteamento, montagem segura de DOM, paginação da lista e summary por cartão |
| Formulário | static/panel/form.js | Configuração tipada, erros acessíveis e preservação dos valores |
| Ciclo de vida | static/panel/lifecycle.js | Mutação confirmada sem atualização otimista |
| Detalhes | static/panel/details.js | Resumo e históricos independentes, filtros e paginação |
| Estilos | static/panel/panel.css | Layout adaptável, contraste, foco e quebra de texto |
| Navegador de testes | config/panel | Playwright e HTTP controlado somente em Docker |

## Data Models

Nenhum DTO novo. Listagem MON-04 fornece configuração e IDs; summary MON-09 fornece o label oficial e estado do cartão. Até 20 summaries por página, sem polling, com requisições concorrentes limitadas pelo tamanho da página. Detalhes carregam os quatro recursos sem fazer consultas por tentativa. Datas de filtros permanecem strings ISO com offset e são codificadas por URLSearchParams; somente datas apresentadas são convertidas para o fuso local.

## Error Handling Strategy

401: autenticação administrativa necessária, recarregar página para challenge do navegador. 403: acesso negado ou CSRF expirado, sem reenvio. 400: erros de formulário/filtro junto aos campos. 409: mensagem de conflito específica. 404: monitor não encontrado. Rede/timeout/5xx: mensagem fixa, sem expor corpo; escrita pode ter sido confirmada, conferir dados antes de repetir. Cada seção de histórico mantém controles e erro local.

## Risks & Concerns

| Concern | Local | Mitigação |
| --- | --- | --- |
| N+1 de summary por lista | MonitorController.list não tem label MON-09 | Página 20, atualização explícita, falha isolada; sem novo endpoint fora do escopo |
| Erro após commit | Fetch de escrita | Não repetir, mensagem de resultado não confirmado |
| Respostas atrasadas | Navegação/filtros | Identidade da renderização e contador por seção |
| Código/HTML de valores persistidos | Nome e URL | textContent e atributos locais; nenhum innerHTML com dados |
| Testes de UI ausentes | config/run-tests.sh | Novo perfil panel-tests com Chromium, PostgreSQL próprio em tmpfs, sem portas do operador |
| Daemon indisponível no início | Docker Desktop local | Iniciar Desktop e não declarar gates sem execução |

## Tech Decisions

Testes usam Node 22.14.0-bookworm-slim e Playwright 1.58.2 fixado no lockfile; o instalador baixa somente Chromium headless compatível e dependências, evitando os outros navegadores da imagem completa. O runtime Java não recebe Node, navegador ou dependência de teste. Consultada a [documentação oficial de browsers](https://playwright.dev/docs/browsers) para `install --with-deps --only-shell chromium`. Compose de testes usa credenciais públicas exclusivamente descartáveis, DB próprio e backend sem envio SMTP. Incidentes reais são gerados pelo executor contra HTTP controlado no serviço de navegador; notificações permanecem PENDING para inspeção.
