# Hexora

Hexora é um gerenciador de arquivos Android open source em desenvolvimento, escrito em Kotlin + Jetpack Compose. O objetivo é combinar um file manager rápido e seguro com ferramentas técnicas avançadas, sem fingir privilégios que o Android não concedeu.

> Status atual: **alpha**. A base de gerenciamento de arquivos está funcional; Shizuku, Wireless ADB, Root, edição de APK/DEX/Smali, Hex Editor, terminal e recursos avançados continuam no roadmap.

## O que já existe

- navegação pelo armazenamento compartilhado quando o usuário concede **All Files Access**;
- acesso de menor privilégio via **Storage Access Framework (SAF)** com permissões persistíveis;
- listagem de volumes e atalhos de pastas;
- criar arquivo e pasta;
- renomear;
- copiar e mover com progresso e cancelamento;
- exclusão com confirmação explícita;
- seleção múltipla e compartilhamento;
- cálculo SHA-256;
- editor de texto local com gravação por arquivo temporário + substituição segura;
- histórico de navegação, busca/filtro da pasta e tela de tarefas;
- tema claro/escuro/sistema;
- proteções contra nomes inválidos e caminhos de archive/Zip Slip;
- CI com lint, testes unitários e geração de APK debug.

## Segurança

Hexora segue menor privilégio por padrão. O app não tenta contornar o sandbox do Android e não trata ocultar botões como segurança.

- `MANAGE_EXTERNAL_STORAGE` é opcional e depende de ação explícita do usuário;
- SAF continua disponível para conceder apenas pastas escolhidas;
- referências locais são canonicalizadas e limitadas aos roots permitidos;
- operações de cópia limpam destinos parciais em falhas e validam tamanho antes de um move por copy+delete;
- substituição destrutiva por conflito permanece bloqueada enquanto não houver troca transacional segura para o provider;
- `FileProvider` é não exportado e concede URI somente durante compartilhamento/abertura;
- cleartext HTTP está desabilitado no Manifest;
- não há telemetria, conta ou secrets embutidos nesta versão.

Veja [SECURITY.md](SECURITY.md) e [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Build

Requisitos:

- JDK 17;
- Android SDK 36;
- Gradle 8.13 (o wrapper do projeto é a fonte preferida).

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

O APK debug fica em `app/build/outputs/apk/debug/`. O GitHub Actions publica o artifact **Hexora-APK** em builds bem-sucedidos.

## Arquitetura

A aplicação usa uma abstração `FileAccessProvider` para impedir que a UI dependa diretamente de um único mecanismo de acesso. Hoje existem providers Local e SAF. Novos níveis de privilégio devem implementar a mesma fronteira sem aumentar permissões silenciosamente.

Detalhes em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Roadmap

Próximas áreas planejadas, sem prometer suporte antes de implementação e testes reais:

- dual-pane;
- preview e operações de arquivos compactados;
- Shizuku e Wireless ADB com autorização explícita;
- Root isolado e opt-in;
- Hex Editor;
- APK/DEX/Smali;
- terminal;
- análise de arquivos e ferramentas de desenvolvedor.

## Contribuindo

Leia [CONTRIBUTING.md](CONTRIBUTING.md). Vulnerabilidades devem seguir o processo de [SECURITY.md](SECURITY.md), não uma issue pública com detalhes exploráveis.

## Licença

Apache License 2.0. Consulte [LICENSE](LICENSE).
