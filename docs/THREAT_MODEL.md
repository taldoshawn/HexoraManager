# Hexora Threat Model

Este documento cobre a superfície implementada no alpha atual.

## Assets

- arquivos do usuário;
- permissões SAF persistidas;
- acesso amplo ao armazenamento quando concedido;
- integridade de arquivos durante copy/move/edit/delete;
- privacidade de nomes, paths e conteúdo.

## Trust boundaries

### Android sandbox

O app deve assumir que qualquer path fora dos roots permitidos é inacessível até o Android conceder acesso real. A UI nunca é uma fronteira de autorização.

### SAF providers

`content://` URIs podem vir de providers externos. O app não deve inferir que um URI é seguro apenas por existir; operações devem permanecer dentro das capabilities efetivamente oferecidas pelo provider e pelas permissões do sistema.

### File names and archive entries

Nomes, extensões e paths são entrada não confiável. Traversal, nomes especiais, NUL/control chars e entradas absolutas precisam ser rejeitados antes de criação/extraction.

### External apps

Abrir/compartilhar usa grants temporários de URI. O `FileProvider` é não exportado e o app não concede write access durante compartilhamento comum.

## Main threats and mitigations

### Path traversal / symlink escape

Risco: um path ou symlink escapar do root permitido.

Mitigações atuais: canonicalização em `LocalFileProvider`, comparação contra roots canonicalizados e `ArchivePathValidator` para entradas de archive.

### Data loss during move

Risco: apagar a origem após cópia incompleta.

Mitigações atuais: streaming com cleanup do destino parcial, verificação de bytes/tamanho e delete da origem apenas após cópia completa. Move nativo do provider é usado quando suportado.

### Destructive conflict replacement

Risco: apagar o destino existente antes de saber se a nova cópia terminará.

Mitigação atual: `ConflictPolicy.REPLACE` é bloqueado até existir substituição transacional segura para o provider.

### Over-privilege

Risco: pedir ou usar permissões acima do necessário.

Mitigações atuais: SAF como alternativa principal de menor privilégio; All Files Access é opcional e explícito; permissões de mídia/notificação não são solicitadas sem necessidade. Shizuku/ADB/Root não são simulados.

### Untrusted file content

Risco: parser, preview ou editor tratar conteúdo malicioso como confiável.

Mitigações atuais: editor de texto não executa conteúdo; arquivos não textuais são delegados a apps externos via URI grants. Parsers avançados ainda não existem.

### Archive bombs

Risco: descompressão consumir armazenamento/memória de forma abusiva.

Status: extração completa ainda não está implementada. Antes de habilitá-la, serão obrigatórios limites de quantidade de entries, tamanho individual, tamanho total descompactado e ratio de expansão, além da proteção Zip Slip já existente.

### Command injection

Risco futuro em terminal/ADB/Root.

Status: essa superfície ainda não existe. Quando for implementada, input deverá ser separado de argumentos/comandos e operações privilegiadas deverão exigir autorização explícita e escopo mínimo.

## Security invariants

- nunca apagar origem de move quando a cópia não foi confirmada como completa;
- nunca aceitar archive entry que resolva fora do diretório alvo;
- nunca transformar ausência de privilégio em sucesso simulado;
- nunca expor provider Android ou Activity desnecessariamente;
- nunca incluir secrets no APK/repositório;
- falhas devem preservar dados sempre que possível e não revelar conteúdo sensível em logs.
