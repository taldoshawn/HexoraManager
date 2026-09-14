# Hexora

Hexora é um gerenciador de arquivos Android open source em Kotlin + Jetpack Compose, inspirado na produtividade de file managers avançados, sem fingir privilégios que o Android não concedeu.

> Status: **alpha**. File manager, SAF/All Files, Shizuku e Root já possuem backends reais. APK/DEX/Smali, Hex Editor, terminal e outras ferramentas continuam no roadmap.

## Funcionalidades atuais

- navegação no armazenamento compartilhado com All Files Access opcional;
- Storage Access Framework (SAF) com grants persistíveis;
- criar, renomear, copiar, mover e excluir;
- seleção múltipla, hash SHA-256, editor de texto e tarefas com progresso;
- Shizuku UserService real: quando o Shizuku é iniciado via Depuração sem fio/ADB, o backend roda como UID shell; com Sui/root pode rodar como UID 0;
- RootService real com libsu e Binder/AIDL;
- provider privilegiado com listagem, leitura, escrita, criação, rename, move e delete via descritores de arquivo, sem montar comandos de shell com paths do usuário;
- atalho para `/storage/emulated/0/Android/data` quando Shizuku ou root estão conectados;
- UI de acesso mostrando o estado real de All Files, SAF, Shizuku, Depuração sem fio e Root;
- proteção contra Zip Slip/path traversal e operações de cópia parcial.

## Android/data

No Android moderno, **All Files Access e SAF não concedem acesso universal ao `Android/data` de outros apps**. O Hexora não simula um bypass inexistente. Sem Shizuku/root, ele continua funcionando normalmente nos locais que o Android permite. Para ampliar o acesso a `Android/data`, conecte Shizuku iniciado por Depuração sem fio/ADB ou Root; o resultado final ainda pode variar conforme fabricante, FUSE e SELinux.

## Build

Requisitos: JDK 17, Android SDK 36 e Gradle 8.13.

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

O GitHub Actions publica o artifact **Hexora-APK** em builds bem-sucedidos.

## Segurança

- privilégios extras são opt-in;
- Shizuku usa UserService oficial, não `newProcess`;
- Root usa libsu RootService;
- paths privilegiados exigem caminho absoluto, rejeitam NUL e são normalizados;
- o serviço retorna `ParcelFileDescriptor` para I/O em vez de concatenar paths em comandos de shell;
- componentes exportados permanecem mínimos; o ShizukuProvider é exportado conforme exigido pela API oficial;
- cleartext HTTP desabilitado;
- sem conta, anúncios, telemetria ou secrets no APK.

Veja `SECURITY.md` e `docs/THREAT_MODEL.md`.

## Roadmap

- dual-pane completo estilo desktop/MT Manager;
- cliente ADB TLS direto com pareamento dentro do Hexora (separado do modo Shizuku por Depuração sem fio);
- archives avançados;
- Hex Editor;
- APK/DEX/Smali;
- terminal;
- análise de arquivos e ferramentas para desenvolvedor.

## Licença

Apache License 2.0. Consulte `LICENSE`.
