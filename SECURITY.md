# Security Policy

## Supported versions

Hexora ainda está em alpha. Correções de segurança são aplicadas primeiro à branch `main` e à versão de desenvolvimento atual.

## Reporting a vulnerability

Não publique detalhes exploráveis em uma issue pública antes de existir correção. Use o recurso privado de Security Advisories do repositório quando disponível, ou entre em contato com o mantenedor por um canal privado listado no perfil do projeto.

Inclua, quando possível:

- versão/commit afetado;
- versão do Android e fabricante;
- pré-condições e nível de privilégio necessário;
- passos mínimos para reproduzir;
- impacto esperado e observado;
- logs ou PoC sem credenciais, tokens ou dados pessoais.

## Security principles

- menor privilégio por padrão;
- nenhuma confiança em dados vindos de nomes de arquivo, URIs, archives ou providers externos;
- nenhuma escalada silenciosa de SAF para All Files, Shizuku, ADB ou Root;
- operações destrutivas exigem intenção explícita e devem evitar perda parcial de dados;
- paths de archive são validados contra path traversal/Zip Slip antes de extração;
- arquivos locais são canonicalizados e limitados a roots permitidos;
- componentes Android exportados devem ser mínimos;
- secrets, caso sejam necessários no futuro, nunca podem ser embarcados no repositório ou APK.

## Out of scope by design

Shizuku, Wireless ADB, Root, terminal e edição APK/DEX ainda não fazem parte da superfície implementada atual. Relatórios sobre funções inexistentes não são vulnerabilidades até que essas funções sejam adicionadas.
