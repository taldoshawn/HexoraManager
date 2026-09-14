# Privileged access

Hexora possui três níveis úteis de acesso a arquivos.

## Normal / SAF / All Files

Funciona sem root e sem Shizuku. É o modo padrão e cobre o armazenamento compartilhado que o Android permite. `MANAGE_EXTERNAL_STORAGE` não é um passe universal para os diretórios privados de outros apps dentro de `Android/data`.

## Shizuku

Hexora usa a API oficial `UserService` do Shizuku. O usuário inicia e autoriza o Shizuku. Quando iniciado por Depuração sem fio/ADB, o serviço do Hexora roda com identidade shell (UID 2000). Com Sui/root, pode rodar como UID 0.

O serviço de arquivos é Binder/AIDL e expõe operações tipadas de filesystem; paths não são concatenados em comandos de shell.

## Root

Hexora usa `libsu` `RootService`. A permissão é solicitada pelo gerenciador root instalado no dispositivo. O serviço root expõe a mesma interface AIDL do backend Shizuku, permitindo que o restante do file manager não dependa de comandos shell ad-hoc.

## Depuração sem fio

Nesta versão, Depuração sem fio é usada como caminho oficial para iniciar o Shizuku e obter o backend shell. Um cliente ADB TLS direto, com pareamento dentro do próprio Hexora sem Shizuku, é uma implementação separada e ainda está no roadmap; a interface não afirma que ele existe antes disso.

## Android/data

O botão `Android/data` seleciona o backend privilegiado disponível: root primeiro, Shizuku em seguida. Sem um deles o app explica a limitação do Android em vez de simular acesso.
