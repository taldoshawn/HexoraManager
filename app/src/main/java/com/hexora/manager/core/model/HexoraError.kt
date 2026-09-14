package com.hexora.manager.core.model

sealed class HexoraError(open val detail: String? = null) {
    data class PermissionDenied(override val detail: String? = null) : HexoraError(detail)
    data class NotFound(override val detail: String? = null) : HexoraError(detail)
    data class AlreadyExists(override val detail: String? = null) : HexoraError(detail)
    data class ReadOnly(override val detail: String? = null) : HexoraError(detail)
    data class NoSpace(override val detail: String? = null) : HexoraError(detail)
    data class AuthenticationFailed(override val detail: String? = null) : HexoraError(detail)
    data class NetworkUnavailable(override val detail: String? = null) : HexoraError(detail)
    data class UnsupportedFormat(override val detail: String? = null) : HexoraError(detail)
    data class CorruptFile(override val detail: String? = null) : HexoraError(detail)
    data class OperationCancelled(override val detail: String? = null) : HexoraError(detail)
    data class RootUnavailable(override val detail: String? = null) : HexoraError(detail)
    data class ShizukuUnavailable(override val detail: String? = null) : HexoraError(detail)
    data class InvalidName(override val detail: String? = null) : HexoraError(detail)
    data class InvalidPath(override val detail: String? = null) : HexoraError(detail)
    data class IoFailure(override val detail: String? = null) : HexoraError(detail)

    fun userMessage(): String = when (this) {
        is PermissionDenied -> "O Hexora não tem permissão para acessar este local."
        is NotFound -> "O arquivo ou diretório não existe mais."
        is AlreadyExists -> "Já existe um item com esse nome."
        is ReadOnly -> "Este local está disponível apenas para leitura."
        is NoSpace -> "Não há espaço suficiente para concluir a operação."
        is AuthenticationFailed -> "A autenticação falhou."
        is NetworkUnavailable -> "A conexão de rede não está disponível."
        is UnsupportedFormat -> "Este formato não é suportado por esta ferramenta."
        is CorruptFile -> "O arquivo parece estar corrompido ou malformado."
        is OperationCancelled -> "A operação foi cancelada."
        is RootUnavailable -> "Acesso root não está disponível."
        is ShizukuUnavailable -> "Shizuku não está disponível ou não foi autorizado."
        is InvalidName -> "O nome informado não é válido."
        is InvalidPath -> "O caminho informado não é válido ou permitido."
        is IoFailure -> "O Hexora não conseguiu concluir a operação de arquivo."
    }
}

sealed interface HexoraResult<out T> {
    data class Success<T>(val value: T) : HexoraResult<T>
    data class Failure(val error: HexoraError) : HexoraResult<Nothing>
}

inline fun <T, R> HexoraResult<T>.map(transform: (T) -> R): HexoraResult<R> = when (this) {
    is HexoraResult.Success -> HexoraResult.Success(transform(value))
    is HexoraResult.Failure -> this
}
