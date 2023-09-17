package co.electriccoin.zcash.network.util

sealed class Resource<T>(val status: Status, val data: T? = null, val message: String? = null) {
    data class Success<T>(val response: T) :
        Resource<T>(status = Status.SUCCESS, data = response)

    data class Error<T>(val errorMsg: String) :
        Resource<T>(status = Status.ERROR, message = errorMsg)

    data class Loading<T>(val response: T? = null) :
        Resource<T>(status = Status.LOADING, data = response)
}

enum class Status {
    SUCCESS,
    ERROR,
    LOADING
}
