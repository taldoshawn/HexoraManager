# Contributing to Hexora

Obrigado pelo interesse em contribuir.

## Antes de alterar

1. Preserve funcionalidades existentes que já estejam corretas.
2. Não aumente permissões do Android sem necessidade concreta e documentada.
3. Não simule Shizuku, ADB, Root ou acesso a paths que o processo não possui.
4. Trate nomes de arquivo, URIs, archives e conteúdo externo como não confiáveis.
5. Evite dependências novas quando a plataforma já resolve o problema de forma segura.

## Fluxo recomendado

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Pull requests devem explicar a causa raiz do problema, o comportamento alterado e qualquer mudança no modelo de segurança/permissões.

## Código

- Kotlin e Jetpack Compose para UI Android.
- Operações de filesystem devem passar por `FileAccessProvider`/`OperationEngine` em vez de espalhar acesso direto pela UI.
- Operações destrutivas devem tratar falhas parciais e cancelamento.
- Nunca concatene input não confiável em comandos de shell. Quando terminal/ADB/Root forem implementados, argumentos e comandos precisarão de fronteiras explícitas e revisão de segurança.
- Archives devem passar por `ArchivePathValidator` e também ter limites de tamanho/quantidade antes de extração real.

## Testes

Mudanças em validação, parsing, paths, conflitos ou operações de arquivo devem incluir testes de casos válidos e adversariais.

## Vulnerabilidades

Siga `SECURITY.md`. Não abra issue pública contendo um exploit utilizável antes da correção.
