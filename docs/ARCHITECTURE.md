# Hexora Architecture

## Goals

Hexora separa UI, estado, acesso ao filesystem e execução de operações para que novos níveis de privilégio possam ser adicionados sem transformar a aplicação em um conjunto de exceções inseguras.

## Camadas atuais

### UI

`ui/HexoraApp.kt` contém as telas Compose: Home, Browser, Tarefas, Acesso, Editor e Configurações. A UI não decide se um path é autorizado; ela solicita operações ao ViewModel.

### State / orchestration

`ui/HexoraViewModel.kt` mantém estados imutáveis expostos por `StateFlow`, histórico de navegação, seleção, clipboard e estado do editor. Ele coordena providers e `OperationEngine`.

### File references

`core/model/HexoraFileRef.kt` é a referência comum para arquivos. Ela identifica origem, provider, path/URI, metadados e capabilities sem fazer a UI depender de `java.io.File` ou `DocumentFile` diretamente.

### Providers

`FileAccessProvider` define as operações mínimas: list, stat, exists, streams, create, rename, delete, move e parent.

Implementados:

- `LocalFileProvider`: app storage e armazenamento compartilhado permitido pelo Android;
- `SafFileProvider`: árvores/documentos concedidos pelo Storage Access Framework.

Planejados, mas não implementados: MediaStore dedicado, Archive, Shizuku, ADB, Root e Remote.

### Operation engine

`OperationEngine` centraliza copy/move/delete/hash e publica progresso em `StateFlow`. Copy é streaming, limpa destinos parciais em falhas e valida tamanho gravado. Move entre providers só apaga a origem após uma cópia completa.

Substituição destrutiva por conflito está deliberadamente bloqueada até que cada provider tenha uma estratégia transacional segura.

## Privilege model

A arquitetura não considera acesso como booleano global. Cada referência pertence a um provider e o provider decide o que realmente consegue fazer naquele momento.

Progressão pretendida:

1. storage privado do app / APIs normais;
2. SAF;
3. All Files Access, quando o usuário decidir;
4. Shizuku;
5. Wireless ADB;
6. Root.

Os níveis 4–6 serão opt-in e não deverão alterar silenciosamente o provider de uma operação existente.

## Threading

Filesystem e hashing usam coroutines/`Dispatchers.IO`. A UI coleta `StateFlow` com lifecycle awareness. Operações longas não devem bloquear a main thread.

## Persistência

Configurações simples usam DataStore Preferences. Permissões SAF persistentes pertencem ao `ContentResolver` do Android. Não há banco de dados nesta fase.

## Future modules

Antes de adicionar APK/DEX/Smali, archives avançados, Hex Editor ou terminal, a preferência é criar módulos/engines isolados sobre a mesma fronteira de capabilities e cobrir parsers e operações destrutivas com testes adversariais.
