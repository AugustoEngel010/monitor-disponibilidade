# MON-11: contexto

## Feature Boundary

Operação e retenção no monólito existente. Pedido autoriza spec, design, tasks, implementação, migração, testes e capacidade locais.

## Implementation Decisions

Preservar AD-001 a AD-011, Basic/CSRF, política de destinos e fluxo Docker. Defaults de TTL, lotes, metadados e logs estão explicitados na spec, sem atribuir ao usuário decisões não discutidas. Ferramentas: shell/aplicação de patches, Docker, Python para validadores; skill tlc-spec-driven. Sem serviços externos novos.

## Deferred Ideas

Arquivamento externo ou compactação dos recibos de deduplicação fica fora desta entrega.
